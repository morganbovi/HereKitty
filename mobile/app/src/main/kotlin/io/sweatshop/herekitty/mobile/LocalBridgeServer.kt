package io.sweatshop.herekitty.mobile

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

private const val SERVICE_TYPE = "_herekitty._tcp."
private const val HANDSHAKE_PREFIX = "HEREKITTY "

/** A short-lived, authenticated LAN alternative to the public relay. */
class LocalBridgeServer(
    private val context: Context,
    private val relaySessionId: String,
    private val relayToken: String,
    private val adbPort: Int,
) {
    private val executor = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket(0)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "herekitty-$relaySessionId"
            serviceType = SERVICE_TYPE
            port = serverSocket.localPort
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }.also { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, it) }
        executor.execute {
            while (!serverSocket.isClosed) {
                runCatching { serverSocket.accept() }.getOrNull()?.let { client ->
                    executor.execute { bridgeIfAuthorized(client) }
                }
            }
        }
    }

    fun stop() {
        registrationListener?.let { listener -> runCatching { nsdManager.unregisterService(listener) } }
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun bridgeIfAuthorized(client: Socket) {
        client.use { desktop ->
            val hello = readHandshake(desktop)
            if (hello != "$HANDSHAKE_PREFIX$relayToken") return
            Socket("127.0.0.1", adbPort).use { adb ->
                val toAdb = executor.submit { desktop.getInputStream().copyTo(adb.getOutputStream()) }
                val toDesktop = executor.submit { adb.getInputStream().copyTo(desktop.getOutputStream()) }
                runCatching { toAdb.get() }
                runCatching { toDesktop.get() }
            }
        }
    }

    private fun readHandshake(socket: Socket): String? {
        val bytes = ArrayList<Byte>(512)
        while (bytes.size < 512) {
            val next = socket.getInputStream().read()
            if (next < 0) return null
            if (next == '\n'.code) return bytes.toByteArray().decodeToString()
            bytes += next.toByte()
        }
        return null
    }
}
