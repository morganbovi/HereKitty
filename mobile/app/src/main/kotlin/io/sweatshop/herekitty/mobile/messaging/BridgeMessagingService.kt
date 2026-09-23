package io.sweatshop.herekitty.mobile.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.sweatshop.herekitty.mobile.MainActivity
import io.sweatshop.herekitty.mobile.domain.devices.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val CHANNEL_ID = "wakeup_requests"

/**
 * Receives the wakeup push a desktop sends via requestDeviceWakeup. Deliberately does not
 * re-enable sharing itself -- it only shows a notification. Tapping it opens the app (which
 * shows the share screen), same reasoning as the OS's own per-network wireless-debug
 * confirmation: a phone coming back online for someone else needs the owner's eyes on it.
 */
class BridgeMessagingService : FirebaseMessagingService() {
    private val deviceRepository: DeviceRepository by inject()

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val model = Build.MODEL
                val device = deviceRepository.ensureRegistered(model)
                deviceRepository.setFcmToken(device.id, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val requesterName = message.data["requesterName"] ?: "Someone"
        val title = message.notification?.title ?: "HereKitty"
        val body = message.notification?.body ?: "$requesterName wants to use this device"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wakeup requests", NotificationManager.IMPORTANCE_HIGH),
            )
        }

        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1, notification)
    }
}
