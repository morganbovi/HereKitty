package io.sweatshop.herekitty.features.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.base.launchCoroutine
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.updatePane
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnAddPaneClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnAskBeforeClosingChanged
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnClearClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnCloseCancelled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyCancelled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyConfirmed
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyRequested
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnCloseConfirmed
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnClosePaneClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnCloseSessionClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnDeviceSwitched
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnExportBundleRequested
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnExportRecordingRequested
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPaneConfigChanged
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPaneSplit
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPanesMerged
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPaneMoved
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPanesSwapped
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPauseToggled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnReconnectClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnRecordingSwitched
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnSplitRequested
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagMoved
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagSplitOnto
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagSplitOut
import io.sweatshop.herekitty.features.session.dialogs.shouldConfirmApply
import io.sweatshop.herekitty.features.session.dialogs.shouldConfirmClose
import io.sweatshop.herekitty.ui.presenter.EventHandler
import io.sweatshop.herekitty.ui.split.SplitOrientation
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Factory


@Factory
class SessionPresenter(
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
) {

    @Composable
    fun present(
        session: LogSession,
        view: ViewConfig,
        savedViews: List<ViewConfig>,
        canCloseSession: Boolean,
        onViewChanged: (ViewConfig) -> Unit,
        onSaveView: (ViewConfig) -> Unit,
        onSplit: (SplitOrientation) -> Unit,
        onSwitchToDevice: (AdbDevice) -> Unit,
        onSwitchToRecording: (Path) -> Unit,
        onCloseSession: () -> Unit,
        onNotice: (String) -> Unit,
    ): SessionUiModel {
        val scope = rememberCoroutineScope()
        val source by session.source.collectAsState()
        val connection by session.connection.collectAsState()
        val stats by session.stats.collectAsState()
        val devices by deviceRepository.devices.collectAsState()
        val askBeforeClosing by settingsRepository.confirmSessionClose.collectAsState()
        val isCompactView by settingsRepository.isCompactView.collectAsState()

        var isExporting by remember(session) { mutableStateOf(false) }
        var isCloseConfirmOpen by remember(session) { mutableStateOf(false) }
        var viewAwaitingConfirmation by remember(session) { mutableStateOf<ViewConfig?>(null) }

        val savedMatch = savedViews.firstOrNull { it.name == view.name }
        val hasUnsavedChanges = savedMatch == null || !view.hasSameSetupAs(savedMatch)

        fun changeLayout(change: (PaneNode) -> PaneNode) {
            onViewChanged(view.copy(root = change(view.root)))
        }

        return SessionUiModel(
            session = session,
            source = source,
            connection = connection,
            stats = stats,
            view = view,
            availableDevices = devices,
            hasUnsavedChanges = hasUnsavedChanges,
            isCloseConfirmOpen = isCloseConfirmOpen,
            viewAwaitingConfirmation = viewAwaitingConfirmation,
            askBeforeClosing = askBeforeClosing,
            isExporting = isExporting,
            canCloseSession = canCloseSession,
            isCompactView = isCompactView,
            eventHandler = EventHandler(session.id, view.root) { event ->
                when (event) {
                    OnAddPaneClicked -> changeLayout { addPane(it) }

                    is OnClosePaneClicked -> changeLayout { root -> closePane(root, event.paneId) ?: root }

                    is OnPaneConfigChanged -> changeLayout { it.updatePane(event.config) }

                    is OnTagSplitOut -> changeLayout { root ->
                        splitTagOut(root, event.paneId, event.tag, event.orientation, event.side)
                    }

                    is OnPaneSplit -> changeLayout { root ->
                        splitPane(root, event.paneId, event.orientation, event.side)
                    }

                    is OnPanesMerged -> changeLayout { root ->
                        mergePaneInto(root, event.sourceId, event.targetId)
                    }

                    is OnPaneMoved -> changeLayout { root ->
                        movePane(root, event.sourceId, event.targetId, event.orientation, event.side)
                    }

                    is OnPanesSwapped -> changeLayout { root ->
                        swapPanes(root, event.first, event.second)
                    }

                    is OnTagMoved -> changeLayout { root ->
                        moveTag(root, event.tag, event.sourceId, event.targetId)
                    }

                    is OnTagSplitOnto -> changeLayout { root ->
                        splitTagOnto(
                            root = root,
                            tag = event.tag,
                            sourceId = event.sourceId,
                            targetId = event.targetId,
                            orientation = event.orientation,
                            side = event.side,
                        )
                    }

                    OnClearClicked -> session.clear()
                    OnPauseToggled -> session.setPaused(connection != ConnectionState.Paused)
                    OnReconnectClicked -> session.reconnect()
                    is OnDeviceSwitched -> onSwitchToDevice(event.device)
                    is OnRecordingSwitched -> onSwitchToRecording(event.path)

                    is OnExportRecordingRequested -> exportWith(
                        scope = scope,
                        path = event.path,
                        onNotice = onNotice,
                        onExportingChanged = { isExporting = it },
                        write = { session.exportTo(event.path) },
                    )

                    is OnExportBundleRequested -> exportWith(
                        scope = scope,
                        path = event.path,
                        onNotice = onNotice,
                        onExportingChanged = { isExporting = it },
                        write = { session.exportBundleTo(event.path, view) },
                    )

                    is OnSplitRequested -> onSplit(event.orientation)

                    OnCloseSessionClicked ->
                        if (shouldConfirmClose(view, hasUnsavedChanges, askBeforeClosing)) {
                            isCloseConfirmOpen = true
                        } else {
                            onCloseSession()
                        }

                    is OnCloseConfirmed -> {
                        event.saveAs?.trim()?.takeIf { it.isNotEmpty() }
                            ?.let { onSaveView(view.copy(name = it)) }
                        isCloseConfirmOpen = false
                        onCloseSession()
                    }

                    OnCloseCancelled -> isCloseConfirmOpen = false

                    is OnViewApplyRequested ->
                        if (shouldConfirmApply(view, event.view, hasUnsavedChanges)) {
                            viewAwaitingConfirmation = event.view
                        } else {
                            onViewChanged(event.view)
                        }

                    OnViewApplyConfirmed -> {
                        viewAwaitingConfirmation?.let(onViewChanged)
                        viewAwaitingConfirmation = null
                    }

                    OnViewApplyCancelled -> viewAwaitingConfirmation = null

                    is OnAskBeforeClosingChanged ->
                        settingsRepository.setConfirmSessionClose(event.ask)
                }
            },
        )
    }

    /** Both exports differ only in what they write, so the progress and notice handling is shared. */
    private fun exportWith(
        scope: CoroutineScope,
        path: Path,
        onNotice: (String) -> Unit,
        onExportingChanged: (Boolean) -> Unit,
        write: suspend () -> Result<Long>,
    ) {
        scope.launchCoroutine(
            onError = {
                onExportingChanged(false)
                onNotice(it.message ?: "Could not export this session")
            },
        ) {
            onExportingChanged(true)
            write()
                .onSuccess { onNotice("Exported $it lines to ${path.fileName}") }
                .onFailure { onNotice(it.message ?: "Could not export this session") }
            onExportingChanged(false)
        }
    }
}
