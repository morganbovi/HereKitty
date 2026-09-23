package io.sweatshop.herekitty.relay

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Stands in for the real HereKitty desktop-side bridge: accepts one local TCP connection (from
 * `adb connect 127.0.0.1:<localPort>`) and pipes it through the relay to whichever phone is on
 * the other end of the same session id. Once this is proven end to end, this logic becomes a
 * small `:adb`-sibling module in HereKitty itself, not a standalone test client.
 *
 * Usage: runDesktopClient -Pargs="<relayHost> <relayPort> <sessionId> <localPort> [authToken]"
 */
fun main(args: Array<String>) {
    val relayHost = args.getOrNull(0) ?: "127.0.0.1"
    val relayPort = args.getOrNull(1)?.toIntOrNull() ?: 7050
    val sessionId = args.getOrNull(2) ?: "test-session"
    val localPort = args.getOrNull(3)?.toIntOrNull() ?: 6520
    val authToken = args.getOrNull(4)

    val serverSocket = ServerSocket()
    serverSocket.reuseAddress = true
    serverSocket.bind(InetSocketAddress("127.0.0.1", localPort))
    println("Listening on 127.0.0.1:$localPort — point `adb connect` at this once a client connects here.")

    val client = HttpClient(CIO) { install(WebSockets) }

    runBlocking {
        while (true) {
            println("Waiting for a local TCP connection (e.g. `adb connect 127.0.0.1:$localPort`)...")
            // accept() is a blocking JVM call; without Dispatchers.IO it hogs runBlocking's
            // single-threaded event loop and starves every other coroutine on it — including the
            // relay-dial coroutine launched below for the *previous* connection, which would then
            // never get scheduled at all (no error, just silent).
            val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
            println("Local connection accepted from ${socket.remoteSocketAddress}. Dialing relay ws://$relayHost:$relayPort/bridge/$sessionId ...")
            launch {
                try {
                    client.webSocket(
                        host = relayHost,
                        port = relayPort,
                        path = "/bridge/$sessionId",
                        request = { authToken?.let { parameter("token", it) } },
                    ) {
                        println("Relay connected — bridging.")
                        val toRelay = launch {
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = withContext(Dispatchers.IO) { socket.getInputStream().read(buffer) }
                                if (n < 0) break
                                send(Frame.Binary(true, buffer.copyOf(n)))
                            }
                        }
                        val fromRelay = launch {
                            for (frame in incoming) {
                                if (frame is Frame.Binary) {
                                    withContext(Dispatchers.IO) {
                                        socket.getOutputStream().write(frame.readBytes())
                                        socket.getOutputStream().flush()
                                    }
                                }
                            }
                        }
                        toRelay.invokeOnCompletion { fromRelay.cancel() }
                        fromRelay.invokeOnCompletion { toRelay.cancel() }
                        joinAll(toRelay, fromRelay)
                    }
                } catch (e: Exception) {
                    println("Bridge ended: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    runCatching { socket.close() }
                }
            }
        }
    }
}
