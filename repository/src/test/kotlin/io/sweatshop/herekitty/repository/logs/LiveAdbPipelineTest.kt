package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.adb.AdbBinaryLocator
import io.sweatshop.herekitty.adb.AdbEndpoint
import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.adb.DeviceTrackEvent
import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives capture, the buffer, the tag registry, and two filtered views against a real device.
 *
 * Opt in with `./gradlew :repository:test -Dherekitty.liveAdb=true` and a device attached; it is
 * skipped otherwise so the normal suite needs no hardware. This is worth keeping: it is what caught
 * lines being dropped by a timed channel receive, and the buffer resolving the gaps that left to the
 * wrong log line.
 */
class LiveAdbPipelineTest {

    @Test
    fun recordsAndFiltersRealDeviceOutput() = runBlocking {
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

        val session = DeviceLogSession(
            id = SessionId(1L),
            initialDevice = device,
            hostClient = client,
            memoryCapBytes = MutableStateFlow(CAP_BYTES),
            parentScope = AppScope(),
        )

        try {
            val everything = session.openView()
            val warningsOnly = session.openView().also { it.configure(LogViewSpec(filter = LogFilter(minLevel = LogLevel.WARN))) }
            delay(CAPTURE_MILLIS)

            val stats = session.stats.value
            assertTrue(stats.totalLinesSeen > 0L, "captured nothing from ${device.displayName}")
            assertTrue(stats.estimatedBytes <= CAP_BYTES, "buffer overshot its cap: ${stats.estimatedBytes}")
            assertTrue(session.tags.value.isNotEmpty(), "no tags were registered")

            val busiestTag = session.tags.value.first().tag
            val pinnedToTag = session.openView().also { it.configure(LogViewSpec(filter = LogFilter(tags = setOf(busiestTag)))) }
            delay(REBUILD_MILLIS)

            assertTrue(everything.snapshot().size > 0, "the unfiltered view is empty")

            val warnings = warningsOnly.snapshot()
            assertTrue(
                (0 until warnings.size).all { warnings[it].level.ordinal >= LogLevel.WARN.ordinal },
                "a level filter let a quieter line through",
            )

            val pinned = pinnedToTag.snapshot()
            assertTrue(pinned.size > 0, "no lines matched the busiest tag '$busiestTag'")
            assertTrue(
                (0 until pinned.size).all { pinned[it].tag == busiestTag },
                "a tag filter let another tag through",
            )
        } finally {
            session.dispose()
        }
    }

    private companion object {
        const val LIVE_PROPERTY = "herekitty.liveAdb"
        const val CAP_BYTES = 8L * 1024 * 1024
        const val TRACK_TIMEOUT_MILLIS = 8_000L
        const val CAPTURE_MILLIS = 6_000L
        const val REBUILD_MILLIS = 1_500L
    }
}
