package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.adb.LogcatParser
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Captures one attached device, reconnecting on its own when the device comes and goes. */
internal class DeviceLogSession(
    id: SessionId,
    initialDevice: AdbDevice,
    private val hostClient: AdbHostClient,
    memoryCapBytes: StateFlow<Long>,
    parentScope: CoroutineScope,
) : BaseLogSession(id, memoryCapBytes, ConnectionState.Connecting, parentScope) {

    val serial: String = initialDevice.serial

    private val mutableSource = MutableStateFlow<SessionSource>(SessionSource.Device(initialDevice))
    override val source: StateFlow<SessionSource> = mutableSource.asStateFlow()

    override val isLive: Boolean = true

    private val parser = LogcatParser(::nextSequence)
    private var captureJob: Job? = null
    private var isPaused = false

    init {
        launchCapture()
    }

    fun updateDevice(device: AdbDevice) {
        mutableSource.value = SessionSource.Device(device)
    }

    override fun setPaused(paused: Boolean) {
        if (isPaused == paused) return
        isPaused = paused
        if (paused) {
            captureJob?.cancel()
            mutableConnection.value = ConnectionState.Paused
        } else {
            launchCapture()
        }
    }

    override fun reconnect() {
        isPaused = false
        launchCapture()
    }

    override fun dispose() {
        captureJob?.cancel()
        super.dispose()
    }

    private fun launchCapture() {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            var tailLines: Int? = null
            var hasStreamedBefore = false

            while (isActive) {
                mutableConnection.value = ConnectionState.Connecting
                try {
                    var streaming = false
                    hostClient.streamLogcat(serial, tailLines).collect { rawLine ->
                        if (!streaming) {
                            streaming = true
                            mutableConnection.value = ConnectionState.Streaming
                            if (hasStreamedBefore) submit(marker(RESUMED_MESSAGE))
                        }
                        parser.accept(rawLine)?.let { submit(it) }
                    }
                    parser.flush()?.let { submit(it) }
                    mutableConnection.value = ConnectionState.Waiting("Device disconnected")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A cancelled read closes the socket and throws SocketException rather than
                    // CancellationException, so closing the session must not look like a dropout.
                    currentCoroutineContext().ensureActive()
                    Log.w(e) { "logcat stream for $serial ended" }
                    mutableConnection.value = ConnectionState.Waiting(e.message ?: "Waiting for $serial")
                }

                hasStreamedBefore = true
                tailLines = RESUME_TAIL_LINES
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun marker(message: String) = LogLine(
        seq = nextSequence(),
        timestampMillis = System.currentTimeMillis(),
        pid = 0,
        tid = 0,
        level = LogLevel.INFO,
        tag = MARKER_TAG,
        message = message,
    )

    private companion object {
        const val MARKER_TAG = "HereKitty"
        const val RESUMED_MESSAGE =
            "Reconnected. Lines the device emitted while it was unplugged could not be recovered."
        const val RECONNECT_DELAY_MILLIS = 1_000L
        const val RESUME_TAIL_LINES = 1
    }
}
