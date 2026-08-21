package io.sweatshop.herekitty.domain.features.devices.model

enum class DeviceState(val wireName: String, val label: String) {
    DEVICE("device", "Ready"),
    OFFLINE("offline", "Offline"),
    UNAUTHORIZED("unauthorized", "Unauthorized"),
    AUTHORIZING("authorizing", "Authorizing"),
    CONNECTING("connecting", "Connecting"),
    BOOTLOADER("bootloader", "Bootloader"),
    RECOVERY("recovery", "Recovery"),
    SIDELOAD("sideload", "Sideload"),
    RESCUE("rescue", "Rescue"),
    NO_PERMISSIONS("no permissions", "No permissions"),
    HOST("host", "Host"),
    UNKNOWN("unknown", "Unknown");

    val canStreamLogs: Boolean get() = this == DEVICE

    companion object {
        fun fromWireName(value: String): DeviceState =
            entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}
