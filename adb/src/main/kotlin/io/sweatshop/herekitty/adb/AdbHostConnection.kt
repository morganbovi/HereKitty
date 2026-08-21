package io.sweatshop.herekitty.adb

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.charset.StandardCharsets.UTF_8

/**
 * A single socket to the adb server speaking the host protocol: each request is a four hex digit
 * length followed by the ASCII service name, and each reply starts with `OKAY` or `FAIL`.
 */
internal class AdbHostConnection(endpoint: AdbEndpoint, connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS) : Closeable {
    private val socket = Socket().apply {
        tcpNoDelay = true
        connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMillis)
    }

    private val input: InputStream = BufferedInputStream(socket.getInputStream(), READ_BUFFER_BYTES)
    private val output = socket.getOutputStream()

    val stream: InputStream get() = input

    fun request(service: String) {
        val payload = service.toByteArray(US_ASCII)
        output.write(String.format("%04x", payload.size).toByteArray(US_ASCII))
        output.write(payload)
        output.flush()
        readStatus(service)
    }

    fun readFrame(): String {
        val length = readLength()
        return if (length == 0) "" else String(readExactly(length), UTF_8)
    }

    private fun readStatus(service: String) {
        when (val status = String(readExactly(4), US_ASCII)) {
            "OKAY" -> return
            "FAIL" -> throw AdbProtocolException("adb refused '$service': ${readFailureReason()}")
            else -> throw AdbProtocolException("Unexpected adb reply '$status' to '$service'")
        }
    }

    private fun readFailureReason(): String = runCatching { readFrame() }.getOrDefault("no reason given")

    private fun readLength(): Int {
        val raw = String(readExactly(4), US_ASCII)
        return raw.toIntOrNull(16) ?: throw AdbProtocolException("Malformed adb length prefix '$raw'")
    }

    private fun readExactly(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) throw AdbProtocolException("adb closed the connection after $read of $count bytes")
            read += n
        }
        return buffer
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val READ_BUFFER_BYTES = 1 shl 16
    }
}
