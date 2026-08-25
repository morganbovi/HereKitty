package io.sweatshop.herekitty.domain.features.logs.model

data class LogFilter(
    val tags: Set<String> = emptySet(),
    val query: String = "",
    val minLevel: LogLevel = LogLevel.VERBOSE,
    val matchCase: Boolean = false,
    val useRegex: Boolean = false,
    val excludeTags: Set<String> = emptySet(),
) {
    val isPassThrough: Boolean
        get() = tags.isEmpty() && excludeTags.isEmpty() && query.isBlank() && minLevel == LogLevel.VERBOSE
}
