package io.sweatshop.herekitty.features.sourcepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.repository.AdbToolsRepository
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.LastSessionRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LogSessionRepository
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnCloseClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnDeviceClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnInstallToolsClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnPendingViewCleared
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRecordingChosen
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRetryClicked
import io.sweatshop.herekitty.ui.presenter.EventHandler
import java.nio.file.Path
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
class SourcePickerPresenter(
    private val deviceRepository: DeviceRepository,
    private val sessionRepository: LogSessionRepository,
    private val adbToolsRepository: AdbToolsRepository,
    private val lastSessionRepository: LastSessionRepository,
) {
    @Composable
    fun present(
        pendingView: ViewConfig,
        canClose: Boolean,
        onDeviceChosen: (AdbDevice) -> Unit,
        onRecordingChosen: (Path) -> Unit,
        onClearPendingView: () -> Unit,
        onClose: () -> Unit,
    ): SourcePickerUiModel {
        val scope = rememberCoroutineScope()
        val devices by deviceRepository.devices.collectAsState()
        val serverState by deviceRepository.serverState.collectAsState()
        val sessions by sessionRepository.sessions.collectAsState()
        val toolsState by adbToolsRepository.state.collectAsState()
        val busySerials = sessions
            .mapNotNull { (it.source.value as? SessionSource.Device)?.device?.serial }
            .toSet()
        // Read once per slot instance rather than on every recomposition: a save only ever happens at
        // quit, by which point there is no next frame here to pick it up anyway.
        val lastSessions = remember { lastSessionRepository.findAll() }.filterNot { it.serial in busySerials }

        return SourcePickerUiModel(
            devices = devices,
            serverState = serverState,
            toolsState = toolsState,
            busySerials = busySerials,
            pendingView = pendingView,
            lastSessions = lastSessions,
            canClose = canClose,
            eventHandler = EventHandler { event ->
                when (event) {
                    is OnDeviceClicked -> onDeviceChosen(event.device)
                    is OnRecordingChosen -> onRecordingChosen(event.path)
                    OnRetryClicked -> {
                        adbToolsRepository.refresh()
                        deviceRepository.reconnect()
                    }

                    // Tracking is retried only once the tools are actually there to be found.
                    OnInstallToolsClicked -> scope.launch {
                        adbToolsRepository.install().onSuccess { deviceRepository.reconnect() }
                    }
                    OnPendingViewCleared -> onClearPendingView()
                    OnCloseClicked -> onClose()
                }
            },
        )
    }
}
