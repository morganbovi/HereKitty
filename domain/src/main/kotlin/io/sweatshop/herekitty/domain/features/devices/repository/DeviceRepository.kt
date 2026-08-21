package io.sweatshop.herekitty.domain.features.devices.repository

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.model.AdbServerState
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val devices: StateFlow<List<AdbDevice>>
    val serverState: StateFlow<AdbServerState>

    fun device(serial: String): AdbDevice?

    fun reconnect()
}
