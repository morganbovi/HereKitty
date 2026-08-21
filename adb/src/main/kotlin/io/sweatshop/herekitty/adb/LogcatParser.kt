package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Incremental parser for `logcat -v long,epoch`, whose records look like:
 *
 * ```
 * [ 1755622801.123  1234: 5678 D/MyTag     ]
 * the message, possibly spanning lines
 * <blank line>
 * ```
 *
 * One instance per session: it owns the pending record and the tag pool that keeps repeated tags
 * from allocating a new String per line.
 */
class LogcatParser(private val nextSeq: () -> Long) {
    private val tagPool = HashMap<String, String>(512)
    private var header: Header? = null
    private val message = StringBuilder(256)
    private var hasMessage = false

    fun accept(rawLine: String): LogLine? {
        val line = rawLine.trimEnd('\r')

        parseHeader(line)?.let { parsed ->
            val completed = emitPending()
            header = parsed
            message.setLength(0)
            hasMessage = false
            return completed
        }

        if (line.isBlank()) return emitPending()

        if (header == null) return orphanLine(line)

        if (hasMessage) message.append('\n')
        message.append(line)
        hasMessage = true
        return null
    }

    fun flush(): LogLine? = emitPending()

    private fun emitPending(): LogLine? {
        val pending = header ?: return null
        header = null
        val line = LogLine(
            seq = nextSeq(),
            timestampMillis = pending.timestampMillis,
            pid = pending.pid,
            tid = pending.tid,
            level = pending.level,
            tag = pending.tag,
            message = message.toString(),
        )
        message.setLength(0)
        hasMessage = false
        return line
    }

    /** Lines with no header, such as `--------- beginning of main`, still belong in the record. */
    private fun orphanLine(line: String): LogLine = LogLine(
        seq = nextSeq(),
        timestampMillis = System.currentTimeMillis(),
        pid = 0,
        tid = 0,
        level = LogLevel.INFO,
        tag = intern(LOGCAT_TAG),
        message = line.removePrefix("--------- ").trim(),
    )

    private fun parseHeader(line: String): Header? {
        if (line.length < MIN_HEADER_LENGTH || line[0] != '[' || line[line.length - 1] != ']') return null
        return parseEpochHeader(line) ?: parseDateHeader(line)
    }

    private fun parseEpochHeader(line: String): Header? {
        var cursor = skipSpaces(line, 1)

        val secondsEnd = digitsEnd(line, cursor)
        if (secondsEnd == cursor || secondsEnd >= line.length || line[secondsEnd] != '.') return null
        val seconds = line.substring(cursor, secondsEnd).toLongOrNull() ?: return null

        cursor = secondsEnd + 1
        val millisEnd = digitsEnd(line, cursor)
        if (millisEnd == cursor) return null
        val millis = line.substring(cursor, millisEnd).padEnd(3, '0').take(3).toLongOrNull() ?: return null

        return parseTail(line, millisEnd, seconds * 1_000L + millis)
    }

    private fun parseDateHeader(line: String): Header? {
        val match = DATE_HEADER.matchEntire(line) ?: return null
        val (month, day, hour, minute, second, millis, pid, tid, level, tag) = match.destructured
        val timestamp = LocalDateTime.of(
            LocalDate.now().year,
            month.toInt(),
            day.toInt(),
            hour.toInt(),
            minute.toInt(),
            second.toInt(),
            millis.toInt() * 1_000_000,
        ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return Header(
            timestampMillis = timestamp,
            pid = pid.toIntOrNull() ?: 0,
            tid = tid.toIntOrNull() ?: 0,
            level = LogLevel.fromLetter(level.first()),
            tag = intern(tag.trim()),
        )
    }

    private fun parseTail(line: String, start: Int, timestampMillis: Long): Header? {
        var cursor = skipSpaces(line, start)

        val pidEnd = digitsEnd(line, cursor)
        if (pidEnd == cursor) return null
        val pid = line.substring(cursor, pidEnd).toIntOrNull() ?: return null

        cursor = pidEnd
        if (cursor >= line.length || line[cursor] != ':') return null
        cursor = skipSpaces(line, cursor + 1)

        val tidEnd = digitsEnd(line, cursor)
        if (tidEnd == cursor) return null
        val tid = line.substring(cursor, tidEnd).toIntOrNull() ?: return null

        cursor = skipSpaces(line, tidEnd)
        if (cursor + 1 >= line.length || line[cursor + 1] != '/') return null
        val level = LogLevel.fromLetter(line[cursor])

        val tag = line.substring(cursor + 2, line.length - 1).trim()
        return Header(timestampMillis, pid, tid, level, intern(tag))
    }

    private fun intern(tag: String): String = tagPool.getOrPut(tag) { tag }

    private fun skipSpaces(line: String, from: Int): Int {
        var cursor = from
        while (cursor < line.length && line[cursor] == ' ') cursor++
        return cursor
    }

    private fun digitsEnd(line: String, from: Int): Int {
        var cursor = from
        while (cursor < line.length && line[cursor] in '0'..'9') cursor++
        return cursor
    }

    private class Header(
        val timestampMillis: Long,
        val pid: Int,
        val tid: Int,
        val level: LogLevel,
        val tag: String,
    )

    private companion object {
        const val LOGCAT_TAG = "logcat"
        const val MIN_HEADER_LENGTH = 12

        val DATE_HEADER =
            Regex("""^\[\s+(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(\d+):\s*(\d+)\s+([VDIWEFS])/(.*?)\s*]$""")
    }
}
