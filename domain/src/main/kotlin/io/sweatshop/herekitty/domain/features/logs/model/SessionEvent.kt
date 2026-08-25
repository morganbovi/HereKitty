package io.sweatshop.herekitty.domain.features.logs.model

/**
 * A moment in the session's own life — a drop, a recovery, a pause — kept apart from [LogLine]s so
 * every pane sees it regardless of that pane's own filter: it describes the session, not its content.
 */
data class SessionEvent(
    val seq: Long,
    val timestampMillis: Long,
    val kind: Kind,
) {
    enum class Kind(val label: String) {
        Disconnected("Disconnected"),
        Reconnected("Reconnected"),
        Paused("Paused"),
        Resumed("Resumed"),
    }
}
