package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.math.abs

/**
 * Whether a stacked line needs its own metadata header, or belongs to the run above it.
 *
 * Without this, stacking would cost a row per line and halve how much of a burst fits on screen. A
 * run of lines from the same tag at the same moment reads as one block, the way consecutive messages
 * from one sender do in a chat.
 */
internal fun needsMetadataHeader(previous: LogLine?, current: LogLine): Boolean {
    if (previous == null) return true
    if (previous.tag != current.tag) return true
    if (previous.level != current.level) return true
    if (previous.pid != current.pid) return true
    return abs(current.timestampMillis - previous.timestampMillis) >= GROUPING_WINDOW_MILLIS
}

/** Past this gap, lines are separate events even from the same tag. */
private const val GROUPING_WINDOW_MILLIS = 1_000L
