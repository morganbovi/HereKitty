package io.sweatshop.herekitty.domain.features.logs.model

class LogLine(
    val seq: Long,
    val timestampMillis: Long,
    val pid: Int,
    val tid: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    override fun toString(): String = "$timestampMillis $pid:$tid ${level.letter}/$tag: $message"
}
