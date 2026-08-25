package io.sweatshop.herekitty.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.features.settings.SettingsWindow
import io.sweatshop.herekitty.features.workspace.WorkspaceContent
import io.sweatshop.herekitty.features.workspace.WorkspacePresenter
import io.sweatshop.herekitty.features.workspace.WorkspaceTab
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnNewTabClicked
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnNoticeDismissed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnTabClosed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnTabSelected
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSettingsOpened
import io.sweatshop.herekitty.features.updates.UpdateBalloon
import io.sweatshop.herekitty.features.updates.UpdatePresenter
import io.sweatshop.herekitty.ui.notification.NotificationHost
import io.sweatshop.herekitty.ui.theme.HereKittyTheme
import io.sweatshop.herekitty.ui.theme.resolveDarkTheme
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.editorTabStyle
import org.jetbrains.jewel.ui.typography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject

@Composable
fun HereKittyApp(
    presenter: HereKittyAppPresenter = koinInject(),
    workspacePresenter: WorkspacePresenter = koinInject(),
    titleBar: @Composable (HereKittyAppUiModel) -> Unit = {},
    settingsRequests: Flow<Unit> = emptyFlow(),
    onExitApplication: () -> Unit = {},
    updatePresenter: UpdatePresenter = koinInject(),
) {
    val uiModel = presenter.present()
    val workspaceUiModel = workspacePresenter.present()
    val updateUiModel = updatePresenter.present(onExitApplication)

    // The platform menu bar can ask for settings too, from outside the composition.
    LaunchedEffect(settingsRequests) {
        settingsRequests.collect { uiModel.eventHandler(OnSettingsOpened) }
    }

    HereKittyTheme(isDark = resolveDarkTheme(uiModel.themeMode)) {
        if (uiModel.isSettingsOpen) SettingsWindow(uiModel, updateUiModel)

        titleBar(uiModel)

        Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            Column(Modifier.fillMaxSize()) {
                // Only the window's own title bar survives compact view; everything this app draws
                // itself, starting with the tab strip, goes to leave nothing but log content.
                if (!uiModel.isCompactView) {
                    TabBar(workspaceUiModel)
                    Divider(Orientation.Horizontal)
                }
                WorkspaceContent(workspaceUiModel, Modifier.weight(1f))
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End,
            ) {
                NotificationHost(
                    notifications = workspaceUiModel.notifications,
                    dismissAfterSeconds = uiModel.notificationDismissSeconds,
                    onDismiss = { workspaceUiModel.eventHandler(OnNoticeDismissed(it)) },
                )

                // Below the notices, so an update offer is the last thing before the corner and does
                // not get pushed around as notices come and go.
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    UpdateBalloon(updateUiModel)
                }
            }
        }
    }
}

@Composable
private fun TabBar(uiModel: WorkspaceUiModel) {
    // The add button rides along as a trailing tab. TabStrip always fills its width — its scrollbar
    // is fillMaxWidth inside — so a sibling button can only ever end up pinned to the far right.
    val titles = uiModel.tabTitles

    val tabs = uiModel.tabs.mapIndexed { index, tab ->
        TabData.Editor(
            selected = tab.id == uiModel.activeTabId,
            closable = uiModel.canCloseTabs,
            onClose = { uiModel.eventHandler(OnTabClosed(tab.id)) },
            onClick = { uiModel.eventHandler(OnTabSelected(tab.id)) },
            content = {
                Text(
                    text = titles.getOrElse(index) { WorkspaceTab.UNNAMED_TITLE },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = TAB_TITLE_MAX_WIDTH),
                )
            },
        )
    } + TabData.Editor(
        selected = false,
        closable = false,
        onClick = { uiModel.eventHandler(OnNewTabClicked) },
        content = {
            Tooltip(tooltip = { Text(NEW_TAB_DESCRIPTION) }) {
                Icon(AllIconsKeys.General.Add, contentDescription = NEW_TAB_DESCRIPTION)
            }
        },
    )

    TabStrip(
        tabs = tabs,
        style = JewelTheme.editorTabStyle,
        modifier = Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT),
    )
}

private val TAB_BAR_HEIGHT = 28.dp
private val TAB_TITLE_MAX_WIDTH = 200.dp
private const val NEW_TAB_DESCRIPTION = "Open another tab"
