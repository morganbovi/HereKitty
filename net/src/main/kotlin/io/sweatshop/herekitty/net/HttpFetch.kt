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
/** Carries the status so a caller can tell "nothing published" from "could not reach it". */
class HttpStatusException(val code: Int, url: String, body: String? = null) :
    RuntimeException(if (body.isNullOrBlank()) "$url answered $code" else "$url answered $code: $body")

object HttpFetch {

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            open(url, headers).use { it.inputStream.reader().readText() }
        }

    suspend fun postForm(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val body = params.entries.joinToString("&") {
            "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        post(url, body, "application/x-www-form-urlencoded", headers)
    }

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        post(url, json, "application/json", headers)
    }

    private fun post(url: String, body: String, contentType: String, headers: Map<String, String>): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Content-Type", contentType)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.reader()?.readText().orEmpty()
            if (code !in 200..299) throw HttpStatusException(code, url, text)
            text
        } finally {
            connection.disconnect()
        }
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

    private fun open(url: String, headers: Map<String, String> = emptyMap()): Connection {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        val code = runCatching { connection.responseCode }.getOrElse {
            connection.disconnect()
            throw it
        }
        if (code != HttpURLConnection.HTTP_OK) {
            val text = connection.errorStream?.reader()?.readText()
            connection.disconnect()
            throw HttpStatusException(code, url, text)
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
