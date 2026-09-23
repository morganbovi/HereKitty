package io.sweatshop.herekitty.mobile

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Phone-side half of the relay bridge: dials the relay's WebSocket, then pipes bytes between it
 * and a local TCP connection to this device's own adbd wireless-debug listener (the port NSD
 * discovery found). A real `adb` client never talks to this directly — it talks to the desktop
 * client's local port, which turns back into a plain TCP connection on the other end of the
 * relay, identical in shape to this one.
 */
class PhoneBridgeClient(
    private val client: OkHttpClient,
    private val relayWsUrl: String,
    private val adbHost: String,
    private val adbPort: Int,
) {
    private val ioExecutor = Executors.newCachedThreadPool()

    fun start(onStatus: (WebSocket, String) -> Unit): WebSocket {
        val request = Request.Builder().url(relayWsUrl).build()
        var localSocket: Socket? = null

        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus(webSocket, "relay connected, dialing local adbd $adbHost:$adbPort")
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(adbHost, adbPort), 5_000)
                    localSocket = socket
                    onStatus(webSocket, "bridging")
                    ioExecutor.submit {
                        try {
                            val buffer = ByteArray(8192)
                            val input = socket.getInputStream()
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                webSocket.send(buffer.copyOf(n).toByteString())
                            }
                        } catch (e: Exception) {
                            onStatus(webSocket, "local->relay ended: ${e.javaClass.simpleName}: ${e.message}")
                        } finally {
                            webSocket.close(1000, "local socket closed")
                        }
                    }
                } catch (e: Exception) {
                    onStatus(webSocket, "failed to reach local adbd: ${e.javaClass.simpleName}: ${e.message}")
                    webSocket.close(1011, "local dial failed")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    localSocket?.getOutputStream()?.let {
                        it.write(bytes.toByteArray())
                        it.flush()
                    }
                } catch (e: Exception) {
                    onStatus(webSocket, "relay->local write failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus(webSocket, "relay closed: $code $reason")
                runCatching { localSocket?.close() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus(webSocket, "relay failure: ${t.javaClass.simpleName}: ${t.message}")
                runCatching { localSocket?.close() }
            }
        })
    }
}
