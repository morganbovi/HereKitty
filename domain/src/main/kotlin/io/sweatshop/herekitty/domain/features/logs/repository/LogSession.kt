package io.sweatshop.herekitty.domain.features.logs.repository

import io.sweatshop.herekitty.domain.features.logs.model.BufferStats
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.SessionEvent
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.model.TagStats
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import java.nio.file.Path
import kotlinx.coroutines.flow.StateFlow

@JvmInline
value class SessionId(val value: Long)

/**
 * One recording, from a device or from a file. Everything that arrives is kept up to the configured
 * memory limit; panes read it through [LogView]s.
 */
interface LogSession {
    val id: SessionId

    val source: StateFlow<SessionSource>
    val connection: StateFlow<ConnectionState>
    val stats: StateFlow<BufferStats>

    /** Every tag seen since the session opened, alphabetical. Never evicted. */
    val tags: StateFlow<List<TagStats>>

    /** Disconnects, reconnects, and pauses, in order. Never evicted — there are only ever a handful. */
    val events: StateFlow<List<SessionEvent>>

    /** False for an imported recording, which has nothing left to capture. */
    val isLive: Boolean

    /**
     * Opens a filtered view over everything the session still holds, not just what arrives next: a
     * pane added an hour in shows the same history as one that was there from the start.
     */
    fun openView(spec: LogViewSpec = LogViewSpec()): LogView

    fun clear()

    fun setPaused(paused: Boolean)

    fun reconnect()

    /** Writes everything currently held to [path], returning how many lines were written. */
    suspend fun exportTo(path: Path): Result<Long>

    /** Writes the recording and [view] together as one shareable bundle. */
    suspend fun exportBundleTo(path: Path, view: ViewConfig): Result<Long>
}
