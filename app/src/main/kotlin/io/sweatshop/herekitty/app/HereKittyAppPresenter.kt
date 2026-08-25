package io.sweatshop.herekitty.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnCheckForUpdatesOnStartupChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnCompactViewToggled
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnConfirmExitChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnNotificationDismissSecondsChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnConfirmSessionCloseChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnLogColumnsChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnLogFontScaleChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnMemoryCapChosen
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnRestoreLastLayoutChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSelectMessageOnlyChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSettingsDismissed
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSettingsOpened
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnThemeModeChanged
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.ui.presenter.EventHandler
import org.koin.core.annotation.Factory

@Factory
class HereKittyAppPresenter(
    private val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
) {
    @Composable
    fun present(): HereKittyAppUiModel {
        val themeMode by settingsRepository.themeMode.collectAsState()
        val logColumns by settingsRepository.logColumns.collectAsState()
        val logFontScale by settingsRepository.logFontScale.collectAsState()
        val notificationDismissSeconds by settingsRepository.notificationDismissSeconds.collectAsState()
        val checkForUpdatesOnStartup by settingsRepository.checkForUpdatesOnStartup.collectAsState()
        val memoryCapBytes by settingsRepository.memoryCapBytes.collectAsState()
        val restoreLastLayout by settingsRepository.restoreLastLayout.collectAsState()
        val confirmSessionClose by settingsRepository.confirmSessionClose.collectAsState()
        val confirmExit by settingsRepository.confirmExit.collectAsState()
        val selectMessageOnly by settingsRepository.selectMessageOnly.collectAsState()
        val isCompactView by settingsRepository.isCompactView.collectAsState()
        val serverState by deviceRepository.serverState.collectAsState()
        val devices by deviceRepository.devices.collectAsState()

        var isSettingsOpen by remember { mutableStateOf(false) }

        return HereKittyAppUiModel(
            themeMode = themeMode,
            logColumns = logColumns,
            logFontScale = logFontScale,
            notificationDismissSeconds = notificationDismissSeconds,
            checkForUpdatesOnStartup = checkForUpdatesOnStartup,
            memoryCapBytes = memoryCapBytes,
            restoreLastLayout = restoreLastLayout,
            confirmSessionClose = confirmSessionClose,
            confirmExit = confirmExit,
            selectMessageOnly = selectMessageOnly,
            isCompactView = isCompactView,
            serverState = serverState,
            deviceCount = devices.size,
            isSettingsOpen = isSettingsOpen,
            eventHandler = EventHandler { event ->
                when (event) {
                    is OnThemeModeChanged -> settingsRepository.setThemeMode(event.mode)
                    is OnLogColumnsChanged -> settingsRepository.setLogColumns(event.columns)
                    is OnLogFontScaleChanged -> settingsRepository.setLogFontScale(event.scale)
                    is OnMemoryCapChosen -> settingsRepository.setMemoryCapBytes(event.bytes)
                    is OnRestoreLastLayoutChanged -> settingsRepository.setRestoreLastLayout(event.restore)
                    is OnConfirmSessionCloseChanged -> settingsRepository.setConfirmSessionClose(event.confirm)
                    is OnConfirmExitChanged -> settingsRepository.setConfirmExit(event.confirm)
                    is OnSelectMessageOnlyChanged -> settingsRepository.setSelectMessageOnly(event.messageOnly)
                    OnCompactViewToggled -> settingsRepository.setCompactView(!isCompactView)
                    is OnCheckForUpdatesOnStartupChanged ->
                        settingsRepository.setCheckForUpdatesOnStartup(event.check)

                    is OnNotificationDismissSecondsChanged ->
                        settingsRepository.setNotificationDismissSeconds(event.seconds)
                    OnSettingsOpened -> isSettingsOpen = true
                    OnSettingsDismissed -> isSettingsOpen = false
                }
            },
        )
    }
}
