package io.sweatshop.herekitty.mobile

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Finds the local port adbd's wireless-debug listener is bound to, via mDNS — the same
 * `_adb-tls-connect._tcp` advertisement `adb`/Android Studio use to auto-discover a device on a
 * LAN. This is the only discovery path available to a normal app: reading /proc/net/tcp6 (how
 * this port was first confirmed, from a host shell) needs adb access the app itself doesn't have.
 */
object AdbServiceDiscovery {
    private const val SERVICE_TYPE = "_adb-tls-connect._tcp"

    sealed interface Result {
        data class Found(val host: String, val port: Int, val serviceName: String) : Result
        data class Error(val message: String) : Result
    }

    /** Call [stop] on the returned listener once done; a live NsdManager.DiscoveryListener leaks
     *  the registration otherwise. */
    fun start(context: Context, onResult: (Result) -> Unit): NsdManager.DiscoveryListener {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onResult(Result.Error("resolve failed for ${serviceInfo.serviceName}: code $errorCode"))
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                if (host == null) {
                    onResult(Result.Error("resolved ${serviceInfo.serviceName} but host was null"))
                    return
                }
                onResult(Result.Found(host, serviceInfo.port, serviceInfo.serviceName))
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION") // executor-based overload requires API 34; minSdk here is 30
                nsdManager.resolveService(serviceInfo, resolveListener)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onResult(Result.Error("start discovery failed: code $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onResult(Result.Error("stop discovery failed: code $errorCode"))
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        return discoveryListener
    }

    fun stop(context: Context, listener: NsdManager.DiscoveryListener) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }
}
