const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const jwt = require("jsonwebtoken");
const { randomUUID } = require("crypto");
admin.initializeApp();

const WAKEUP_COOLDOWN_MS = 2 * 60 * 1000;
const RELAY_TOKEN_TTL_SECONDS = 10 * 60;
const relayJwtSecret = defineSecret("RELAY_JWT_SECRET");

// Called by an admin to add an already-signed-in user to the org. The target user must have
// signed in at least once already -- Firebase Auth users only exist after a first sign-in, so
// there's no invite-a-not-yet-existing-user flow for v1. Sets a custom claim (orgId, isAdmin)
// rather than trusting a Firestore field, so Security Rules can check org membership without an
// extra read, and a client can never grant itself membership by writing its own document.
exports.assignUserToOrg = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be signed in");
    if (request.auth.token.isAdmin !== true) {
        throw new HttpsError("permission-denied", "Only an org admin can assign users");
    }

    const { email, role } = request.data;
    if (!email) throw new HttpsError("invalid-argument", "email is required");
    const resolvedRole = role === "admin" ? "admin" : "member";

    const orgId = "default"; // v1: exactly one org

    let userRecord;
    try {
        userRecord = await admin.auth().getUserByEmail(email);
    } catch (e) {
        throw new HttpsError("not-found", `No user has signed in yet with email ${email}`);
    }

    const existingClaims = userRecord.customClaims || {};
    await admin.auth().setCustomUserClaims(userRecord.uid, {
        ...existingClaims,
        orgId,
        isAdmin: resolvedRole === "admin",
    });

    await admin.firestore()
        .collection("orgs").doc(orgId)
        .collection("members").doc(userRecord.uid)
        .set({
            role: resolvedRole,
            email: userRecord.email || email,
            displayName: userRecord.displayName || userRecord.email || email,
            addedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

    return { uid: userRecord.uid, orgId, role: resolvedRole };
});

// Called by any org member to ask an offline-but-shared device's owner to turn sharing back on.
// Never touches the device's isShared state directly -- it only sends a push. The phone decides
// whether to act on it, and the user has to tap through, same reasoning as the OS's own
// per-network wireless-debug confirmation: a phone coming back online for someone else to use
// needs the owner's eyes on it in the moment, not a background flip triggered by a stranger.
exports.requestDeviceWakeup = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be signed in");
    const orgId = request.auth.token.orgId;
    if (!orgId) throw new HttpsError("permission-denied", "Must be an org member");

    const { deviceId } = request.data;
    if (!deviceId) throw new HttpsError("invalid-argument", "deviceId is required");

    const deviceRef = admin.firestore()
        .collection("orgs").doc(orgId)
        .collection("devices").doc(deviceId);
    const deviceSnap = await deviceRef.get();
    if (!deviceSnap.exists) throw new HttpsError("not-found", "Device not found");
    const device = deviceSnap.data();

    if (!device.fcmToken) {
        throw new HttpsError("failed-precondition", "This device has no notification token registered");
    }

    const lastRequestAt = device.lastWakeupRequestAt ? device.lastWakeupRequestAt.toMillis() : 0;
    if (Date.now() - lastRequestAt < WAKEUP_COOLDOWN_MS) {
        throw new HttpsError("resource-exhausted", "A wakeup request was already sent recently -- try again shortly");
    }

    const requesterName = request.auth.token.name || request.auth.token.email || "Someone";

    await admin.messaging().send({
        token: device.fcmToken,
        notification: {
            title: "HereKitty",
            body: `${requesterName} wants to use ${device.name || "your device"}`,
        },
        data: {
            type: "wakeup_request",
            deviceId,
            requesterName,
        },
    });

    await deviceRef.update({ lastWakeupRequestAt: admin.firestore.FieldValue.serverTimestamp() });

    return { sent: true };
});

// Claims a shared device and returns the exact short-lived credential both bridge endpoints need.
// The relay only sees this signed credential; it has no Firebase access and cannot mint one.
exports.claimRelaySession = onCall({ secrets: [relayJwtSecret] }, async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be signed in");
    const orgId = request.auth.token.orgId;
    if (!orgId) throw new HttpsError("permission-denied", "Must be an org member");

    const { deviceId } = request.data;
    if (!deviceId) throw new HttpsError("invalid-argument", "deviceId is required");

    const deviceSnap = await admin.firestore()
        .collection("orgs").doc(orgId)
        .collection("devices").doc(deviceId)
        .get();
    if (!deviceSnap.exists) throw new HttpsError("not-found", "Device not found");
    if (deviceSnap.data().isShared !== true) {
        throw new HttpsError("failed-precondition", "Device is not currently shared");
    }

    const relaySessionId = randomUUID();
    const relayToken = jwt.sign(
        { deviceId, relaySessionId },
        relayJwtSecret.value(),
        {
            algorithm: "HS256",
            audience: "herekitty-relay",
            expiresIn: RELAY_TOKEN_TTL_SECONDS,
            issuer: "herekitty-mobile",
            subject: request.auth.uid,
        },
    );

    await admin.database().ref(`sessions/${deviceId}`).set({
        connectedByUid: request.auth.uid,
        connectedByDisplayName: request.auth.token.name || request.auth.token.email || request.auth.uid,
        relaySessionId,
        relayToken,
        claimedAt: admin.database.ServerValue.TIMESTAMP,
    });

    return { relaySessionId, relayToken };
});

exports.releaseRelaySession = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be signed in");
    const { deviceId, relaySessionId } = request.data;
    if (!deviceId || !relaySessionId) {
        throw new HttpsError("invalid-argument", "deviceId and relaySessionId are required");
    }

    const sessionRef = admin.database().ref(`sessions/${deviceId}`);
    await sessionRef.transaction((current) => {
        if (current?.connectedByUid !== request.auth.uid || current?.relaySessionId !== relaySessionId) {
            return;
        }
        return null;
    });
    return { released: true };
});
