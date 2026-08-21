package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A recording on its own: the JSON lines of [LogRecordingCodec], gzipped. */
internal object RecordingFiles {

    const val EXTENSION = "hklog.gz"

    fun write(
        path: Path,
        recordedFrom: String,
        serial: String,
        exportedAtMillis: Long,
        lineCount: Long,
        lines: (write: (LogLine) -> Unit) -> Unit,
    ): Long {
        path.parent?.let { Files.createDirectories(it) }
        return GZIPOutputStream(Files.newOutputStream(path), BUFFER_BYTES).use { out ->
            LogRecordingCodec.write(out, recordedFrom, serial, exportedAtMillis, lineCount, lines)
        }
    }

    fun readHeader(path: Path): LogRecordingCodec.Header =
        GZIPInputStream(Files.newInputStream(path), BUFFER_BYTES).use { LogRecordingCodec.readHeader(it) }

    suspend fun read(
        path: Path,
        nextSeq: () -> Long,
        onLine: suspend (LogLine) -> Unit,
    ): LogRecordingCodec.Header =
        GZIPInputStream(Files.newInputStream(path), BUFFER_BYTES).use {
            LogRecordingCodec.read(it, nextSeq, onLine)
        }

    private const val BUFFER_BYTES = 1 shl 16
}
