package io.sweatshop.herekitty.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

fun formatTimeOfDay(epochMillis: Long): String =
    if (epochMillis <= 0L) "--:--:--.---" else timeOfDay.format(Instant.ofEpochMilli(epochMillis))

private val captureDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US).withZone(ZoneId.systemDefault())

/** A day-and-time stamp, for something captured once rather than a log line's within-a-run timestamp. */
fun formatCaptureDate(epochMillis: Long): String =
    if (epochMillis <= 0L) "Unknown date" else captureDate.format(Instant.ofEpochMilli(epochMillis))

fun formatBytes(bytes: Long): String = when {
    bytes >= GIB -> String.format(Locale.US, "%.1f GB", bytes / GIB.toDouble())
    bytes >= MIB -> "${bytes / MIB} MB"
    bytes >= KIB -> "${bytes / KIB} KB"
    else -> "$bytes B"
}

fun formatCount(count: Long): String = when {
    count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 10_000L -> "${count / 1_000L}k"
    else -> count.toString()
}

/**
 * Keeps both ends of a long name rather than just the start: a file's extension and whatever makes
 * it distinct from its siblings (a date, a version) tend to live at the end, not the middle.
 */
fun truncateMiddle(text: String, maxLength: Int = 24): String {
    if (text.length <= maxLength) return text
    val headLength = (maxLength - 1) / 2
    val tailLength = maxLength - 1 - headLength
    return "${text.take(headLength)}…${text.takeLast(tailLength)}"
}

private const val KIB = 1024L
private const val MIB = KIB * 1024L
private const val GIB = MIB * 1024L
