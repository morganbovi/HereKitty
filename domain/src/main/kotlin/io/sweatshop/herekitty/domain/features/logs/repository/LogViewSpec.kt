package io.sweatshop.herekitty.domain.features.logs.repository

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter

/**
 * Everything that decides which rows a pane shows. Collapsing sits here rather than in [LogFilter]
 * because it does not decide whether a line matches — it decides whether a matching line gets its own
 * row or joins the one above it.
 */
data class LogViewSpec(
    val filter: LogFilter = LogFilter(),
    val collapseDuplicates: Boolean = false,
)
