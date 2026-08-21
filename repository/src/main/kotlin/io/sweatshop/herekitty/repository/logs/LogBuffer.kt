package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.BufferStats
import io.sweatshop.herekitty.domain.features.logs.model.LogLine

/**
 * Append-only store for one session's lines, held as fixed-size chunks so that dropping the oldest
 * lines is a single chunk removal rather than a shift of millions of elements.
 *
 * Only the newest chunk is ever mutated, which lets [snapshot] hand readers a consistent view
 * without copying the lines or holding a lock while they are read.
 */
internal class LogBuffer {
    private val chunks = ArrayDeque<Chunk>()

    private var retainedLines = 0L
    private var estimatedBytes = 0L
    private var droppedLines = 0L
    private var totalLinesSeen = 0L

    val firstSeq: Long get() = chunks.firstOrNull()?.startSeq ?: 0L

    fun append(line: LogLine) {
        val chunk = chunks.lastOrNull()?.takeIf { it.count < CHUNK_SIZE }
            ?: Chunk(line.seq).also { chunks.addLast(it) }

        chunk.lines[chunk.count++] = line
        val bytes = estimateBytes(line)
        chunk.bytes += bytes
        estimatedBytes += bytes
        retainedLines++
        totalLinesSeen++
    }

    /** Drops whole chunks until the estimate fits, returning true when anything was evicted. */
    fun trimTo(capacityBytes: Long): Boolean {
        var evicted = false
        while (estimatedBytes > capacityBytes && chunks.size > 1) {
            val chunk = chunks.removeFirst()
            estimatedBytes -= chunk.bytes
            retainedLines -= chunk.count
            droppedLines += chunk.count
            evicted = true
        }
        return evicted
    }

    fun clear() {
        chunks.clear()
        retainedLines = 0L
        estimatedBytes = 0L
        droppedLines = 0L
    }

    fun forEach(action: (LogLine) -> Unit) {
        for (chunk in chunks) {
            val lines = chunk.lines
            for (index in 0 until chunk.count) {
                action(lines[index] ?: continue)
            }
        }
    }

    fun snapshot(): BufferSnapshot = BufferSnapshot(
        chunks = chunks.toTypedArray(),
        lastChunkCount = chunks.lastOrNull()?.count ?: 0,
    )

    fun stats(capacityBytes: Long) = BufferStats(
        lineCount = retainedLines,
        estimatedBytes = estimatedBytes,
        capacityBytes = capacityBytes,
        droppedLines = droppedLines,
        totalLinesSeen = totalLinesSeen,
    )

    internal class Chunk(val startSeq: Long) {
        val lines = arrayOfNulls<LogLine>(CHUNK_SIZE)
        var count = 0
        var bytes = 0L

        /** Sequence numbers ascend within a chunk but may skip, so search rather than subtract. */
        fun indexOf(seq: Long, limit: Int): Int {
            var low = 0
            var high = minOf(limit, count) - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val candidate = lines[mid]?.seq ?: return -1
                when {
                    candidate < seq -> low = mid + 1
                    candidate > seq -> high = mid - 1
                    else -> return mid
                }
            }
            return -1
        }
    }

    internal companion object {
        const val CHUNK_SIZE = 4096

        /**
         * Object header plus fields for [LogLine], its array slot, and the message's String header.
         * Message characters are counted as one byte each, matching the JVM's Latin-1 compact
         * strings for the ASCII that log output almost always is. Tags are interned per session, so
         * they are not counted here.
         */
        private const val LINE_OVERHEAD_BYTES = 96L

        fun estimateBytes(line: LogLine): Long = LINE_OVERHEAD_BYTES + line.message.length
    }
}

/**
 * A stable view of the buffer's chunks. Chunks other than the last are immutable, and the last is
 * only read up to the count captured here, so lines can be read without synchronisation.
 *
 * Lookup locates the chunk by binary search on its first sequence number rather than by dividing an
 * offset. Arithmetic would assume sequence numbers run without gaps, which silently resolves to the
 * *wrong* line the moment they do not.
 */
internal class BufferSnapshot(
    private val chunks: Array<LogBuffer.Chunk>,
    private val lastChunkCount: Int,
) {
    /** Walks every captured line in order. Safe outside the lock, which is what export relies on. */
    fun forEach(action: (LogLine) -> Unit) {
        chunks.forEachIndexed { chunkIndex, chunk ->
            val limit = if (chunkIndex == chunks.size - 1) lastChunkCount else chunk.count
            for (index in 0 until limit) {
                action(chunk.lines[index] ?: continue)
            }
        }
    }

    fun get(seq: Long): LogLine? {
        val chunkIndex = chunkIndexFor(seq)
        if (chunkIndex < 0) return null

        val chunk = chunks[chunkIndex]
        val limit = if (chunkIndex == chunks.size - 1) lastChunkCount else chunk.count
        val within = chunk.indexOf(seq, limit)
        return if (within < 0) null else chunk.lines[within]
    }

    /** The last chunk whose [LogBuffer.Chunk.startSeq] is not greater than [seq]. */
    private fun chunkIndexFor(seq: Long): Int {
        var low = 0
        var high = chunks.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (chunks[mid].startSeq <= seq) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}
