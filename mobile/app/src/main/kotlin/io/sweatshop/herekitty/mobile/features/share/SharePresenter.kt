package io.sweatshop.herekitty.mobile.features.share

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import io.sweatshop.herekitty.mobile.domain.devices.DeviceRepository
import io.sweatshop.herekitty.mobile.relay.RelayShareService
import io.sweatshop.herekitty.mobile.relay.RelayShareStatus
import io.sweatshop.herekitty.mobile.ui.presenter.EventHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Factory

/**
 * Owns "share my phone": registering this install's device doc, the isShared toggle + naming,
 * and -- once shared -- starting `RelayShareService` to listen for a desktop's session claim and
 * bridge to the relay. The actual bridging does not run here: a `LaunchedEffect` lives and dies
 * with the Activity, and Android suspends that the moment this screen leaves the foreground, which
 * silently killed an in-progress bridge the instant the phone's screen locked. The foreground
 * service is what survives that; this presenter only starts/stops it and reflects its status.
 */
@Factory
class SharePresenter(
    private val deviceRepository: DeviceRepository,
    private val shareStatus: RelayShareStatus,
) {
    @Composable
    fun present(): ShareUiModel {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val bridgeStatus by shareStatus.status.collectAsState()

        var device by remember { mutableStateOf<io.sweatshop.herekitty.mobile.domain.devices.Device?>(null) }
        var nameDraft by remember { mutableStateOf("") }
        var editingName by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val registered = deviceRepository.ensureRegistered(Build.MODEL)
                device = registered
                nameDraft = registered.name
                // onNewToken only fires when the token *changes* -- register whatever the
                // current one is too, so a fresh install/reinstall doesn't go silent.
                val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                if (token != null) deviceRepository.setFcmToken(registered.id, token)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Failed to register this device"
            }
        }

        LaunchedEffect(device?.id, device?.isShared) {
            val current = device ?: return@LaunchedEffect
            if (current.isShared) {
                RelayShareService.start(context, current.id)
            } else {
                RelayShareService.stop(context)
            }
        }

        return ShareUiModel(
            device = device,
            nameDraft = nameDraft,
            editingName = editingName,
            bridgeStatus = bridgeStatus,
            error = error,
            eventHandler = EventHandler { event ->
                when (event) {
                    is ShareUiModel.Event.OnSharedToggled -> scope.launch {
                        val d = device ?: return@launch
                        try {
                            deviceRepository.setShared(d.id, event.shared)
                            deviceRepository.setPresence(d.id, event.shared)
                            device = d.copy(isShared = event.shared)
                        } catch (e: Exception) {
                            error = e.localizedMessage
                        }
                    }

                    is ShareUiModel.Event.OnNameChanged -> nameDraft = event.name

                    ShareUiModel.Event.OnNameSaved -> scope.launch {
                        val d = device ?: return@launch
                        try {
                            deviceRepository.setName(d.id, nameDraft)
                            device = d.copy(name = nameDraft)
                            editingName = false
                        } catch (e: Exception) {
                            error = e.localizedMessage
                        }
                    }

                    ShareUiModel.Event.OnNameEditStarted -> editingName = true
                    ShareUiModel.Event.OnNameEditCancelled -> {
                        nameDraft = device?.name.orEmpty()
                        editingName = false
                    }

                    ShareUiModel.Event.OnRetryClicked -> {
                        if (device != null) RelayShareService.retry(context)
                    }

                    ShareUiModel.Event.OnErrorDismissed -> error = null
                }
            },
        )
    }
}
