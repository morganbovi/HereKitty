package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.model.TagStats

/**
 * Every tag seen this session. Kept outside the ring buffer so the tag picker still offers a tag
 * whose lines have already been evicted.
 */
internal class TagRegistry {
    private val counters = HashMap<String, Counter>(256)

    fun record(line: LogLine) {
        val counter = counters.getOrPut(line.tag) { Counter() }
        counter.count++
        counter.lastSeenMillis = line.timestampMillis
        if (line.level.ordinal > counter.highestLevel.ordinal) counter.highestLevel = line.level
    }

    /**
     * Alphabetical, so a tag stays put in the picker however its traffic ebbs and flows.
     *
     * Sorting is on the first letter or digit rather than the raw tag: real tags carry padding and
     * punctuation, and `/      ExampleTagImpl` belongs under E where someone would look for it, not
     * bunched with every other tag that happens to start with a slash.
     */
    fun snapshot(): List<TagStats> = counters.entries
        .map { (tag, counter) -> TagStats(tag, counter.count, counter.lastSeenMillis, counter.highestLevel) }
        .sortedWith(
            compareBy<TagStats, String>(String.CASE_INSENSITIVE_ORDER) { sortKeyFor(it.tag) }
                .thenBy { it.tag },
        )

    private fun sortKeyFor(tag: String): String =
        tag.dropWhile { !it.isLetterOrDigit() }.trimEnd().ifEmpty { tag.trim() }

    fun clear() = counters.clear()

    private class Counter {
        var count = 0L
        var lastSeenMillis = 0L
        var highestLevel = LogLevel.VERBOSE
    }
}
