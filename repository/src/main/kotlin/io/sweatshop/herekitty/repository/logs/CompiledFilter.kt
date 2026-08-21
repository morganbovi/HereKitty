package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLine

/** A [LogFilter] with its regex compiled once, ready to be tested against every incoming line. */
internal class CompiledFilter(val filter: LogFilter) {
    private val regex: Regex? = when {
        !filter.useRegex || filter.query.isBlank() -> null
        else -> runCatching {
            Regex(filter.query, if (filter.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        }.getOrNull()
    }

    val hasInvalidRegex: Boolean = filter.useRegex && filter.query.isNotBlank() && regex == null

    fun matches(line: LogLine): Boolean {
        if (line.level.ordinal < filter.minLevel.ordinal) return false
        if (filter.tags.isNotEmpty() && line.tag !in filter.tags) return false
        if (line.tag in filter.excludeTags) return false

        if (filter.query.isBlank()) return true

        regex?.let { return it.containsMatchIn(line.message) || it.containsMatchIn(line.tag) }

        // A half-typed pattern should not blank the pane out.
        if (hasInvalidRegex) return true

        val ignoreCase = !filter.matchCase
        return line.message.contains(filter.query, ignoreCase) || line.tag.contains(filter.query, ignoreCase)
    }
}
