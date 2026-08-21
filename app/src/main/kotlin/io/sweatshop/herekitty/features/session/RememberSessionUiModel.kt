package io.sweatshop.herekitty.features.session

import androidx.compose.runtime.Composable
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import java.nio.file.Path
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.split.SplitOrientation
import org.koin.compose.koinInject

@Composable
fun rememberSessionUiModel(
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
    presenter: SessionPresenter = koinInject(),
): SessionUiModel = presenter.present(
    session = session,
    view = view,
    savedViews = savedViews,
    canCloseSession = canCloseSession,
    onViewChanged = onViewChanged,
    onSaveView = onSaveView,
    onSplit = onSplit,
    onSwitchToDevice = onSwitchToDevice,
    onSwitchToRecording = onSwitchToRecording,
    onCloseSession = onCloseSession,
    onNotice = onNotice,
)
