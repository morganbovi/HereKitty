package io.sweatshop.herekitty.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import io.sweatshop.herekitty.domain.features.updates.repository.UpdateRepository
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnCheckRequested
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnDismissed
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnDownloadRequested
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnRestartRequested
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
class UpdatePresenter(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
) {
    @Composable
    fun present(onExitApplication: () -> Unit): UpdateUiModel {
        val scope = rememberCoroutineScope()
        val state by updateRepository.state.collectAsState()
        var wasAsked by remember { mutableStateOf(false) }

        // Keyed to nothing so it runs once for the launch, not once per recomposition. Read directly
        // rather than collected: turning the setting on should take effect next launch, not this one.
        LaunchedEffect(Unit) {
            if (settingsRepository.checkForUpdatesOnStartup.value) updateRepository.check()
        }

        return UpdateUiModel(
            state = state,
            runningVersion = AppVersion.Current,
            wasAsked = wasAsked,
            eventHandler = EventHandler {
                when (it) {
                    OnCheckRequested -> {
                        wasAsked = true
                        scope.launch { updateRepository.check() }
                    }

                    OnDownloadRequested -> {
                        val available = state as? UpdateState.Available ?: return@EventHandler
                        scope.launch { updateRepository.download(available.release) }
                    }

                    // The helper waits on this process, so quitting is what lets it act.
                    OnRestartRequested -> scope.launch {
                        if (updateRepository.applyAndRestart()) onExitApplication()
                    }

                    OnDismissed -> {
                        wasAsked = false
                        updateRepository.dismiss()
                    }
                }
            },
        )
    }
}
