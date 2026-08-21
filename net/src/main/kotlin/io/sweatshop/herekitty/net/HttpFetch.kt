package io.sweatshop.herekitty.net

import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The little HTTP this app does: fetch a document, or fetch a file while reporting progress.
 *
 * `HttpURLConnection` rather than `java.net.http.HttpClient` or a client library, because
 * `java.net.http` is not in the runtime image jpackage builds — anything using it works under
 * `./gradlew run` and throws `NoClassDefFoundError` in the packaged app. `HttpURLConnection` lives in
 * `java.base`, which is always there.
 */
object HttpFetch {

    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        open(url).use { it.inputStream.reader().readText() }
    }

    /** [onProgress] gets null while the size is unknown, which is any response without a length. */
    suspend fun toFile(url: String, target: Path, onProgress: (Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            open(url).use { connection ->
                val expected = connection.contentLengthLong.takeIf { it > 0 }
                onProgress(null)

                connection.inputStream.use { source ->
                    Files.newOutputStream(target).use { sink ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var received = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            received += read
                            onProgress(expected?.let { received.toFloat() / it })
                        }
                    }
                }
            }
        }

    private fun open(url: String): Connection {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }

        val code = runCatching { connection.responseCode }.getOrElse {
            connection.disconnect()
            throw it
        }
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            error("$url answered $code")
        }
        return Connection(connection)
    }

    /** Closeable purely so the connection is released on every path out. */
    private class Connection(val connection: HttpURLConnection) : AutoCloseable {
        val inputStream get() = connection.inputStream
        val contentLengthLong get() = connection.contentLengthLong
        override fun close() = connection.disconnect()
    }

    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 30_000
    private const val BUFFER_BYTES = 1 shl 16
}
