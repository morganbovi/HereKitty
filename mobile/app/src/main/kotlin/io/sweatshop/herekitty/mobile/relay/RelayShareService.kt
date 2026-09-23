package io.sweatshop.herekitty.mobile.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.sweatshop.herekitty.mobile.AdbServiceDiscovery
import io.sweatshop.herekitty.mobile.BuildConfig
import io.sweatshop.herekitty.mobile.LocalBridgeServer
import io.sweatshop.herekitty.mobile.PhoneBridgeClient
import io.sweatshop.herekitty.mobile.domain.devices.DeviceRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.koin.android.ext.android.inject

private const val NOTIFICATION_CHANNEL_ID = "relay_share"
private const val NOTIFICATION_ID = 1
private const val EXTRA_DEVICE_ID = "deviceId"
private const val EXTRA_RETRY = "retry"
private const val DISCOVERY_ATTEMPTS = 3
private const val DISCOVERY_RETRY_DELAY_MILLIS = 4_000L
private const val RELAY_RETRY_DELAY_MILLIS = 4_000L
private const val MAX_RELAY_RETRIES = 3

/**
 * Keeps "share my phone" alive once the app is backgrounded. The bridge used to run inside a
 * `LaunchedEffect` in `SharePresenter`, tied to the Activity's own coroutine scope -- Android
 * suspends that the moment the app leaves the foreground (screen lock, switching apps), which
 * silently killed the bridge exactly when a desktop was mid-session with it. A foreground service
 * with a persistent notification is the only mechanism Android offers for "must keep running,
 * no Activity required."
 */
