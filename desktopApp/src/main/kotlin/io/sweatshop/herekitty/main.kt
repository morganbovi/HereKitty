package io.sweatshop.herekitty

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import java.awt.Desktop
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.sweatshop.herekitty.app.AppTitleBarContent
import io.sweatshop.herekitty.app.HereKittyApp
import io.sweatshop.herekitty.app.dialogs.ExitPrompt
import io.sweatshop.herekitty.di.appModule
import io.sweatshop.herekitty.domain.features.lifecycle.repository.AppLifecycleRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LastSessionRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LogSessionRepository
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.ui.keyboard.rememberCompactViewShortcuts
import io.sweatshop.herekitty.ui.keyboard.rememberFontScaleShortcuts
import io.sweatshop.herekitty.ui.theme.HereKittyTheme
import io.sweatshop.herekitty.ui.theme.resolveDarkTheme
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(appModule) }

    application {
        val settings = koinInject<SettingsRepository>()
        val sessionRepository = koinInject<LogSessionRepository>()
        val lastSessionRepository = koinInject<LastSessionRepository>()
        val lifecycleRepository = koinInject<AppLifecycleRepository>()
        val confirmExit by settings.confirmExit.collectAsState()
        val themeMode by settings.themeMode.collectAsState()
        var isExitPromptOpen by remember { mutableStateOf(false) }
        val settingsRequests = remember {
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }
        val quitScope = rememberCoroutineScope()

        // Every live device session is snapshotted before the window actually goes, so a slot whose
        // device is gone next launch can still open what it last held. Marked clean last, since that
        // is the one bit that must never be true unless this actually finished.
        fun quit() {
            quitScope.launch {
                withTimeoutOrNull(QUIT_SAVE_TIMEOUT_MILLIS) {
                    lastSessionRepository.saveAll(sessionRepository.sessions.value)
                }
                lifecycleRepository.markCleanExit()
                exitApplication()
            }
        }

        // Puts a working "Settings…" in the macOS application menu.
        DisposableEffect(Unit) {
            val desktop = Desktop.getDesktop()
            val supported = desktop.isSupported(Desktop.Action.APP_PREFERENCES)
            if (supported) desktop.setPreferencesHandler { settingsRequests.tryEmit(Unit) }
            onDispose { if (supported) desktop.setPreferencesHandler(null) }
        }

        val onFontScaleKey = rememberFontScaleShortcuts()
        val onCompactViewKey = rememberCompactViewShortcuts()

        HereKittyTheme(isDark = resolveDarkTheme(themeMode)) {
            DecoratedWindow(
                // Only a manual export survives as a shareable file, so it is worth a question by default.
                onCloseRequest = { if (confirmExit) isExitPromptOpen = true else quit() },
                state = rememberWindowState(size = DpSize(1600.dp, 920.dp)),
                title = "HereKitty",
                icon = AppIcon.painter,
                onKeyEvent = { onFontScaleKey(it) || onCompactViewKey(it) },
            ) {
                HereKittyApp(
                    titleBar = { uiModel -> TitleBar { AppTitleBarContent(uiModel) } },
                    settingsRequests = settingsRequests,
                    // An in-place update is applied by a helper that waits for this process to go.
                    onExitApplication = ::exitApplication,
                )

                if (isExitPromptOpen) {
                    ExitPrompt(
                        onCancel = { isExitPromptOpen = false },
                        onExit = { stopAsking ->
                            if (stopAsking) settings.setConfirmExit(false)
                            quit()
                        },
                    )
                }
            }
        }
    }
}

private const val QUIT_SAVE_TIMEOUT_MILLIS = 3_000L
