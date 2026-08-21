package io.sweatshop.herekitty.features.workspace

/**
 * Numbers only the titles that would otherwise be ambiguous.
 *
 * A lone "New tab" stays "New tab"; two of them become "New tab 1" and "New tab 2". Same for two tabs
 * running the same saved view. Numbering everything unconditionally would put a "1" on tabs that were
 * already unmistakable.
 */
internal fun disambiguateTitles(baseTitles: List<String>): List<String> {
    val totals = baseTitles.groupingBy { it }.eachCount()
    val used = mutableMapOf<String, Int>()

    return baseTitles.map { base ->
        if (totals.getValue(base) == 1) {
            base
        } else {
            val ordinal = used.getOrDefault(base, 0) + 1
            used[base] = ordinal
            "$base $ordinal"
        }
    }
}
