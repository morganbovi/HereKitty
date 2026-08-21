package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.LogLineLayout

/**
 * The columns a particular pane actually draws.
 *
 * Stacking spends a row on metadata, so it is worth being exact about when that row earns its place.
 * A pane watching several tags has to show the tag whatever the global setting says: without it there
 * is no telling which of them a line came from. A pane pinned to one tag is the opposite case — the
 * tag above every line says nothing new, so when it would be the only thing up there the header goes
 * away entirely.
 */
internal fun effectiveColumnsFor(columns: LogColumns, filter: LogFilter): LogColumns {
    if (columns.layout != LogLineLayout.Stacked) return columns

    val hasOtherFields = columns.timestamp || columns.level || columns.processIds

    return when {
        filter.tags.size > 1 -> columns.copy(tag = true)
        filter.tags.size == 1 && !hasOtherFields -> columns.copy(tag = false)
        else -> columns
    }
}
