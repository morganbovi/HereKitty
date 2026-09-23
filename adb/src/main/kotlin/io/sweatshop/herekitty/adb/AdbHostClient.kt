package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.base.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Talks the adb host protocol directly, so device connects and disconnects arrive as events from
 * `host:track-devices` rather than being discovered by polling. The adb binary is only used to
 * start the server when it is not already listening.
 */
@Single
class AdbHostClient(private val endpoint: AdbEndpoint, private val binaryLocator: AdbBinaryLocator) {

    @Volatile private var supportsLongDeviceFormat = true

    suspend fun serverVersion(): Int = withConnection { connection ->
        connection.request(SERVICE_VERSION)
        connection.readFrame().trim().toIntOrNull(16) ?: 0
    }

    suspend fun connect(hostPort: String): Result<String> = runCatching {
        withConnection { connection ->
            connection.request("$SERVICE_CONNECT$hostPort")
            val message = connection.readFrame()
            if (CONNECT_FAILURE_PREFIXES.any { message.startsWith(it, ignoreCase = true) }) {
                throw AdbProtocolException(message)
            }
            message
        }
    }

    suspend fun disconnect(hostPort: String): Result<String> = runCatching {
        withConnection { connection ->
            connection.request("$SERVICE_DISCONNECT$hostPort")
            connection.readFrame()
        }
    }

    suspend fun ensureServerRunning(): Int {
        runCatching { serverVersion() }.onSuccess { return it }

        val adb = binaryLocator.locate()
            ?: throw AdbProtocolException(
                "Could not find the adb binary. Set ANDROID_HOME, or point HEREKITTY_ADB at it.",
            )

        Log.i { "adb server not responding on ${endpoint.host}:${endpoint.port}; starting it via ${adb.absolutePath}" }
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(adb.absolutePath, "start-server").redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            process.waitFor(START_SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return serverVersion()
    }

    /** Emits the full device list every time adb reports a change. */
    fun trackDevices(): Flow<DeviceTrackEvent> = channelFlow {
        while (isActive) {
            try {
                val version = ensureServerRunning()
                withConnection { connection ->
                    connection.request(if (supportsLongDeviceFormat) SERVICE_TRACK_LONG else SERVICE_TRACK)
                    while (isActive) {
                        send(DeviceTrackEvent.Devices(parseDeviceList(connection.readFrame()), version))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbProtocolException) {
                if (supportsLongDeviceFormat) {
                    Log.i { "adb does not support $SERVICE_TRACK_LONG; falling back to $SERVICE_TRACK" }
                    supportsLongDeviceFormat = false
                    continue
                }
                dropAndRetry(e)
            } catch (e: Exception) {
                // Closing the socket to unblock a read surfaces as SocketException, not
                // CancellationException, so shutdown must not be reported as adb going away.
                currentCoroutineContext().ensureActive()
                dropAndRetry(e)
            }
        }
    }

    /**
     * Streams raw logcat output for one device. [resumeFrom] is passed verbatim as logcat's own
     * `-T` argument -- either a line count (`"1"`, the full-history dump is not replayed a second
     * time) or, for resuming after a disconnect without losing what the device logged in the gap,
     * an epoch `seconds.millis` timestamp (`-v epoch`'s own format, which `-T` also accepts) to
     * pick logcat's ring buffer up from exactly where the session left off.
     */
    fun streamLogcat(serial: String, resumeFrom: String? = null): Flow<String> = channelFlow {
        withConnection { connection ->
            connection.request(serviceTransport(serial))
            connection.request(serviceLogcat(resumeFrom))

            BufferedReader(InputStreamReader(connection.stream, UTF_8), READER_BUFFER_BYTES).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    send(line)
                }
            }
        }
    }

    private suspend fun ProducerScope<DeviceTrackEvent>.dropAndRetry(cause: Throwable) {
        Log.w(cause) { "adb device tracking dropped; retrying in ${RETRY_DELAY_MILLIS}ms" }
        send(DeviceTrackEvent.Unavailable(cause.message ?: "adb is not reachable"))
        delay(RETRY_DELAY_MILLIS)
    }

    /**
     * Runs [block] against a fresh adb socket on the IO dispatcher.
     *
     * Socket reads do not respond to coroutine cancellation, so a sibling coroutine parks on
     * [awaitCancellation] and closes the socket the moment cancellation arrives; that is what
     * unblocks the reader. A completion handler would not do: a coroutine blocked in IO is only
     * *cancelling*, never *completed*, so the handler would wait on the very read it must break.
     */
    private suspend fun <T> withConnection(block: suspend (AdbHostConnection) -> T): T =
        withContext(Dispatchers.IO) {
            val connection = AdbHostConnection(endpoint)
            val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.close()
                }
            }
            try {
                block(connection)
            } finally {
                closer.cancel()
                connection.close()
            }
        }

    private companion object {
        const val SERVICE_VERSION = "host:version"
        const val SERVICE_TRACK = "host:track-devices"
        const val SERVICE_TRACK_LONG = "host:track-devices-l"
        const val SERVICE_CONNECT = "host:connect:"
        const val SERVICE_DISCONNECT = "host:disconnect:"
        val CONNECT_FAILURE_PREFIXES = listOf("unable to connect", "failed to connect", "no route to host")
        const val RETRY_DELAY_MILLIS = 1_500L
        const val START_SERVER_TIMEOUT_SECONDS = 10L
        const val READER_BUFFER_BYTES = 1 shl 16

        fun serviceTransport(serial: String) = "host:transport:$serial"

        fun serviceLogcat(resumeFrom: String?): String = buildString {
            append("exec:logcat -v long,epoch")
            if (resumeFrom != null) append(" -T ").append(resumeFrom)
        }
    }
}
