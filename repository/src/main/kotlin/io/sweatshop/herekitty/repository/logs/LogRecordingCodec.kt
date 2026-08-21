package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads and writes a whole recording as JSON lines: one header, then one object per log line.
 *
 * Line objects use short keys because a busy device produces millions of them and the key repeats in
 * every one. Streaming both ways keeps a multi-million-line session off the heap during transfer.
 *
 * Deliberately stream-based rather than file-based: the same bytes go into a standalone gzip file and
 * into an entry of a session bundle, and the two must not drift apart.
 */
internal object LogRecordingCodec {

    const val FORMAT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Header(
        val herekitty: Int = FORMAT_VERSION,
        val recordedFrom: String = "",
        val serial: String = "",
        val exportedAtMillis: Long = 0L,
        val lineCount: Long = 0L,
    )

    @Serializable
    private data class Record(
        val t: Long,
        val p: Int,
        val i: Int,
        val l: String,
        val g: String,
        val m: String,
    )

    fun write(
        destination: OutputStream,
        recordedFrom: String,
        serial: String,
        exportedAtMillis: Long,
        lineCount: Long,
        lines: (write: (LogLine) -> Unit) -> Unit,
    ): Long {
        var written = 0L

        destination.bufferedWriter(UTF_8).let { out ->
            out.write(json.encodeToString(Header(FORMAT_VERSION, recordedFrom, serial, exportedAtMillis, lineCount)))
            out.newLine()

            lines { line ->
                out.write(
                    json.encodeToString(
                        Record(line.timestampMillis, line.pid, line.tid, line.level.letter.toString(), line.tag, line.message),
                    ),
                )
                out.newLine()
                written++
            }

            // Flushed rather than closed: the caller owns the stream, which may be one entry of a zip.
            out.flush()
        }
        return written
    }

    fun readHeader(source: InputStream): Header {
        val first = source.bufferedReader(UTF_8).readLine() ?: error("This recording is empty")
        return json.decodeFromString<Header>(first).also {
            require(it.herekitty == FORMAT_VERSION) {
                "This recording was written by a different HereKitty format (version ${it.herekitty})"
            }
        }
    }

    /** Invokes [onLine] for each recorded line, in order, reusing one interned tag per distinct tag. */
    suspend fun read(
        source: InputStream,
        nextSeq: () -> Long,
        onLine: suspend (LogLine) -> Unit,
    ): Header {
        val input = source.bufferedReader(UTF_8)
        val first = input.readLine() ?: error("This recording is empty")
        val header = json.decodeFromString<Header>(first)
        require(header.herekitty == FORMAT_VERSION) {
            "This recording was written by a different HereKitty format (version ${header.herekitty})"
        }

        val tagPool = HashMap<String, String>(512)
        while (true) {
            val next = input.readLine() ?: break
            if (next.isBlank()) continue
            val record = json.decodeFromString<Record>(next)
            onLine(
                LogLine(
                    seq = nextSeq(),
                    timestampMillis = record.t,
                    pid = record.p,
                    tid = record.i,
                    level = LogLevel.fromLetter(record.l.firstOrNull() ?: 'V'),
                    tag = tagPool.getOrPut(record.g) { record.g },
                    message = record.m,
                ),
            )
        }
        return header
    }
}
