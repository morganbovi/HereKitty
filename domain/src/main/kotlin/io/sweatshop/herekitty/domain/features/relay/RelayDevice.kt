package io.sweatshop.herekitty.domain.features.relay

data class RelayDevice(
    val id: String,
    val ownerDisplayName: String,
    val name: String,
    val isOnline: Boolean,
)

/**
 * Reads the org's shared phones over plain REST -- Firestore for the device list, Realtime
 * Database for presence -- rather than a Firebase Client SDK, same reasoning as `:auth`: this
 * project has no Firebase SDK on desktop. Distinct from `:adb`'s DeviceRepository, which is
 * locally-connected USB/emulator devices; this is org members' *shared* phones, reached through
 * the relay once connected.
 */
interface RelayDeviceRepository {
    suspend fun listSharedDevices(): Result<List<RelayDevice>>

    /** Pings the relay's own /health endpoint -- independent of whether any device is shared. */
    suspend fun relayIsReachable(): Boolean
}
