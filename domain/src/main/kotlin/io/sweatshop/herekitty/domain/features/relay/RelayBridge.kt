package io.sweatshop.herekitty.domain.features.relay

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface RelayBridgeStatus {
    data object Claiming : RelayBridgeStatus
    data object Bridging : RelayBridgeStatus
    data class Reconnecting(val attempt: Int) : RelayBridgeStatus
    data class Connected(val hostPort: String, val transport: RelayTransport) : RelayBridgeStatus
    data class Failed(val message: String) : RelayBridgeStatus
}

enum class RelayTransport { LocalNetwork, Relay }

/**
 * Bridges a shared phone onto this desktop's own adb: claims a relay session, forwards a local
 * TCP port through the relay to the phone's adbd, then `adb connect`s to it. Once that succeeds
 * the phone is an ordinary adb device and needs nothing further from here -- it flows through
 * :adb's existing host:track-devices the same as anything on USB, which is why there is no
 * "session" concept exposed above this: only a status per device, for the Connect button to
 * reflect.
 */
interface RelayBridgeRepository {
    /** Keyed by deviceId. A device absent from the map has never been connected this run. */
    val statuses: StateFlow<Map<String, RelayBridgeStatus>>

    /**
     * Emits a device's `hostPort` exactly once, the moment its bridge reaches [RelayBridgeStatus.Connected]
     * -- not replayed to a collector that attaches afterward. [statuses] is level-triggered (an
     * already-Connected bridge looks identical to a freshly-connected one to anything reading it),
     * which is wrong for "open a session automatically": a source picker recomposed after the user
     * closes that very session would see the same still-Connected status and reopen it right back.
     * This is the edge, not the level.
     */
    val connectedEvents: SharedFlow<String>

    suspend fun connect(deviceId: String)

    suspend fun disconnect(deviceId: String)
}
