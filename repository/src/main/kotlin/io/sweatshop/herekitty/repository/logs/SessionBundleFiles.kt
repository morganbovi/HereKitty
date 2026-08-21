package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A session and the view it was being read through, in one file you can hand to someone.
 *
 * It is an ordinary zip, so it can be unzipped and inspected with anything. The log inside is stored
 * uncompressed and left to the zip's own deflate: gzipping it first would compress already-compressed
 * bytes for nothing. No manifest — the two entry names are the format.
 */
internal object SessionBundleFiles {

    const val EXTENSION = "hkbundle"

    private const val VIEW_ENTRY = "view.hkview"
    private const val LOG_ENTRY = "session.hklog"
    private const val BUFFER_BYTES = 1 shl 16

    fun looksLikeBundle(path: Path): Boolean =
        path.fileName?.toString()?.endsWith(".$EXTENSION", ignoreCase = true) == true

    fun write(
        path: Path,
        viewJson: String,
        recordedFrom: String,
        serial: String,
        exportedAtMillis: Long,
        lineCount: Long,
        lines: (write: (LogLine) -> Unit) -> Unit,
    ): Long {
        path.parent?.let { Files.createDirectories(it) }

        return ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)

            zip.putNextEntry(ZipEntry(VIEW_ENTRY))
            zip.write(viewJson.toByteArray(UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(LOG_ENTRY))
            val written = LogRecordingCodec.write(
                destination = zip,
                recordedFrom = recordedFrom,
                serial = serial,
                exportedAtMillis = exportedAtMillis,
                lineCount = lineCount,
                lines = lines,
            )
            zip.closeEntry()
            written
        }
    }

    fun readViewJson(path: Path): String = withEntry(path, VIEW_ENTRY) { it.readBytes().toString(UTF_8) }

    fun readHeader(path: Path): LogRecordingCodec.Header =
        withEntry(path, LOG_ENTRY) { LogRecordingCodec.readHeader(it) }

    suspend fun read(
        path: Path,
        nextSeq: () -> Long,
        onLine: suspend (LogLine) -> Unit,
    ): LogRecordingCodec.Header = ZipFile(path.toFile()).use { zip ->
        val entry = zip.getEntry(LOG_ENTRY) ?: error("${path.fileName} has no $LOG_ENTRY inside it")
        zip.getInputStream(entry).buffered(BUFFER_BYTES).use { LogRecordingCodec.read(it, nextSeq, onLine) }
    }

    private fun <T> withEntry(path: Path, name: String, block: (InputStream) -> T): T =
        ZipFile(path.toFile()).use { zip ->
            val entry = zip.getEntry(name) ?: error("${path.fileName} has no $name inside it")
            zip.getInputStream(entry).buffered(BUFFER_BYTES).use(block)
        }
}
