package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.adb.LogcatParser
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.SessionEvent
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

    // Instance-level rather than local to launchCapture()'s coroutine: mutableConnection.value gets
    // overwritten to Connecting at the top of every retry, so it cannot itself say whether the stream
    // that is about to start is a first connect or a recovery — and a manual Reconnect click cancels
    // and restarts that coroutine entirely, which a coroutine-local flag would not survive either.
    @Volatile private var wasDisconnected = false

    /** Same reasoning as [wasDisconnected]: a deliberate pause, not a drop, so it gets its own flag. */
    @Volatile private var wasPaused = false

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
            recordEvent(SessionEvent.Kind.Paused)
            wasPaused = true
        } else {
            // Not a fresh open: dropping the -T limit here would replay the device's whole retained
            // backlog into a pane that may have just been cleared, looking exactly like old lines
            // coming back from the dead.
            launchCapture(RESUME_TAIL_LINES)
        }
    }

    override fun reconnect() {
        isPaused = false
        launchCapture(RESUME_TAIL_LINES)
    }

    override fun dispose() {
        captureJob?.cancel()
        super.dispose()
    }

    /** [initialTailLines] is only ever `null` for the very first connect, to show what's already there. */
    private fun launchCapture(initialTailLines: Int? = null) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            var tailLines = initialTailLines

            while (isActive) {
                mutableConnection.value = ConnectionState.Connecting
                try {
                    var streaming = false
                    hostClient.streamLogcat(serial, tailLines).collect { rawLine ->
                        if (!streaming) {
                            streaming = true
                            when {
                                wasDisconnected -> {
                                    recordEvent(SessionEvent.Kind.Reconnected)
                                    wasDisconnected = false
                                }
                                wasPaused -> {
                                    recordEvent(SessionEvent.Kind.Resumed)
                                    wasPaused = false
                                }
                            }
                            mutableConnection.value = ConnectionState.Streaming
                        }
                        parser.accept(rawLine)?.let { submit(it) }
                    }
                    parser.flush()?.let { submit(it) }
                    markDisconnectedIfWasStreaming()
                    mutableConnection.value = ConnectionState.Waiting("Device disconnected")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A cancelled read closes the socket and throws SocketException rather than
                    // CancellationException, so closing the session must not look like a dropout.
                    currentCoroutineContext().ensureActive()
                    Log.w(e) { "logcat stream for $serial ended" }
                    markDisconnectedIfWasStreaming()
                    mutableConnection.value = ConnectionState.Waiting(e.message ?: "Waiting for $serial")
                }

                tailLines = RESUME_TAIL_LINES
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun markDisconnectedIfWasStreaming() {
        if (mutableConnection.value != ConnectionState.Streaming) return
        recordEvent(SessionEvent.Kind.Disconnected)
        wasDisconnected = true
    }

    private companion object {
        const val RECONNECT_DELAY_MILLIS = 1_000L
        const val RESUME_TAIL_LINES = 1
    }
}
