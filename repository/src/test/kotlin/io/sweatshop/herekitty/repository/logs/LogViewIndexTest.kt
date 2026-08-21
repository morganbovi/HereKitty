package io.sweatshop.herekitty.repository.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogViewIndexTest {

    private fun IndexSnapshot.toList() = (0 until size).map { seqAt(it) }

    private fun IndexSnapshot.counts() = (0 until size).map { countAt(it) }

    @Test
    fun `keeps matches in arrival order`() {
        val index = LogViewIndex()
        listOf(3L, 9L, 12L).forEach(index::add)

        assertEquals(listOf(3L, 9L, 12L), index.snapshot().toList())
    }

    @Test
    fun `grows past its initial capacity`() {
        val index = LogViewIndex()
        (0L until 10_000L).forEach(index::add)

        val snapshot = index.snapshot()
        assertEquals(10_000, snapshot.size)
        assertEquals(0L, snapshot.seqAt(0))
        assertEquals(9_999L, snapshot.seqAt(9_999))
    }

    @Test
    fun `drops sequence numbers below the buffer's oldest line`() {
        val index = LogViewIndex()
        (0L until 100L).forEach(index::add)

        index.pruneBelow(40L)

        assertEquals((40L until 100L).toList(), index.snapshot().toList())
    }

    @Test
    fun `pruning below the first match keeps everything`() {
        val index = LogViewIndex()
        listOf(10L, 20L).forEach(index::add)

        index.pruneBelow(5L)

        assertEquals(listOf(10L, 20L), index.snapshot().toList())
    }

    @Test
    fun `pruning past the last match empties the index`() {
        val index = LogViewIndex()
        listOf(10L, 20L).forEach(index::add)

        index.pruneBelow(100L)

        assertEquals(0, index.snapshot().size)
    }

    /** Panes read a snapshot while the ingest loop keeps appending and pruning. */
    @Test
    fun `a snapshot is unaffected by later appends and prunes`() {
        val index = LogViewIndex()
        (0L until 2_000L).forEach(index::add)
        val snapshot = index.snapshot()

        (2_000L until 20_000L).forEach(index::add)
        index.pruneBelow(15_000L)

        assertEquals(2_000, snapshot.size)
        assertEquals(0L, snapshot.seqAt(0))
        assertEquals(1_999L, snapshot.seqAt(1_999))
    }

    @Test
    fun `keeps appending correctly after a prune`() {
        val index = LogViewIndex()
        (0L until 2_000L).forEach(index::add)
        index.pruneBelow(1_900L)

        (2_000L until 2_100L).forEach(index::add)

        assertEquals((1_900L until 2_100L).toList(), index.snapshot().toList())
    }

    @Test
    fun `clear empties the index`() {
        val index = LogViewIndex()
        (0L until 5_000L).forEach(index::add)

        index.clear()

        assertEquals(0, index.snapshot().size)
        index.add(7L)
        assertEquals(listOf(7L), index.snapshot().toList())
    }

    @Test
    fun `every new row starts with a count of one`() {
        val index = LogViewIndex()
        listOf(3L, 9L).forEach(index::add)

        assertEquals(listOf(1, 1), index.snapshot().counts())
    }

    @Test
    fun `folding a repeat raises the newest row's count without adding a row`() {
        val index = LogViewIndex()
        index.add(1L)
        repeat(4) { index.incrementLast() }
        index.add(2L)

        val snapshot = index.snapshot()
        assertEquals(listOf(1L, 2L), snapshot.toList())
        assertEquals(listOf(5, 1), snapshot.counts())
    }

    @Test
    fun `folding into an empty index does nothing`() {
        val index = LogViewIndex()

        index.incrementLast()

        assertEquals(0, index.snapshot().size)
        assertTrue(index.isEmpty)
    }

    @Test
    fun `counts survive growing past the initial capacity`() {
        val index = LogViewIndex()
        (0L until 5_000L).forEach {
            index.add(it)
            index.incrementLast()
        }

        val snapshot = index.snapshot()
        assertEquals(5_000, snapshot.size)
        assertEquals(2, snapshot.countAt(0))
        assertEquals(2, snapshot.countAt(4_999))
    }

    @Test
    fun `counts stay with their rows through a prune`() {
        val index = LogViewIndex()
        (0L until 100L).forEach { seq ->
            index.add(seq)
            repeat(seq.toInt() % 3) { index.incrementLast() }
        }

        index.pruneBelow(40L)

        val snapshot = index.snapshot()
        assertEquals(40L, snapshot.seqAt(0))
        assertEquals(1 + (40 % 3), snapshot.countAt(0))
        assertEquals(1 + (99 % 3), snapshot.countAt(snapshot.size - 1))
    }

    /**
     * The newest row's count is deliberately live: a line that is still repeating should show its
     * count climbing. Isolating it would mean copying the counts on every snapshot, which is the cost
     * this whole design exists to avoid.
     */
    @Test
    fun `a snapshot sees the newest row keep counting`() {
        val index = LogViewIndex()
        index.add(1L)
        val snapshot = index.snapshot()

        index.incrementLast()

        assertEquals(2, snapshot.countAt(0))
    }

    @Test
    fun `a snapshot is unaffected by rows added after it`() {
        val index = LogViewIndex()
        index.add(1L)
        val snapshot = index.snapshot()

        index.add(2L)
        index.incrementLast()

        assertEquals(1, snapshot.size)
        assertEquals(1, snapshot.countAt(0))
    }

    @Test
    fun `clear empties the counts too`() {
        val index = LogViewIndex()
        index.add(1L)
        index.incrementLast()

        index.clear()

        assertTrue(index.isEmpty)
        index.add(5L)
        assertEquals(listOf(1), index.snapshot().counts())
    }
}
