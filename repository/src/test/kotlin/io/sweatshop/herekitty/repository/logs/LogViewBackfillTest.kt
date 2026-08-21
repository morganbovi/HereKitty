package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.LogSnapshot
import io.sweatshop.herekitty.domain.features.logs.repository.LogView
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * A pane added late must show the same history as one that was open all along — a view is opened onto
 * what the session holds, not onto what happens next.
 */
class LogViewBackfillTest {

    @Test
    fun `a view opened after the fact sees the lines already recorded`() = withSession { session ->
        session.record(30)

        val view = session.openView()

        assertEquals(30, view.awaitLines(30).size)
    }

    @Test
    fun `an unfiltered view opened late is backfilled, not left empty`() = withSession { session ->
        session.record(10)

        // The spec a fresh pane has: no tags, no query, nothing collapsed.
        val view = session.openView(LogViewSpec())

        assertEquals(10, view.awaitLines(10).size)
    }

    @Test
    fun `a view opened with a filter is backfilled through it`() = withSession { session ->
        session.record(10, tag = "Radio")
        session.record(6, tag = "Outcome")

        val view = session.openView(LogViewSpec(filter = LogFilter(tags = setOf("Outcome"))))

        val snapshot = view.awaitLines(6)
        assertEquals(6, snapshot.size)
        assertEquals(List(6) { "Outcome" }, (0 until snapshot.size).map { snapshot[it].tag })
    }

    @Test
    fun `a backfilled view keeps taking new lines afterwards`() = withSession { session ->
        session.record(5)
        val view = session.openView()
        view.awaitLines(5)

        session.record(7)

        assertEquals(12, view.awaitLines(12).size)
    }

    @Test
    fun `reopening a view is what happens when a pane moves, and loses nothing`() = withSession { session ->
        session.record(20, tag = "Radio")
        val spec = LogViewSpec(filter = LogFilter(tags = setOf("Radio")))

        val before = session.openView(spec).awaitLines(20).size
        // Dragging a pane elsewhere disposes its view and opens a new one in the new position.
        val after = session.openView(spec).awaitLines(20).size

        assertEquals(before, after)
    }
}

private fun withSession(block: suspend (TestLogSession) -> Unit) = runBlocking {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val session = TestLogSession(scope)
    try {
        withTimeout(AWAIT_TIMEOUT_MILLIS) { block(session) }
    } finally {
        scope.cancel()
    }
}

private class TestLogSession(scope: CoroutineScope) : BaseLogSession(
    id = SessionId(1L),
    memoryCapBytes = MutableStateFlow(64L * 1024 * 1024),
    initialConnection = ConnectionState.Streaming,
    parentScope = scope,
) {
    override val isLive: Boolean = true

    override val source = MutableStateFlow<SessionSource>(
        SessionSource.Recording(java.nio.file.Path.of("test.hklog"), recordedFrom = "test", recordedLineCount = 0L),
    )

    suspend fun record(count: Int, tag: String = "Radio") {
        repeat(count) {
            submit(
                LogLine(
                    seq = nextSequence(),
                    timestampMillis = 1_700_000_000_000L + it,
                    pid = 1,
                    tid = 1,
                    level = LogLevel.INFO,
                    tag = tag,
                    message = "line $it",
                ),
            )
        }
        awaitStats(count)
    }

    private suspend fun awaitStats(atLeast: Int) {
        while (stats.value.lineCount < atLeast) delay(POLL_MILLIS)
    }
}

/** Indexing runs off the caller's thread, so the assertion has to wait for it rather than assume it. */
private suspend fun LogView.awaitLines(expected: Int): LogSnapshot {
    while (true) {
        val snapshot = snapshot()
        if (snapshot.size >= expected && !isRebuilding.value) return snapshot
        delay(POLL_MILLIS)
    }
}

private const val POLL_MILLIS = 2L
private const val AWAIT_TIMEOUT_MILLIS = 10_000L
