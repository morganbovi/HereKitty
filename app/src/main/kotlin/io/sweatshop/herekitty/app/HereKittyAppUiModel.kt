package io.sweatshop.herekitty.app

import io.sweatshop.herekitty.domain.features.devices.model.AdbServerState
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.ThemeMode
import io.sweatshop.herekitty.ui.presenter.EventHandler

data class HereKittyAppUiModel(
    val themeMode: ThemeMode,
    val logColumns: LogColumns,
    val logFontScale: Float,
    val memoryCapBytes: Long,
    val restoreLastLayout: Boolean,
    val confirmSessionClose: Boolean,
    val confirmExit: Boolean,
    val notificationDismissSeconds: Int,
    val checkForUpdatesOnStartup: Boolean,
    val serverState: AdbServerState,
    val deviceCount: Int,
    val isSettingsOpen: Boolean,
    val eventHandler: EventHandler<Event>,
) {
    val adbStatus: String
        get() = when (serverState) {
            is AdbServerState.Starting -> "adb starting"
            is AdbServerState.Connected -> if (deviceCount == 1) "1 device" else "$deviceCount devices"
            is AdbServerState.Unavailable -> "adb unavailable"
        }

    val isAdbHealthy: Boolean get() = serverState is AdbServerState.Connected

    sealed interface Event {
        data class OnThemeModeChanged(val mode: ThemeMode) : Event

        data class OnLogColumnsChanged(val columns: LogColumns) : Event

        data class OnLogFontScaleChanged(val scale: Float) : Event

        data class OnMemoryCapChosen(val bytes: Long) : Event

        data class OnRestoreLastLayoutChanged(val restore: Boolean) : Event

        data class OnConfirmSessionCloseChanged(val confirm: Boolean) : Event

        data class OnConfirmExitChanged(val confirm: Boolean) : Event

        data class OnNotificationDismissSecondsChanged(val seconds: Int) : Event

        data class OnCheckForUpdatesOnStartupChanged(val check: Boolean) : Event

        data object OnSettingsOpened : Event

        data object OnSettingsDismissed : Event
    }
}
