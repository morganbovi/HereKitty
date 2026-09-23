package io.sweatshop.herekitty.features.relay

import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.domain.features.relay.RelayBridgeStatus
import io.sweatshop.herekitty.domain.features.relay.RelayDevice
import io.sweatshop.herekitty.ui.presenter.EventHandler

data class RelayCardUiModel(
    val authState: AuthState,
    val isSigningIn: Boolean,
    val error: String?,
    val isBrowsing: Boolean,
    val isLoadingDevices: Boolean,
    val devices: List<RelayDevice>,
    val relayReachable: Boolean?,
    val wakeupStatus: String?,
    val bridgeStatuses: Map<String, RelayBridgeStatus>,
    val eventHandler: EventHandler<Event>,
) {
    val orgId: String? get() = (authState as? AuthState.Authenticated)?.orgId

    sealed interface Event {
        data object OnSignInClicked : Event
        data object OnSignOutClicked : Event
        data object OnErrorDismissed : Event
        data object OnBrowseClicked : Event
        data object OnBrowseClosed : Event
        data object OnRefreshDevicesClicked : Event
        data class OnRequestDeviceClicked(val deviceId: String) : Event
        data class OnConnectClicked(val deviceId: String) : Event
        data class OnDisconnectClicked(val deviceId: String) : Event
    }
}
