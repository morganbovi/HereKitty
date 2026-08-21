package io.sweatshop.herekitty.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

fun formatTimeOfDay(epochMillis: Long): String =
    if (epochMillis <= 0L) "--:--:--.---" else timeOfDay.format(Instant.ofEpochMilli(epochMillis))

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

private const val KIB = 1024L
private const val MIB = KIB * 1024L
private const val GIB = MIB * 1024L
