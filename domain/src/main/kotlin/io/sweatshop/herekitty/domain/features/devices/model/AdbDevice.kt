package io.sweatshop.herekitty.domain.features.devices.model

data class AdbDevice(
    val serial: String,
    val state: DeviceState,
    val model: String? = null,
    val product: String? = null,
    val device: String? = null,
    val transportId: String? = null,
) {
    val displayName: String get() = model?.replace('_', ' ')?.takeIf { it.isNotBlank() } ?: serial

    val isEmulator: Boolean get() = serial.startsWith("emulator-")

    val isWireless: Boolean get() = serial.contains(':') && !isEmulator
}
