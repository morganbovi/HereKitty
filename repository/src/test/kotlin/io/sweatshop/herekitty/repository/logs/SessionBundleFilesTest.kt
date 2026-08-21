package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.repository.views.ViewConfigCodec
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SessionBundleFilesTest {

    private fun tempBundle(): Path =
        Files.createTempFile("herekitty", ".hkbundle").also { Files.delete(it) }

    private fun line(seq: Long, message: String = "sent packet") =
        LogLine(seq, 1_787_172_644_106L + seq, 2099, 3244, LogLevel.WARN, "Radio", message)

    private val view = ViewConfig(
        name = "new-flow",
        root = ViewConfig.panesRow(
            listOf(
                PaneConfig(PaneId(0L), LogFilter(tags = setOf("Radio")), collapseDuplicates = true),
                PaneConfig(PaneId(1L), LogFilter(query = "timeout", minLevel = LogLevel.WARN), followTail = false),
            ),
        ),
    )

    private fun writeBundle(path: Path, lines: List<LogLine>): Long = SessionBundleFiles.write(
        path = path,
        viewJson = ViewConfigCodec.encode(view),
        recordedFrom = "SM S906U",
        serial = "RFCT42DKQBP",
        exportedAtMillis = 1_787_172_700_000L,
        lineCount = lines.size.toLong(),
        lines = { write -> lines.forEach(write) },
    )

    @Test
    fun `a bundle carries both the view and the logs`() = runTest {
        val path = tempBundle()
        val lines = listOf(line(0L), line(1L, "multi\nline"), line(2L))

        assertEquals(3L, writeBundle(path, lines))

        val restoredView = ViewConfigCodec.decode(SessionBundleFiles.readViewJson(path), "ignored")
        assertEquals("new-flow", restoredView.name)
        assertTrue(restoredView.hasSameSetupAs(view))

        var seq = 0L
        val read = mutableListOf<LogLine>()
        val header = SessionBundleFiles.read(path, { seq++ }) { read += it }

        assertEquals("SM S906U", header.recordedFrom)
        assertEquals(3L, header.lineCount)
        assertEquals(lines.map { it.message }, read.map { it.message })
    }

    /** An ordinary zip, so it can be unpacked and inspected with anything. */
    @Test
    fun `a bundle is a plain zip with two named entries`() {
        val path = tempBundle()
        writeBundle(path, listOf(line(0L)))

        ZipFile(path.toFile()).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertEquals(setOf("view.hkview", "session.hklog"), names)
            assertNotNull(zip.getEntry("session.hklog"))
        }
    }

    @Test
    fun `the header can be read without walking the logs`() {
        val path = tempBundle()
        writeBundle(path, List(50) { line(it.toLong()) })

        assertEquals(50L, SessionBundleFiles.readHeader(path).lineCount)
    }

    @Test
    fun `bundles are recognised by name, and bare recordings are not`() {
        assertTrue(SessionBundleFiles.looksLikeBundle(Path.of("/tmp/session.hkbundle")))
        assertTrue(SessionBundleFiles.looksLikeBundle(Path.of("/tmp/Session.HKBUNDLE")))
        assertFalse(SessionBundleFiles.looksLikeBundle(Path.of("/tmp/session.hklog.gz")))
        assertFalse(SessionBundleFiles.looksLikeBundle(Path.of("/tmp/view.hkview")))
    }

    @Test
    fun `the zip compresses repetitive output`() {
        val path = tempBundle()
        writeBundle(path, List(5_000) { line(it.toLong(), "send_sync_sensor_request:531, waiting") })

        assertTrue(Files.size(path) < 5_000L * 80L / 4, "expected real compression, got ${Files.size(path)}")
    }

    @Test
    fun `a zip without the expected entries is rejected clearly`() {
        val path = Files.createTempFile("herekitty-empty", ".hkbundle")
        java.util.zip.ZipOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(java.util.zip.ZipEntry("something-else.txt"))
            it.write("nope".toByteArray())
            it.closeEntry()
        }

        assertFailsWith<IllegalStateException> { SessionBundleFiles.readViewJson(path) }
        assertFailsWith<IllegalStateException> { SessionBundleFiles.readHeader(path) }
    }

    @Test
    fun `an empty recording still bundles`() = runTest {
        val path = tempBundle()

        assertEquals(0L, writeBundle(path, emptyList()))

        var seq = 0L
        val read = mutableListOf<LogLine>()
        SessionBundleFiles.read(path, { seq++ }) { read += it }
        assertEquals(emptyList(), read)
        assertEquals("new-flow", ViewConfigCodec.decode(SessionBundleFiles.readViewJson(path), "x").name)
    }
}
