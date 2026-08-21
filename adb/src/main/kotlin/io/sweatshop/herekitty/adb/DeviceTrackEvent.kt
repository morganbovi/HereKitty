package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice

sealed interface DeviceTrackEvent {
    data class Devices(val devices: List<AdbDevice>, val serverVersion: Int) : DeviceTrackEvent

    data class Unavailable(val reason: String) : DeviceTrackEvent
}
