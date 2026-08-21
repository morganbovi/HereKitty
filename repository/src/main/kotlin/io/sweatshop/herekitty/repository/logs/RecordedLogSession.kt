package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A recording loaded from a file. It fills the same buffer as a live capture, so every filter and
 * every view config works against it unchanged; it simply never receives anything new.
 */
internal class RecordedLogSession(
    id: SessionId,
    private val path: Path,
    header: LogRecordingCodec.Header,
    memoryCapBytes: StateFlow<Long>,
    parentScope: CoroutineScope,
) : BaseLogSession(id, memoryCapBytes, ConnectionState.Connecting, parentScope) {

    private val mutableSource = MutableStateFlow<SessionSource>(
        SessionSource.Recording(
            path = path,
            recordedFrom = header.recordedFrom.ifBlank { path.fileName.toString() },
            recordedLineCount = header.lineCount,
        ),
    )
    override val source: StateFlow<SessionSource> = mutableSource.asStateFlow()

    override val isLive: Boolean = false

    init {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (SessionBundleFiles.looksLikeBundle(path)) {
                    SessionBundleFiles.read(path, ::nextSequence) { submit(it) }
                } else {
                    RecordingFiles.read(path, ::nextSequence) { submit(it) }
                }
            }
                .onSuccess { mutableConnection.value = ConnectionState.Recorded }
                .onFailure { failure ->
                    Log.w(failure) { "Could not finish reading $path" }
                    mutableConnection.value =
                        ConnectionState.Failed(failure.message ?: "Could not read this recording")
                }
        }
    }
}
