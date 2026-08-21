package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.repository.LogSnapshot
import io.sweatshop.herekitty.domain.features.logs.repository.LogView
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class LogViewImpl(
    initialSpec: LogViewSpec,
    private val onSpecChanged: (LogViewImpl, LogViewSpec) -> Unit,
    private val onClosed: (LogViewImpl) -> Unit,
    private val takeSnapshot: (LogViewImpl) -> LogSnapshot,
) : LogView {

    private val _spec = MutableStateFlow(initialSpec)
    override val spec: StateFlow<LogViewSpec> = _spec.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _isRebuilding = MutableStateFlow(false)
    override val isRebuilding: StateFlow<Boolean> = _isRebuilding.asStateFlow()

    /** All three are only touched while the owning session's lock is held. */
    internal var compiled = CompiledFilter(initialSpec.filter)
    internal var collapseDuplicates = initialSpec.collapseDuplicates
    internal var lastIndexedLine: LogLine? = null
    internal val index = LogViewIndex()

    /**
     * Safe to skip an unchanged spec only because the index is already built for whatever [spec]
     * currently holds — the session indexes a view when it opens it, not when it is first configured.
     */
    override fun configure(spec: LogViewSpec) {
        if (_spec.value == spec) return
        _spec.value = spec
        onSpecChanged(this, spec)
    }

    override fun snapshot(): LogSnapshot = takeSnapshot(this)

    override fun close() = onClosed(this)

    /** Adds a matching line, folding it into the row above when it repeats it. */
    internal fun record(line: LogLine) {
        if (collapseDuplicates && !index.isEmpty && isRepeatOf(lastIndexedLine, line)) {
            index.incrementLast()
            return
        }
        index.add(line.seq)
        lastIndexedLine = line
    }

    internal fun resetIndex() {
        index.clear()
        lastIndexedLine = null
    }

    internal fun setRebuilding(rebuilding: Boolean) {
        _isRebuilding.value = rebuilding
    }

    internal fun bumpRevision() {
        _revision.value++
    }
}

internal class IndexedLogSnapshot(
    private val index: IndexSnapshot,
    private val buffer: BufferSnapshot,
) : LogSnapshot {
    override val size: Int get() = index.size

    override fun get(index: Int): LogLine = buffer.get(this.index.seqAt(index)) ?: EVICTED

    override fun repeatCountAt(index: Int): Int = this.index.countAt(index)

    private companion object {
        val EVICTED = LogLine(
            seq = -1L,
            timestampMillis = 0L,
            pid = 0,
            tid = 0,
            level = LogLevel.VERBOSE,
            tag = "herekitty",
            message = "(line dropped to stay under the memory limit)",
        )
    }
}
