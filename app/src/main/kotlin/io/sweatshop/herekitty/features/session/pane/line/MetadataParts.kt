package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.ui.format.formatTimeOfDay

/**
 * One field of a stacked line's metadata. The tag is flagged because it is the only part that is also
 * a control: double-clicking it filters the pane by it.
 */
internal data class MetadataPart(val text: String, val isTag: Boolean = false)

/** The tiny line above a stacked message: whichever of tag, time, level and ids are switched on. */
internal fun stackedMetadataParts(line: LogLine, columns: LogColumns): List<MetadataPart> = buildList {
    if (columns.tag) add(MetadataPart(line.tag.trim(), isTag = true))
    if (columns.timestamp) add(MetadataPart(formatTimeOfDay(line.timestampMillis)))
    if (columns.level) add(MetadataPart(line.level.letter.toString()))
    if (columns.processIds) add(MetadataPart("${line.pid}-${line.tid}"))
}
