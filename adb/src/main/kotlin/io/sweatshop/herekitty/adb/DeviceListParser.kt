package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.model.DeviceState

private val PROPERTY_KEYS = setOf("usb", "product", "model", "device", "transport_id")

internal fun parseDeviceList(payload: String): List<AdbDevice> =
    payload.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("List of devices") }
        .mapNotNull(::parseDeviceLine)
        .toList()

private fun parseDeviceLine(line: String): AdbDevice? {
    val tokens = line.split('\t', ' ').filter { it.isNotBlank() }
    if (tokens.size < 2) return null

    val serial = tokens.first()
    val properties = mutableMapOf<String, String>()
    val stateWords = mutableListOf<String>()

    for (token in tokens.drop(1)) {
        val key = token.substringBefore(':', missingDelimiterValue = "")
        if (key in PROPERTY_KEYS) {
            properties[key] = token.substringAfter(':')
        } else {
            stateWords += token
        }
    }

    return AdbDevice(
        serial = serial,
        state = DeviceState.fromWireName(stateWords.joinToString(" ").substringBefore(" (")),
        model = properties["model"],
        product = properties["product"],
        device = properties["device"],
        transportId = properties["transport_id"],
    )
}
