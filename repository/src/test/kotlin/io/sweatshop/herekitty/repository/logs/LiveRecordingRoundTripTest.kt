package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.adb.AdbBinaryLocator
import io.sweatshop.herekitty.adb.AdbEndpoint
import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.adb.DeviceTrackEvent
import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Captures a real device, exports it, reads it back, and checks the reimported session filters the
 * same way. Opt in with `-Dherekitty.liveAdb=true` and a device attached.
 *
 * Worth keeping: the unit tests cover the codec against handwritten lines, but only a real device
 * produces the volume, the odd tags, and the multi-line messages that the format has to survive.
 */
class LiveRecordingRoundTripTest {

    @Test
    fun exportsAndReimportsARealCapture() = runBlocking {
        if (System.getProperty(LIVE_PROPERTY) == null) {
            println("Skipping: pass -D$LIVE_PROPERTY=true with a device attached to run this.")
            return@runBlocking
        }

        val client = AdbHostClient(AdbEndpoint.fromEnvironment(), AdbBinaryLocator())
        val tracked = withTimeoutOrNull(TRACK_TIMEOUT_MILLIS) { client.trackDevices().first() }
        val device = (tracked as? DeviceTrackEvent.Devices)?.devices?.firstOrNull { it.state.canStreamLogs }
        if (device == null) {
            println("Skipping: no device is ready to stream logs.")
            return@runBlocking
        }

        val capBytes = MutableStateFlow(CAP_BYTES)
        val file = Files.createTempFile("herekitty-live", ".hklog.gz").also { Files.delete(it) }

        val live = DeviceLogSession(SessionId(1L), device, client, capBytes, AppScope())
        val exportedLines: Long
        val busiestTag: String?
        try {
            delay(CAPTURE_MILLIS)
            val liveStats = live.stats.value
            assertTrue(liveStats.lineCount > 0L, "captured nothing from ${device.displayName}")
            busiestTag = live.tags.value.maxByOrNull { it.count }?.tag

            exportedLines = live.exportTo(file).getOrThrow()
            assertEquals(
                liveStats.lineCount,
                exportedLines,
                "export should write exactly the lines still held",
            )
        } finally {
            live.dispose()
        }

        val header = RecordingFiles.readHeader(file)
        assertEquals(device.displayName, header.recordedFrom)
        assertEquals(device.serial, header.serial)

        val reimported = RecordedLogSession(SessionId(2L), file, header, capBytes, AppScope())
        try {
            val everything = reimported.openView()
            val pinned = reimported.openView()
                .also { it.configure(LogViewSpec(filter = LogFilter(tags = setOfNotNull(busiestTag)))) }

            withTimeoutOrNull(IMPORT_TIMEOUT_MILLIS) {
                while (reimported.connection.value != ConnectionState.Recorded) delay(200)
            }
            delay(SETTLE_MILLIS)

            assertEquals(
                exportedLines,
                reimported.stats.value.lineCount,
                "the reimported session should hold every exported line",
            )
            assertTrue(everything.snapshot().size > 0, "the reimported view is empty")

            if (busiestTag != null) {
                val matches = pinned.snapshot()
                assertTrue(matches.size > 0, "no reimported lines matched '$busiestTag'")
                assertTrue(
                    (0 until matches.size).all { matches[it].tag == busiestTag },
                    "a tag filter let another tag through after reimport",
                )
            }
        } finally {
            reimported.dispose()
            Files.deleteIfExists(file)
        }
    }

    private companion object {
        const val LIVE_PROPERTY = "herekitty.liveAdb"
        const val CAP_BYTES = 16L * 1024 * 1024
        const val TRACK_TIMEOUT_MILLIS = 8_000L
        const val CAPTURE_MILLIS = 6_000L
        const val IMPORT_TIMEOUT_MILLIS = 60_000L
        const val SETTLE_MILLIS = 1_500L
    }
}
