package io.sweatshop.herekitty.mobile.sources.devices

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.sweatshop.herekitty.mobile.domain.devices.Device
import io.sweatshop.herekitty.mobile.domain.devices.DeviceRepository
import io.sweatshop.herekitty.mobile.domain.devices.SessionClaim
import io.sweatshop.herekitty.mobile.sources.installId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Single

private const val ORG_ID = "default" // v1: exactly one org, see docs/remote-bridge-plan.md

@Single(binds = [DeviceRepository::class])
class DeviceRepositoryImpl(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth,
) : DeviceRepository {

    private val firestore get() = FirebaseFirestore.getInstance()
    private val database get() = FirebaseDatabase.getInstance()

    private fun devicesCollection() =
        firestore.collection("orgs").document(ORG_ID).collection("devices")

    override suspend fun ensureRegistered(model: String): Device {
        val uid = firebaseAuth.currentUser?.uid ?: error("Not signed in")
        val displayName = firebaseAuth.currentUser?.displayName
            ?: firebaseAuth.currentUser?.email
            ?: "Unknown"
        val id = installId(context)
        val docRef = devicesCollection().document(id)

        val existing = docRef.get().await()
        if (existing.exists()) return existing.toDevice(id)

        val data = mapOf(
            "ownerId" to uid,
            "ownerDisplayName" to displayName,
            "name" to model,
            "model" to model,
            "installId" to id,
            "isShared" to false,
        )
        docRef.set(data).await()
        return Device(
            id = id,
            ownerId = uid,
            ownerDisplayName = displayName,
            name = model,
            model = model,
            isShared = false,
        )
    }

    override suspend fun setShared(deviceId: String, shared: Boolean) {
        devicesCollection().document(deviceId).update("isShared", shared).await()
    }

    override suspend fun setName(deviceId: String, name: String) {
        devicesCollection().document(deviceId).update("name", name).await()
    }

    override suspend fun setFcmToken(deviceId: String, token: String) {
        devicesCollection().document(deviceId).update("fcmToken", token).await()
    }

    override suspend fun setPresence(deviceId: String, online: Boolean) {
        val onlineRef = database.getReference("presence").child(deviceId).child("online")
        if (online) {
            onlineRef.setValue(true).await()
            database.getReference("presence").child(deviceId).child("lastSeenAt")
                .setValue(ServerValue.TIMESTAMP).await()
            // Fires even on an ungraceful disconnect (crash, network loss) -- this is what keeps
            // a phone from showing "online" forever after it silently drops off.
            onlineRef.onDisconnect().setValue(false)
        } else {
            onlineRef.setValue(false).await()
        }
    }

    override fun observeSessionClaim(deviceId: String): Flow<SessionClaim?> = callbackFlow {
        val ref = database.getReference("sessions").child(deviceId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connectedByUid = snapshot.child("connectedByUid").getValue(String::class.java)
                val relaySessionId = snapshot.child("relaySessionId").getValue(String::class.java)
                val relayToken = snapshot.child("relayToken").getValue(String::class.java)
                val connectedByDisplayName =
                    snapshot.child("connectedByDisplayName").getValue(String::class.java)
                trySend(
                    if (connectedByUid != null && relaySessionId != null && relayToken != null) {
                        SessionClaim(connectedByUid, connectedByDisplayName ?: connectedByUid, relaySessionId, relayToken)
                    } else {
                        null
                    },
                )
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DocumentSnapshot.toDevice(id: String): Device = Device(
        id = id,
        ownerId = getString("ownerId") ?: "",
        ownerDisplayName = getString("ownerDisplayName") ?: "",
        name = getString("name") ?: "",
        model = getString("model") ?: "",
        isShared = getBoolean("isShared") ?: false,
    )
}
