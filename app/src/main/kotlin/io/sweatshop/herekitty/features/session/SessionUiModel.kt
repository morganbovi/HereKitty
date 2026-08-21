package io.sweatshop.herekitty.features.session

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.model.BufferStats
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.presenter.EventHandler
import io.sweatshop.herekitty.ui.split.SplitOrientation
import java.nio.file.Path

data class SessionUiModel(
    val session: LogSession,
    val source: SessionSource,
    val connection: ConnectionState,
    val stats: BufferStats,
    val view: ViewConfig,
    val availableDevices: List<AdbDevice>,
    val hasUnsavedChanges: Boolean,
    val isCloseConfirmOpen: Boolean,
    val viewAwaitingConfirmation: ViewConfig?,
    val askBeforeClosing: Boolean,
    val isExporting: Boolean,
    val canCloseSession: Boolean,
    val eventHandler: EventHandler<Event>,
) {
    val root: PaneNode get() = view.root

    val panes: List<PaneConfig> get() = view.panes

    val isPaused: Boolean get() = connection == ConnectionState.Paused

    val isLive: Boolean get() = session.isLive

    val viewLabel: String get() = view.name.ifBlank { "Unsaved view" }

    val isNamed: Boolean get() = view.name.isNotBlank()

    val currentSerial: String? get() = (source as? SessionSource.Device)?.device?.serial

    sealed interface Event {
        data object OnAddPaneClicked : Event

        data class OnClosePaneClicked(val paneId: PaneId) : Event

        data class OnPaneConfigChanged(val config: PaneConfig) : Event

        data class OnPaneMoved(
            val sourceId: PaneId,
            val targetId: PaneId,
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnPanesSwapped(val first: PaneId, val second: PaneId) : Event

        data class OnTagMoved(val tag: String, val sourceId: PaneId, val targetId: PaneId) : Event

        data class OnTagSplitOnto(
            val tag: String,
            val sourceId: PaneId,
            val targetId: PaneId,
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnTagSplitOut(
            val paneId: PaneId,
            val tag: String,
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnPaneSplit(
            val paneId: PaneId,
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnPanesMerged(val sourceId: PaneId, val targetId: PaneId) : Event

        data object OnClearClicked : Event

        data object OnPauseToggled : Event

        data object OnReconnectClicked : Event

        data class OnDeviceSwitched(val device: AdbDevice) : Event

        data class OnRecordingSwitched(val path: Path) : Event

        data class OnExportRecordingRequested(val path: Path) : Event

        data class OnExportBundleRequested(val path: Path) : Event

        data class OnSplitRequested(val orientation: SplitOrientation) : Event

        data object OnCloseSessionClicked : Event

        data class OnCloseConfirmed(val saveAs: String?) : Event

        data object OnCloseCancelled : Event

        data class OnViewApplyRequested(val view: ViewConfig) : Event

        data object OnViewApplyConfirmed : Event

        data object OnViewApplyCancelled : Event

        data class OnAskBeforeClosingChanged(val ask: Boolean) : Event
    }
}
