package io.sweatshop.herekitty.domain.features.views.model

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter

@JvmInline
value class PaneId(val value: Long)

/**
 * One pane's saved specification. This is data rather than presenter state so a setup can be named,
 * reapplied to another source, and handed to someone else.
 */
data class PaneConfig(
    val id: PaneId,
    val filter: LogFilter = LogFilter(),
    val followTail: Boolean = true,
    val collapseDuplicates: Boolean = false,
) {
    /** What to call this pane in a tab or a config listing. */
    val label: String
        get() = when {
            filter.tags.size == 1 -> filter.tags.first().trim()
            filter.tags.size > 1 -> "${filter.tags.size} tags"
            filter.query.isNotBlank() -> "\"${filter.query}\""
            else -> "All"
        }
}
