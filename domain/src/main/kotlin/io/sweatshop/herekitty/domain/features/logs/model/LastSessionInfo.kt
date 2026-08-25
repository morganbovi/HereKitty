package io.sweatshop.herekitty.domain.features.logs.model

import java.nio.file.Path

/** What a clean quit left behind for one device serial, offered back the next time it is missing. */
data class LastSessionInfo(
    val serial: String,
    val path: Path,
    val recordedFrom: String,
    val exportedAtMillis: Long,
    val lineCount: Long,
)
