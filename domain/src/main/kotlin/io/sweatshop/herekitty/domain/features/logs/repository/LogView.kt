package io.sweatshop.herekitty.domain.features.logs.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * A filtered window onto a [LogSession]'s buffer. Views hold an index of matching lines rather than
 * copies, so a session can back many panes at once.
 */
interface LogView {
    val spec: StateFlow<LogViewSpec>

    /** Bumped when the match set changes, at most once per UI frame. */
    val revision: StateFlow<Long>

    val isRebuilding: StateFlow<Boolean>

    fun configure(spec: LogViewSpec)

    fun snapshot(): LogSnapshot

    fun close()
}
