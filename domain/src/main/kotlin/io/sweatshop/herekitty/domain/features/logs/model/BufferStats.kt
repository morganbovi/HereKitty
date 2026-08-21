package io.sweatshop.herekitty.domain.features.logs.model

data class BufferStats(
    val lineCount: Long,
    val estimatedBytes: Long,
    val capacityBytes: Long,
    val droppedLines: Long,
    val totalLinesSeen: Long,
) {
    val usedFraction: Float
        get() = if (capacityBytes <= 0L) {
            0f
        } else {
            (estimatedBytes.toDouble() / capacityBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val hasDropped: Boolean get() = droppedLines > 0L

    companion object {
        fun empty(capacityBytes: Long) = BufferStats(0L, 0L, capacityBytes, 0L, 0L)
    }
}
