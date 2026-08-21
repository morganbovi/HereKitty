package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RecordingFilesTest {

    private fun tempFile(): Path = Files.createTempFile("herekitty", ".hklog.gz").also { Files.delete(it) }

    private fun line(seq: Long, tag: String = "Radio", message: String = "sent packet") =
        LogLine(seq, 1_787_172_644_106L + seq, 2099, 3244, LogLevel.WARN, tag, message)

    private suspend fun roundTrip(lines: List<LogLine>): Pair<LogRecordingCodec.Header, List<LogLine>> {
        val path = tempFile()
        RecordingFiles.write(
            path = path,
            recordedFrom = "SM S911U",
            serial = "EXAMPLE0001",
            exportedAtMillis = 1_787_172_700_000L,
            lineCount = lines.size.toLong(),
            lines = { write -> lines.forEach(write) },
        )

        var seq = 0L
        val read = mutableListOf<LogLine>()
        val header = RecordingFiles.read(path, { seq++ }) { read += it }
        return header to read
    }

    @Test
    fun `writes and reads back every field of every line`() = runTest {
        val original = listOf(
            line(0L),
            line(1L, tag = "/      ExampleTagImpl", message = "multi\nline\nmessage"),
            line(2L, message = ""),
        )

        val (header, read) = roundTrip(original)

        assertEquals("SM S911U", header.recordedFrom)
        assertEquals("EXAMPLE0001", header.serial)
        assertEquals(3L, header.lineCount)
        assertEquals(3, read.size)
        original.zip(read).forEach { (before, after) ->
            assertEquals(before.timestampMillis, after.timestampMillis)
            assertEquals(before.pid, after.pid)
            assertEquals(before.tid, after.tid)
            assertEquals(before.level, after.level)
            assertEquals(before.tag, after.tag)
            assertEquals(before.message, after.message)
        }
    }

    /** A newline inside a message must not be mistaken for the end of a record. */
    @Test
    fun `survives messages containing newlines and quotes`() = runTest {
        val awkward = "line one\nline \"two\"\ttabbed\\slash"

        val (_, read) = roundTrip(listOf(line(0L, message = awkward)))

        assertEquals(awkward, read.single().message)
    }

    @Test
    fun `numbers the lines it reads with the caller's sequence`() = runTest {
        val (_, read) = roundTrip(List(5) { line(100L + it) })

        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), read.map { it.seq })
    }

    @Test
    fun `interns repeated tags so a large recording does not allocate one per line`() = runTest {
        val (_, read) = roundTrip(List(4) { line(it.toLong(), tag = "sensors-hal") })

        assertSame(read[0].tag, read[1].tag)
        assertSame(read[0].tag, read[3].tag)
    }

    @Test
    fun `reads the header without walking the whole file`() = runTest {
        val path = tempFile()
        RecordingFiles.write(path, "SM S906U", "RFCT42DKQBP", 1L, 2L) { write ->
            repeat(2) { write(line(it.toLong())) }
        }

        val header = RecordingFiles.readHeader(path)

        assertEquals("SM S906U", header.recordedFrom)
        assertEquals(2L, header.lineCount)
    }

    @Test
    fun `compresses repetitive log output`() = runTest {
        val path = tempFile()
        val written = RecordingFiles.write(path, "device", "serial", 1L, 5_000L) { write ->
            repeat(5_000) { write(line(it.toLong(), message = "send_sync_sensor_request:531, waiting")) }
        }

        assertEquals(5_000L, written)
        val uncompressedEstimate = 5_000L * 80L
        assertTrue(
            Files.size(path) < uncompressedEstimate / 4,
            "expected real compression, got ${Files.size(path)} bytes",
        )
    }

    @Test
    fun `refuses a file written by a different format version`() = runTest {
        val path = Files.createTempFile("herekitty-bad", ".hklog.gz")
        java.util.zip.GZIPOutputStream(Files.newOutputStream(path)).bufferedWriter().use {
            it.write("""{"herekitty":99,"recordedFrom":"x","serial":"y","exportedAtMillis":0,"lineCount":0}""")
            it.newLine()
        }

        assertFailsWith<IllegalArgumentException> { RecordingFiles.readHeader(path) }
    }

    @Test
    fun `an empty recording round trips to nothing`() = runTest {
        val (header, read) = roundTrip(emptyList())

        assertEquals(0L, header.lineCount)
        assertEquals(emptyList(), read)
    }
}
