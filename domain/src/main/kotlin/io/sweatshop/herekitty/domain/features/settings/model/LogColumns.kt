package io.sweatshop.herekitty.domain.features.settings.model

/** How a log line is arranged. */
enum class LogLineLayout {
    /** Everything on one line, metadata in fixed-width columns before the message. */
    Columns,

    /**
     * A tiny metadata line above the message, which then gets the pane's whole width. Worth the extra
     * row in narrow panes, where the timestamp and tag columns cost more than the message has left.
     */
    Stacked,
}

/** Which parts of a log line are drawn, and how they are arranged. */
data class LogColumns(
    val timestamp: Boolean = true,
    val level: Boolean = true,
    val tag: Boolean = true,
    val processIds: Boolean = false,
    val softWrap: Boolean = false,
    val layout: LogLineLayout = LogLineLayout.Stacked,
)
