package io.sweatshop.herekitty.repository.logs

/**
 * The sequence numbers a single pane's filter matches, in arrival order, each with how many identical
 * lines it stands for. One long and one int per row keeps many panes over one large buffer affordable.
 *
 * Growing and compacting always allocate fresh arrays so that snapshots already handed out keep
 * pointing at consistent ones.
 */
internal class LogViewIndex {
    private var seqs = LongArray(INITIAL_CAPACITY)
    private var counts = IntArray(INITIAL_CAPACITY)
    private var start = 0
    private var end = 0

    val isEmpty: Boolean get() = end == start

    fun add(seq: Long) {
        if (end == seqs.size) grow()
        seqs[end] = seq
        counts[end] = 1
        end++
    }

    /** Folds another identical line into the newest row instead of giving it one of its own. */
    fun incrementLast() {
        if (end == start) return
        val last = end - 1
        if (counts[last] < Int.MAX_VALUE) counts[last]++
    }

    fun pruneBelow(minSeq: Long) {
        var low = start
        var high = end
        while (low < high) {
            val mid = (low + high) ushr 1
            if (seqs[mid] < minSeq) low = mid + 1 else high = mid
        }
        start = low
    }

    fun clear() {
        seqs = LongArray(INITIAL_CAPACITY)
        counts = IntArray(INITIAL_CAPACITY)
        start = 0
        end = 0
    }

    fun snapshot(): IndexSnapshot = IndexSnapshot(seqs, counts, start, end - start)

    private fun grow() {
        val live = end - start
        val capacity = if (live > seqs.size / 2) seqs.size * 2 else seqs.size
        seqs = LongArray(capacity).also { seqs.copyInto(it, 0, start, end) }
        counts = IntArray(capacity).also { counts.copyInto(it, 0, start, end) }
        start = 0
        end = live
    }

    private companion object {
        const val INITIAL_CAPACITY = 1024
    }
}

internal class IndexSnapshot(
    private val seqs: LongArray,
    private val counts: IntArray,
    private val offset: Int,
    val size: Int,
) {
    fun seqAt(index: Int): Long = seqs[offset + index]

    fun countAt(index: Int): Int = counts[offset + index]
}
