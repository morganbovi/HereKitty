package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogBufferTest {

    private fun line(seq: Long, message: String = "m", tag: String = "t") =
        LogLine(seq, 1_000L + seq, 1, 2, LogLevel.DEBUG, tag, message)

    private fun LogBuffer.appendRange(range: LongRange) = range.forEach { append(line(it)) }

    @Test
    fun `reads back every appended line by sequence number`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 10_000L)

        val snapshot = buffer.snapshot()
        assertEquals(0L, snapshot.get(0L)?.seq)
        assertEquals(4095L, snapshot.get(4095L)?.seq)
        assertEquals(4096L, snapshot.get(4096L)?.seq)
        assertEquals(9999L, snapshot.get(9999L)?.seq)
        assertNull(snapshot.get(10_000L))
    }

    @Test
    fun `counts lines and reports the configured capacity`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 500L)

        val stats = buffer.stats(capacityBytes = 1_000_000L)
        assertEquals(500L, stats.lineCount)
        assertEquals(500L, stats.totalLinesSeen)
        assertEquals(0L, stats.droppedLines)
        assertEquals(1_000_000L, stats.capacityBytes)
        assertTrue(stats.estimatedBytes > 0L)
    }

    @Test
    fun `evicts whole chunks once the estimate exceeds the cap`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 20_000L)
        val fullBytes = buffer.stats(Long.MAX_VALUE).estimatedBytes

        assertTrue(buffer.trimTo(fullBytes / 2))

        val stats = buffer.stats(fullBytes / 2)
        assertTrue(stats.estimatedBytes <= fullBytes / 2)
        assertEquals(20_000L, stats.lineCount + stats.droppedLines)
        assertEquals(20_000L, stats.totalLinesSeen)
        assertEquals(0L, buffer.firstSeq % LogBuffer.CHUNK_SIZE)
    }

    @Test
    fun `keeps the newest chunk even when a single chunk exceeds the cap`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 10L)

        buffer.trimTo(capacityBytes = 1L)

        assertEquals(10L, buffer.stats(1L).lineCount)
        assertNotNull(buffer.snapshot().get(0L))
    }

    @Test
    fun `reports evicted sequence numbers as absent`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 20_000L)
        buffer.trimTo(buffer.stats(Long.MAX_VALUE).estimatedBytes / 2)

        val snapshot = buffer.snapshot()
        assertNull(snapshot.get(0L))
        assertNotNull(snapshot.get(19_999L))
        assertEquals(buffer.firstSeq, snapshot.get(buffer.firstSeq)?.seq)
    }

    /** A snapshot must survive eviction, because panes read it after the ingest loop moves on. */
    @Test
    fun `a snapshot taken before eviction still reads its lines`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 20_000L)
        val snapshot = buffer.snapshot()

        buffer.trimTo(1L)

        assertEquals(0L, snapshot.get(0L)?.seq)
        assertEquals(19_999L, snapshot.get(19_999L)?.seq)
    }

    /** Readers hold a snapshot while the ingest loop appends to the newest chunk. */
    @Test
    fun `a snapshot does not expose lines appended after it was taken`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 100L)
        val snapshot = buffer.snapshot()

        buffer.appendRange(100L until 200L)

        assertEquals(99L, snapshot.get(99L)?.seq)
        assertNull(snapshot.get(100L))
    }

    @Test
    fun `clear drops the contents while sequence numbers keep advancing`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 5_000L)

        buffer.clear()
        buffer.append(line(5_000L))

        assertEquals(1L, buffer.stats(1_000L).lineCount)
        assertEquals(5_000L, buffer.firstSeq)
        assertEquals(5_000L, buffer.snapshot().get(5_000L)?.seq)
        assertNull(buffer.snapshot().get(4_999L))
    }

    @Test
    fun `visits every retained line in order`() {
        val buffer = LogBuffer()
        buffer.appendRange(0L until 9_000L)

        val seen = mutableListOf<Long>()
        buffer.forEach { seen += it.seq }

        assertEquals(9_000, seen.size)
        assertEquals((0L until 9_000L).toList(), seen)
    }

    /**
     * Sequence numbers are allocated by the parser and may skip. Lookup must never resolve a gap to
     * a neighbouring line: doing so put lines with the wrong tag and level into filtered panes.
     */
    @Test
    fun `resolves lines correctly when sequence numbers skip`() {
        val buffer = LogBuffer()
        val seqs = (0L until 12_000L step 3L).toList()
        seqs.forEach { buffer.append(line(it, message = "seq$it")) }

        val snapshot = buffer.snapshot()
        seqs.forEach { seq ->
            assertEquals(seq, snapshot.get(seq)?.seq, "wrong line for seq $seq")
            assertEquals("seq$seq", snapshot.get(seq)?.message)
        }
    }

    @Test
    fun `returns nothing for a sequence number that was never appended`() {
        val buffer = LogBuffer()
        listOf(0L, 1L, 5L, 6L).forEach { buffer.append(line(it)) }

        val snapshot = buffer.snapshot()
        assertNull(snapshot.get(2L))
        assertNull(snapshot.get(3L))
        assertNull(snapshot.get(4L))
        assertEquals(5L, snapshot.get(5L)?.seq)
        assertNull(snapshot.get(7L))
    }

    @Test
    fun `resolves lines correctly across gaps after eviction`() {
        val buffer = LogBuffer()
        val seqs = (0L until 40_000L step 2L).toList()
        seqs.forEach { buffer.append(line(it)) }
        buffer.trimTo(buffer.stats(Long.MAX_VALUE).estimatedBytes / 3)

        val snapshot = buffer.snapshot()
        val retained = seqs.filter { it >= buffer.firstSeq }
        retained.forEach { seq -> assertEquals(seq, snapshot.get(seq)?.seq, "wrong line for seq $seq") }
        assertNull(snapshot.get(buffer.firstSeq - 2L))
    }

    @Test
    fun `counts a longer message as costing more memory`() {
        val small = LogBuffer().apply { append(line(0L, message = "x")) }.stats(0L).estimatedBytes
        val large = LogBuffer().apply { append(line(0L, message = "x".repeat(1_000))) }.stats(0L).estimatedBytes

        assertEquals(999L, large - small)
    }
}
