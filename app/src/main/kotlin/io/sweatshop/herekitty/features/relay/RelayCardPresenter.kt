package io.sweatshop.herekitty.features.relay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.features.auth.AuthRepository
import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.relay.RelayBridgeRepository
import io.sweatshop.herekitty.domain.features.relay.RelayDevice
import io.sweatshop.herekitty.domain.features.relay.RelayDeviceRepository
import io.sweatshop.herekitty.net.HttpFetch
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Factory

private const val REQUEST_DEVICE_WAKEUP_URL =
    "https://us-central1-herekitty-mobile.cloudfunctions.net/requestDeviceWakeup"

@Factory
class RelayCardPresenter(
    private val authRepository: AuthRepository,
    private val relayDeviceRepository: RelayDeviceRepository,
    private val relayBridgeRepository: RelayBridgeRepository,
    private val deviceRepository: DeviceRepository,
) {
    @Composable
    fun present(onDeviceBridged: (AdbDevice) -> Unit): RelayCardUiModel {
        val scope = rememberCoroutineScope()
        val authState by authRepository.authState.collectAsState()
        val bridgeStatuses by relayBridgeRepository.statuses.collectAsState()
        var isSigningIn by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var isBrowsing by remember { mutableStateOf(false) }
        var isLoadingDevices by remember { mutableStateOf(false) }
        var devices by remember { mutableStateOf<List<RelayDevice>>(emptyList()) }
        var relayReachable by remember { mutableStateOf<Boolean?>(null) }
        var wakeupStatus by remember { mutableStateOf<String?>(null) }

        val orgId = (authState as? AuthState.Authenticated)?.orgId

        LaunchedEffect(Unit) {
            relayBridgeRepository.connectedEvents.collect { hostPort ->
                val device = withTimeoutOrNull(5_000) {
                    deviceRepository.devices.first { list -> list.any { it.serial == hostPort } }
                        .first { it.serial == hostPort }
                }
                if (device != null) {
                    isBrowsing = false
                    onDeviceBridged(device)
                }
            }
        }

        suspend fun refreshDevices() {
            isLoadingDevices = true
            relayDeviceRepository.listSharedDevices()
                .onSuccess { devices = it }
                .onFailure { error = it.message }
            isLoadingDevices = false
        }

        suspend fun refreshRelayHealth() {
            relayReachable = relayDeviceRepository.relayIsReachable()
        }

        LaunchedEffect(orgId) {
            if (orgId != null) refreshDevices()
        }

        LaunchedEffect(Unit) { refreshRelayHealth() }

        return RelayCardUiModel(
            authState = authState,
            isSigningIn = isSigningIn,
            error = error,
            isBrowsing = isBrowsing,
            isLoadingDevices = isLoadingDevices,
            devices = devices,
            relayReachable = relayReachable,
            wakeupStatus = wakeupStatus,
            bridgeStatuses = bridgeStatuses,
            eventHandler = EventHandler { event ->
                when (event) {
                    RelayCardUiModel.Event.OnSignInClicked -> scope.launch {
                        isSigningIn = true
                        error = null
                        authRepository.signIn().onFailure { error = it.message ?: "Sign-in failed" }
                        isSigningIn = false
                    }

                    RelayCardUiModel.Event.OnSignOutClicked -> {
                        authRepository.signOut()
                        isBrowsing = false
                        devices = emptyList()
                    }

                    RelayCardUiModel.Event.OnErrorDismissed -> error = null
                    RelayCardUiModel.Event.OnBrowseClicked -> isBrowsing = true
                    RelayCardUiModel.Event.OnBrowseClosed -> isBrowsing = false
                    RelayCardUiModel.Event.OnRefreshDevicesClicked -> scope.launch {
                        refreshDevices()
                        refreshRelayHealth()
                    }

                    is RelayCardUiModel.Event.OnRequestDeviceClicked -> scope.launch {
                        wakeupStatus = "Sending…"
                        try {
                            val token = authRepository.idToken() ?: error("Not signed in")
                            HttpFetch.postJson(
                                REQUEST_DEVICE_WAKEUP_URL,
                                """{"data":{"deviceId":"${event.deviceId}"}}""",
                                headers = mapOf("Authorization" to "Bearer $token"),
                            )
                            wakeupStatus = "Request sent"
                        } catch (e: Exception) {
                            wakeupStatus = e.message ?: "Failed to send request"
                        }
                    }

                    is RelayCardUiModel.Event.OnConnectClicked -> scope.launch {
                        relayBridgeRepository.connect(event.deviceId)
                    }

                    is RelayCardUiModel.Event.OnDisconnectClicked -> scope.launch {
                        relayBridgeRepository.disconnect(event.deviceId)
                    }
                }
            },
        )
    }
}