class RelayShareService : Service() {
    private val deviceRepository: DeviceRepository by inject()
    private val status: RelayShareStatus by inject()
    private val okHttpClient by lazy { OkHttpClient() }
    private val scope = CoroutineScope(SupervisorJob())
    private var bridgeJob: Job? = null
    private var networkRecoveryJob: Job? = null
    private var relayRetryAttempt = 0
    private var activeClaim: io.sweatshop.herekitty.mobile.domain.devices.SessionClaim? = null
    private var activeDeviceId: String? = null
    private var activeWebSocket: WebSocket? = null
    private var localBridgeServer: LocalBridgeServer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)
        if (intent?.getBooleanExtra(EXTRA_RETRY, false) == true) {
            activeClaim?.let { claim ->
                networkRecoveryJob?.cancel()
                relayRetryAttempt = 0
                networkRecoveryJob = scope.launch { bridgeClaim(claim, networkRecovery = true) }
            }
        }
        // Restarted by the OS after being killed (START_STICKY) redelivers a null intent -- only
        // the original start actually carries the device id, and a job is already running anyway.
        if (deviceId != null && bridgeJob == null) {
            bridgeJob = scope.launch { watchAndBridge(deviceId) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bridgeJob?.cancel()
        activeWebSocket?.close(1000, "sharing stopped")
        localBridgeServer?.stop()
        scope.cancel()
        status.update("not shared")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun watchAndBridge(deviceId: String) {
        activeDeviceId = deviceId
        status.update("shared, waiting for a connection")
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        if (connectivityManager.activeNetwork == null) {
            deviceRepository.setPresence(deviceId, online = false)
            status.update("connection lost — waiting for a network")
        } else {
            // A previous outage may have left RTDB presence false. Registering the callback does
            // not guarantee an immediate onAvailable callback for the already-active network.
            deviceRepository.setPresence(deviceId, online = true)
        }
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                localBridgeServer?.stop()
                localBridgeServer = null
                networkRecoveryJob?.cancel()
                scope.launch { deviceRepository.setPresence(deviceId, online = false) }
                status.update("connection lost — waiting for a network")
            }

            override fun onAvailable(network: Network) {
                networkRecoveryJob?.cancel()
                networkRecoveryJob = scope.launch {
                    deviceRepository.setPresence(deviceId, online = true)
                    activeClaim?.let {
                        relayRetryAttempt = 0
                        bridgeClaim(it, networkRecovery = true)
                    }
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        try {
            deviceRepository.observeSessionClaim(deviceId).collectLatest { claim ->
                activeClaim = claim
                if (claim == null) {
                    status.update("shared, waiting for a connection")
                } else {
                    bridgeClaim(claim, networkRecovery = false)
                }
            }
        } finally {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    private suspend fun bridgeClaim(
        claim: io.sweatshop.herekitty.mobile.domain.devices.SessionClaim,
        networkRecovery: Boolean,
    ) {
        status.update(
            if (networkRecovery) "network changed -- restoring the bridge..."
            else "${claim.connectedByDisplayName} is connecting -- finding local adbd...",
        )
        val port = discoverAdbPortWithRetries()
        if (port == null) {
            status.update("wireless debugging needs confirmation for this network")
            showWirelessDebuggingPrompt()
            return
        }
        localBridgeServer?.stop()
        localBridgeServer = LocalBridgeServer(this, claim.relaySessionId, claim.relayToken, port).also { it.start() }
        val previousWebSocket = activeWebSocket
        activeWebSocket = null
        previousWebSocket?.close(1000, "reconnecting")
        activeWebSocket = PhoneBridgeClient(
            client = okHttpClient,
            relayWsUrl = relayUrlFor(claim.relaySessionId, claim.relayToken),
            adbHost = "127.0.0.1",
            adbPort = port,
        ).start { webSocket, newStatus ->
            // A replacement bridge closes its predecessor deliberately. Its late callbacks must
            // never mark the fresh bridge offline or schedule another retry.
            if (webSocket !== activeWebSocket) return@start
            if (newStatus == "bridging") relayRetryAttempt = 0
            status.update(
                when {
                    newStatus.startsWith("relay failure") || newStatus.startsWith("relay closed") ->
                        "connection lost — reconnecting when your network returns"
                    else -> newStatus
                },
            )
            if (newStatus.startsWith("relay failure") || newStatus.startsWith("relay closed")) {
                activeDeviceId?.let { deviceId ->
                    scope.launch { deviceRepository.setPresence(deviceId, online = false) }
                }
                scheduleRelayRetry()
            }
        }
    }

    /** A closed relay socket is not always accompanied by an Android network callback. */
    private fun scheduleRelayRetry() {
        val claim = activeClaim ?: return
        if (networkRecoveryJob?.isActive == true) return
        if (relayRetryAttempt >= MAX_RELAY_RETRIES) {
            status.update("connection needs attention — retry when you are ready")
            return
        }

        relayRetryAttempt += 1
        networkRecoveryJob = scope.launch {
            status.update("connection lost — retrying ($relayRetryAttempt/$MAX_RELAY_RETRIES)")
            delay(RELAY_RETRY_DELAY_MILLIS)
            bridgeClaim(claim, networkRecovery = true)
        }
    }

    /**
     * Discovery alone assumed wireless debugging was already on and just gave up if it wasn't --
     * fine for the manual spike buttons, wrong for something meant to work unattended. Wireless
     * debugging turning itself off is a real thing that happens (observed: toggling WiFi off and
     * back on left `adb_wifi_enabled` off), and with nobody watching the phone to notice or fix
     * it, the app has to recover on its own using the same WRITE_SECURE_SETTINGS grant the manual
     * toggle buttons already rely on.
     */
    private suspend fun discoverAdbPortWithRetries(): Int? {
        repeat(DISCOVERY_ATTEMPTS) { attempt ->
            ensureWirelessDebuggingEnabled()
            val port = discoverAdbPort()
            if (port != null) return port
            if (attempt < DISCOVERY_ATTEMPTS - 1) delay(DISCOVERY_RETRY_DELAY_MILLIS)
        }
        return null
    }

    private fun ensureWirelessDebuggingEnabled() {
        val current = runCatching {
            Settings.Global.getString(contentResolver, "adb_wifi_enabled")
        }.getOrNull()
        if (current != "1") {
            runCatching { Settings.Global.putString(contentResolver, "adb_wifi_enabled", "1") }
        }
    }

    private suspend fun discoverAdbPort(): Int? =
        withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val listener = AdbServiceDiscovery.start(this@RelayShareService) { result ->
                    if (result is AdbServiceDiscovery.Result.Found && cont.isActive) {
                        cont.resume(result.port)
                    }
                }
                cont.invokeOnCancellation { AdbServiceDiscovery.stop(this@RelayShareService, listener) }
            }
        }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Phone sharing",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("HereKitty")
            .setContentText("Sharing this phone for remote log viewing")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()
    }

    private fun showWirelessDebuggingPrompt() {
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("HereKitty needs wireless debugging")
                .setContentText("Tap to confirm it for this Wi-Fi network")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build(),
        )
    }

    companion object {
        fun start(context: Context, deviceId: String) {
            val intent = Intent(context, RelayShareService::class.java).putExtra(EXTRA_DEVICE_ID, deviceId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RelayShareService::class.java))
        }

        fun retry(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RelayShareService::class.java).putExtra(EXTRA_RETRY, true),
            )
        }
    }
}

private fun relayUrlFor(relaySessionId: String, relayToken: String): String =
    "${BuildConfig.RELAY_URL.removeSuffix("/")}/bridge/$relaySessionId?token=$relayToken"
