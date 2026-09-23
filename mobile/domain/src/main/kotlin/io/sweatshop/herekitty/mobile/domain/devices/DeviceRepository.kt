package io.sweatshop.herekitty.mobile.domain.devices

import kotlinx.coroutines.flow.Flow

data class Device(
    val id: String, // == this install's generated installId, stable for the life of the install
    val ownerId: String,
    val ownerDisplayName: String,
    val name: String,
    val model: String,
    val isShared: Boolean,
)

/** Someone else's desktop has claimed this device for a bridge session. Null means unclaimed. */
data class SessionClaim(
    val connectedByUid: String,
    val connectedByDisplayName: String,
    val relaySessionId: String,
    val relayToken: String,
)

interface DeviceRepository {
    /** Creates this install's device doc on first call, otherwise returns the existing one. */
    suspend fun ensureRegistered(model: String): Device

    suspend fun setShared(deviceId: String, shared: Boolean)
    suspend fun setName(deviceId: String, name: String)
    suspend fun setFcmToken(deviceId: String, token: String)

    /** RTDB presence — an owner-only write, so a device can only assert its own online state. */
    suspend fun setPresence(deviceId: String, online: Boolean)

    /** Live updates as a desktop claims, takes over, or releases a session on this device. */
    fun observeSessionClaim(deviceId: String): Flow<SessionClaim?>
}
