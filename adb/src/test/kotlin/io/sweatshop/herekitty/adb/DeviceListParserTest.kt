package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.features.devices.model.DeviceState
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceListParserTest {

    /** Exactly what `host:track-devices-l` reported for the two attached phones. */
    @Test
    fun `parses the long form emitted by adb`() {
        val devices = parseDeviceList(
            """
            EXAMPLE0001            device usb:1-1.1.1.4 product:dm1qsqw model:SM_S911U device:dm1q transport_id:2
            RFCT42DKQBP            device usb:2-1 product:g0qsqw model:SM_S906U device:g0q transport_id:1
            """.trimIndent(),
        )

        assertEquals(2, devices.size)
        assertEquals("EXAMPLE0001", devices[0].serial)
        assertEquals(DeviceState.DEVICE, devices[0].state)
        assertEquals("SM_S911U", devices[0].model)
        assertEquals("dm1qsqw", devices[0].product)
        assertEquals("2", devices[0].transportId)
        assertEquals("SM S911U", devices[0].displayName)
    }

    @Test
    fun `parses the tab separated short form`() {
        val devices = parseDeviceList("emulator-5554\tdevice\nEXAMPLE0001\toffline")

        assertEquals(DeviceState.DEVICE, devices[0].state)
        assertEquals(DeviceState.OFFLINE, devices[1].state)
        assertEquals("emulator-5554", devices[0].serial)
    }

    @Test
    fun `reads a multi word state without mistaking it for a property`() {
        val devices = parseDeviceList("1234567890 no permissions (user in plugdev group); see [link]")

        assertEquals(DeviceState.NO_PERMISSIONS, devices.single().state)
    }

    @Test
    fun `falls back to the serial when the device reports no model`() {
        assertEquals("EXAMPLE0001", parseDeviceList("EXAMPLE0001\tunauthorized").single().displayName)
        assertEquals(DeviceState.UNAUTHORIZED, parseDeviceList("EXAMPLE0001\tunauthorized").single().state)
    }

    @Test
    fun `skips the header line and blank lines`() {
        assertEquals(0, parseDeviceList("List of devices attached\n\n\n").size)
    }
}
