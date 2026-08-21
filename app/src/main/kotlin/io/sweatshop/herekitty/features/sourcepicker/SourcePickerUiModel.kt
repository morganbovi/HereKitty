package io.sweatshop.herekitty.features.sourcepicker

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.model.AdbServerState
import io.sweatshop.herekitty.domain.features.devices.model.AdbToolsState
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.presenter.EventHandler
import java.nio.file.Path

data class SourcePickerUiModel(
    val devices: List<AdbDevice>,
    val serverState: AdbServerState,
    val toolsState: AdbToolsState,
    val busySerials: Set<String>,
    val pendingView: ViewConfig,
    val canClose: Boolean,
    val eventHandler: EventHandler<Event>,
) {
    val unavailableReason: String? get() = (serverState as? AdbServerState.Unavailable)?.reason

    val isStarting: Boolean get() = serverState is AdbServerState.Starting

    /** Offering the download only makes sense when there is no adb to find in the first place. */
    val canInstallTools: Boolean
        get() = toolsState is AdbToolsState.Missing || toolsState is AdbToolsState.Failed

    val installProgress: Float? get() = (toolsState as? AdbToolsState.Installing)?.fraction

    val isInstallingTools: Boolean get() = toolsState is AdbToolsState.Installing

    val installFailure: String? get() = (toolsState as? AdbToolsState.Failed)?.reason

    /**
     * Set when a view is already waiting to be applied to whichever source is chosen.
     *
     * Announced for *any* setup, not only a named or multi-pane one. A restored layout whose device
     * was not plugged in leaves its view sitting here, and a slot that silently carries one looks
     * exactly like a blank slate until the logs arrive filtered.
     */
    val pendingViewLabel: String?
        get() = when {
            pendingView.name.isNotBlank() -> pendingView.name
            pendingView.hasSameSetupAs(ViewConfig.Default) -> null
            else -> pendingView.summary
        }

    sealed interface Event {
        data class OnDeviceClicked(val device: AdbDevice) : Event

        data class OnRecordingChosen(val path: Path) : Event

        data object OnInstallToolsClicked : Event

        data object OnPendingViewCleared : Event

        data object OnRetryClicked : Event

        data object OnCloseClicked : Event
    }
}
