package io.sweatshop.herekitty.repository.devices

import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.adb.DeviceTrackEvent
import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.model.AdbServerState
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(binds = [DeviceRepository::class])
class DeviceRepositoryImpl(
    private val hostClient: AdbHostClient,
    private val appScope: AppScope,
) : DeviceRepository {

    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    override val devices = _devices.asStateFlow()

    private val _serverState = MutableStateFlow<AdbServerState>(AdbServerState.Starting)
    override val serverState = _serverState.asStateFlow()

    private var trackJob: Job? = null

    init {
        startTracking()
    }

    override fun device(serial: String): AdbDevice? = _devices.value.firstOrNull { it.serial == serial }

    override fun reconnect() {
        startTracking()
    }

    private fun startTracking() {
        trackJob?.cancel()
        _serverState.value = AdbServerState.Starting
        trackJob = appScope.launch {
            hostClient.trackDevices().collect { event ->
                when (event) {
                    is DeviceTrackEvent.Devices -> {
                        _devices.value = event.devices.sortedWith(
                            compareBy({ it.isEmulator }, { it.displayName }, { it.serial }),
                        )
                        _serverState.value = AdbServerState.Connected(event.serverVersion)
                    }

                    is DeviceTrackEvent.Unavailable -> {
                        _devices.value = emptyList()
                        _serverState.value = AdbServerState.Unavailable(event.reason)
                    }
                }
            }
        }
    }
}
