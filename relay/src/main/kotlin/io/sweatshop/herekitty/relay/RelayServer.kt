package io.sweatshop.herekitty.relay

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * A dumb byte pipe, not a log-aware protocol. Two participants connect to the same session id;
 * whichever bytes one side sends (raw ADB protocol frames, in the real use case) come out the
 * other side unchanged. That's deliberate: the phone bridges its own real adbd TCP/TLS listener
 * through here, so the desktop's existing `:adb` host-protocol code needs zero changes — it just
 * dials `adb connect` at a local port that ends up here.
 *
 * A Cloud Function issues a short-lived signed token for each session. The relay only verifies
 * that signature and session binding; it deliberately has no Firebase or ADB awareness.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 7050
    val relayJwtSecret = requireNotNull(System.getenv("RELAY_JWT_SECRET")) {
        "RELAY_JWT_SECRET must be set"
    }
    val tokenVerifier = RelaySessionTokenVerifier(relayJwtSecret)

    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            // A radio going dark (WiFi off, moving out of range) sends no close frame -- nothing
            // tells either side the link is gone except this timing out on a missed pong. Shrunk
            // from 15s/30s: that meant up to 45s of a session claiming "Streaming" over a link that
            // was actually already dead, which is what surfaced this in the first place.
            pingPeriod = Duration.ofSeconds(3)
            timeout = Duration.ofSeconds(8)
        }
        routing {
            get("/health") { call.respondText("ok") }
            webSocket("/bridge/{sessionId}") {
                val sessionId = call.parameters["sessionId"]
                if (sessionId == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "sessionId required"))
                    return@webSocket
                }
                if (!tokenVerifier.authorizes(call.request.queryParameters["token"], sessionId)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid, expired, or missing session token"))
                    return@webSocket
                }
                println("[$sessionId] participant connected")
                when (val role = Pairing.rendezvous(sessionId, this)) {
                    is Role.WaitForClose -> {
                        println("[$sessionId] first participant, waiting for peer")
                        // PAIRING_TIMEOUT must only bound the *unpaired* wait -- once a peer shows
                        // up, the Piper coroutine reads and writes this exact session object from
                        // outside this coroutine, so this one has to keep waiting on closeReason
                        // unbounded, or it forcibly closes an actively-piping bridge out from under
                        // itself the moment the same 2-minute clock runs out (the original bug
                        // here: every bridge died at almost exactly the 2-minute mark regardless
                        // of whether data was still flowing).
                        val outcome = withTimeoutOrNull(PAIRING_TIMEOUT) {
                            select {
                                closeReason.onAwait { WaitOutcome.CLOSED }
                                role.paired.onAwait { WaitOutcome.PAIRED }
                            }
                        }
                        when (outcome) {
                            null -> {
                                println("[$sessionId] no peer arrived within timeout, closing")
                                // Only removes if we're still the waiting entry — a peer that
                                // arrived in the same instant already removed us via rendezvous.
                                Pairing.abandonIfStillWaiting(sessionId, this)
                                close(CloseReason(CloseReason.Codes.GOING_AWAY, "no peer joined in time"))
                            }
                            WaitOutcome.CLOSED -> {
                                println("[$sessionId] first participant's connection ended")
                            }
                            WaitOutcome.PAIRED -> {
                                println("[$sessionId] peer arrived; waiting for the session to end")
                                closeReason.await()
                                println("[$sessionId] first participant's connection ended")
                            }
                        }
                    }
                    is Role.Piper -> {
                        println("[$sessionId] second participant, piping both directions")
                        coroutineScope {
                            val toPartner = launch { pipe(sessionId, this@webSocket, role.partner) }
                            val toSelf = launch { pipe(sessionId, role.partner, this@webSocket) }
                            // One direction ending (a clean close on either side) should tear down
                            // the other — otherwise the still-open side just blocks forever.
                            toPartner.invokeOnCompletion { toSelf.cancel() }
                            toSelf.invokeOnCompletion { toPartner.cancel() }
                        }
                        runCatching { role.partner.close() }
                        println("[$sessionId] piping ended")
                    }
                }
            }
        }
    }.start(wait = true)
}

private val PAIRING_TIMEOUT = Duration.ofMinutes(2).toMillis()

private suspend fun pipe(sessionId: String, from: DefaultWebSocketServerSession, to: DefaultWebSocketServerSession) {
    try {
        for (frame in from.incoming) {
            if (frame is Frame.Binary) {
                to.send(Frame.Binary(true, frame.readBytes()))
            }
        }
    } catch (e: Exception) {
        println("[$sessionId] pipe ended: ${e.javaClass.simpleName}: ${e.message}")
    }
}

private enum class WaitOutcome { CLOSED, PAIRED }

private sealed interface Role {
    /** [paired] completes the instant a second participant dequeues this entry, so the first
     *  participant's own coroutine can stop treating PAIRING_TIMEOUT as a cap on the whole session. */
    data class WaitForClose(val paired: CompletableDeferred<Unit>) : Role
    data class Piper(val partner: DefaultWebSocketServerSession) : Role
}

/** First arrival waits; second arrival is handed the first's session and does all the piping —
 *  only one side may ever read a given session's `incoming`, so this is deliberately asymmetric. */
private object Pairing {
    private class Entry(val session: DefaultWebSocketServerSession) {
        val paired = CompletableDeferred<Unit>()
    }

    private val waiting = ConcurrentHashMap<String, Entry>()

    fun rendezvous(sessionId: String, session: DefaultWebSocketServerSession): Role {
        val entry = Entry(session)
        val existing = waiting.putIfAbsent(sessionId, entry)
        return if (existing == null) {
            Role.WaitForClose(entry.paired)
        } else {
            waiting.remove(sessionId, existing)
            existing.paired.complete(Unit)
            Role.Piper(existing.session)
        }
    }

    fun abandonIfStillWaiting(sessionId: String, session: DefaultWebSocketServerSession) {
        waiting.entries.removeIf { it.value.session === session }
    }
}
