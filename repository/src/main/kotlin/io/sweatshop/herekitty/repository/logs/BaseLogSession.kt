package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.BufferStats
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.model.TagStats
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.logs.repository.LogSnapshot
import io.sweatshop.herekitty.domain.features.logs.repository.LogView
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.repository.views.ViewConfigCodec
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The recording machinery shared by every kind of session: one capped buffer, a tag registry, and any
 * number of filtered [LogView]s over it.
 *
 * Subclasses only decide where lines come from, and push them in through [submit].
 */
internal abstract class BaseLogSession(
    override val id: SessionId,
    private val memoryCapBytes: StateFlow<Long>,
    initialConnection: ConnectionState,
    parentScope: CoroutineScope,
) : LogSession {

    /** Guards the buffer, the tag registry, and every view's index together. */
    private val lock = Any()
    private val buffer = LogBuffer()
    private val tagRegistry = TagRegistry()
    private val views = CopyOnWriteArrayList<LogViewImpl>()
    private val incoming = Channel<LogLine>(INCOMING_CAPACITY)

    protected val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    private var sequence = 0L

    @Volatile private var pendingRevision = false
    private var lastTagPublishNanos = 0L

    protected val mutableConnection = MutableStateFlow(initialConnection)
    override val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()

    private val mutableStats = MutableStateFlow(BufferStats.empty(memoryCapBytes.value))
    override val stats: StateFlow<BufferStats> = mutableStats.asStateFlow()

    private val mutableTags = MutableStateFlow<List<TagStats>>(emptyList())
    override val tags: StateFlow<List<TagStats>> = mutableTags.asStateFlow()

    init {
        launchIngest()
        launchRevisionTicker()
    }

    /** Called from a single producer coroutine, so the counter needs no synchronisation. */
    protected fun nextSequence(): Long = sequence++

    protected suspend fun submit(line: LogLine) = incoming.send(line)

    override fun openView(spec: LogViewSpec): LogView {
        val view = LogViewImpl(
            initialSpec = spec,
            onSpecChanged = ::reindex,
            onClosed = { views.remove(it) },
            takeSnapshot = ::snapshotFor,
        )
        views.add(view)
        // Registered before indexing, so a line arriving mid-walk is either already in the buffer the
        // walk covers or recorded incrementally after it — the lock makes it one or the other.
        reindex(view, spec)
        return view
    }

    override fun clear() {
        synchronized(lock) {
            buffer.clear()
            tagRegistry.clear()
            views.forEach { it.resetIndex() }
        }
        mutableTags.value = emptyList()
        publishStats()
        views.forEach { it.bumpRevision() }
    }

    override fun setPaused(paused: Boolean) = Unit

    override fun reconnect() = Unit

    override suspend fun exportTo(path: Path): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val recording = takeRecording()
            RecordingFiles.write(
                path = path,
                recordedFrom = recording.recordedFrom,
                serial = recording.serial,
                exportedAtMillis = recording.exportedAtMillis,
                lineCount = recording.lineCount,
                lines = { write -> recording.snapshot.forEach(write) },
            )
        }
    }

    override suspend fun exportBundleTo(path: Path, view: ViewConfig): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val recording = takeRecording()
                SessionBundleFiles.write(
                    path = path,
                    viewJson = ViewConfigCodec.encode(view),
                    recordedFrom = recording.recordedFrom,
                    serial = recording.serial,
                    exportedAtMillis = recording.exportedAtMillis,
                    lineCount = recording.lineCount,
                    lines = { write -> recording.snapshot.forEach(write) },
                )
            }
        }

    /**
     * Snapshots under the lock so the write itself happens outside it: a multi-million-line export
     * must not stall capture for its whole duration.
     */
    private fun takeRecording(): Recording {
        val snapshot = synchronized(lock) { buffer.snapshot() }
        val currentSource = source.value
        return Recording(
            snapshot = snapshot,
            recordedFrom = currentSource.label,
            serial = currentSource.detail,
            exportedAtMillis = System.currentTimeMillis(),
            lineCount = stats.value.lineCount,
        )
    }

    private class Recording(
        val snapshot: BufferSnapshot,
        val recordedFrom: String,
        val serial: String,
        val exportedAtMillis: Long,
        val lineCount: Long,
    )

    open fun dispose() {
        scope.cancel()
        incoming.close()
        views.clear()
    }

    /**
     * Drains [incoming] and commits in batches.
     *
     * The receive is never wrapped in a timeout: cancelling a suspended `receive` can discard an
     * element it has already taken from the channel, which would lose lines and leave gaps in the
     * sequence numbers. UI pacing lives in [launchRevisionTicker] instead.
     */
    private fun launchIngest() {
        scope.launch(Dispatchers.Default) {
            val batch = ArrayList<LogLine>(FLUSH_SIZE)
            while (isActive) {
                batch.add(incoming.receive())
                while (batch.size < FLUSH_SIZE) {
                    batch.add(incoming.tryReceive().getOrNull() ?: break)
                }
                commit(batch)
                batch.clear()
            }
        }
    }

    /** Caps pane repaints at roughly one per frame, however fast lines are arriving. */
    private fun launchRevisionTicker() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                publishIfDirty()
            }
        }
    }

    private fun commit(batch: List<LogLine>) {
        val capacity = memoryCapBytes.value
        synchronized(lock) {
            for (line in batch) {
                buffer.append(line)
                tagRegistry.record(line)
                for (view in views) {
                    if (view.compiled.matches(line)) view.record(line)
                }
            }
            if (buffer.trimTo(capacity)) {
                val minSeq = buffer.firstSeq
                for (view in views) {
                    view.index.pruneBelow(minSeq)
                    // With nothing left to fold into, the next line has to start a fresh row.
                    if (view.index.isEmpty) view.lastIndexedLine = null
                }
            }
        }
        pendingRevision = true
    }

    private fun publishIfDirty() {
        if (!pendingRevision) return
        pendingRevision = false

        publishStats()
        for (view in views) view.bumpRevision()

        val now = System.nanoTime()
        if (now - lastTagPublishNanos >= TAG_PUBLISH_INTERVAL_NANOS) {
            lastTagPublishNanos = now
            mutableTags.value = synchronized(lock) { tagRegistry.snapshot() }
        }
    }

    private fun publishStats() {
        mutableStats.value = synchronized(lock) { buffer.stats(memoryCapBytes.value) }
    }

    private fun reindex(view: LogViewImpl, spec: LogViewSpec) {
        scope.launch(Dispatchers.Default) {
            view.setRebuilding(true)
            try {
                val compiled = CompiledFilter(spec.filter)
                synchronized(lock) {
                    view.compiled = compiled
                    view.collapseDuplicates = spec.collapseDuplicates
                    view.resetIndex()
                    buffer.forEach { line -> if (compiled.matches(line)) view.record(line) }
                }
            } finally {
                view.setRebuilding(false)
                view.bumpRevision()
            }
        }
    }

    private fun snapshotFor(view: LogViewImpl): LogSnapshot = synchronized(lock) {
        IndexedLogSnapshot(view.index.snapshot(), buffer.snapshot())
    }

    protected companion object {
        const val INCOMING_CAPACITY = 1 shl 16
        const val FLUSH_SIZE = 2048
        const val FLUSH_INTERVAL_MILLIS = 33L
        val TAG_PUBLISH_INTERVAL_NANOS = 500L * 1_000_000L
    }
}
