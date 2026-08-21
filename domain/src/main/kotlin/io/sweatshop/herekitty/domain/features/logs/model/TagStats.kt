package io.sweatshop.herekitty.domain.features.logs.model

data class TagStats(
    val tag: String,
    val count: Long,
    val lastSeenMillis: Long,
    val highestLevel: LogLevel,
)
