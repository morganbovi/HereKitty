package io.sweatshop.herekitty.features.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.ConnectionState
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnAddPaneClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnClearClicked
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
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagMoved
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagSplitOnto
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnTagSplitOut
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyRequested
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnPauseToggled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnReconnectClicked
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnRecordingSwitched
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnSplitRequested
import io.sweatshop.herekitty.features.session.dialogs.ApplyViewPrompt
import io.sweatshop.herekitty.features.session.dialogs.CloseSessionPrompt
import io.sweatshop.herekitty.features.session.dialogs.DeleteViewPrompt
import io.sweatshop.herekitty.features.session.pane.LogPaneContent
import io.sweatshop.herekitty.features.views.ViewActions
import io.sweatshop.herekitty.features.views.ViewMenuPopup
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.files.FileDialogs
import io.sweatshop.herekitty.ui.format.formatBytes
import io.sweatshop.herekitty.ui.format.formatCount
import io.sweatshop.herekitty.ui.split.ReorderGrip
import io.sweatshop.herekitty.ui.split.ResizableSplit
import io.sweatshop.herekitty.ui.split.toSplitOrientation
import io.sweatshop.herekitty.ui.split.SplitOrientation
import io.sweatshop.herekitty.ui.theme.colorFor
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography


private const val RECORDING_EXTENSION = "hklog.gz"
private const val BUNDLE_EXTENSION = "hkbundle"

@Composable
fun SessionContent(
    uiModel: SessionUiModel,
    viewActions: ViewActions,
    modifier: Modifier = Modifier,
    grip: ReorderGrip = ReorderGrip.Disabled,
) {
    if (uiModel.isCloseConfirmOpen) CloseSessionPrompt(uiModel)
    uiModel.viewAwaitingConfirmation?.let { ApplyViewPrompt(uiModel, it) }

    var viewPendingDeletion by remember { mutableStateOf<String?>(null) }

    viewPendingDeletion?.let { name ->
        DeleteViewPrompt(
            name = name,
            isApplied = name == uiModel.view.name,
            onCancel = { viewPendingDeletion = null },
            onDelete = {
                viewActions.onDelete(name)
                viewPendingDeletion = null
            },
        )
    }

    val dragState = remember { PaneDragState() }

    Column(modifier.fillMaxSize()) {
        SessionToolbar(uiModel, viewActions, grip) { viewPendingDeletion = it }
        Divider(Orientation.Horizontal)
        PaneDragHost(dragState, Modifier.fillMaxSize()) {
            PaneTreeContent(uiModel, uiModel.root, dragState, Modifier.fillMaxSize())
        }
    }
}

/** Draws the pane tree, recursing into splits the same way the workspace draws its own. */
@Composable
private fun PaneTreeContent(
    uiModel: SessionUiModel,
    node: PaneNode,
    dragState: PaneDragState,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is PaneNode.Leaf -> {
            val paneId = node.pane.id
            DisposableEffect(paneId) { onDispose { dragState.paneRemoved(paneId) } }

            LogPaneContent(
                session = uiModel.session,
                config = node.pane,
                otherPanes = uiModel.panes.filterNot { it.id == paneId },
                canClose = uiModel.panes.size > 1,
                onConfigChanged = { uiModel.eventHandler(OnPaneConfigChanged(it)) },
                onSplitTagOut = { tag, orientation, side ->
                    uiModel.eventHandler(OnTagSplitOut(paneId, tag, orientation, side))
                },
                onSplitPane = { orientation, side ->
                    uiModel.eventHandler(OnPaneSplit(paneId, orientation, side))
                },
                onMergeInto = { target -> uiModel.eventHandler(OnPanesMerged(paneId, target)) },
                onClose = { uiModel.eventHandler(OnClosePaneClicked(paneId)) },
                grip = dragState.gripFor(paneId, isEnabled = uiModel.panes.size > 1) { drop ->
                    uiModel.eventHandler(drop.eventFrom(paneId))
                },
                tagGrip = { tag ->
                    dragState.tagGrip(tag, paneId) { drop ->
                        uiModel.eventHandler(drop.tagEventFrom(tag, paneId))
                    }
                },
                modifier = modifier
                    .onGloballyPositioned { dragState.panePositioned(paneId, it) }
                    .alpha(if (dragState.draggedPaneId == paneId) DRAGGED_ALPHA else 1f),
            )
        }

        is PaneNode.Split -> ResizableSplit(
            items = node.children,
            key = { it.id.value },
            orientation = node.orientation.toSplitOrientation(),
            modifier = modifier,
            minChildSize = MIN_PANE_SIZE,
        ) { child, _ ->
            PaneTreeContent(uiModel, child, dragState)
        }
    }
}

/** A tag dropped on an edge gets a pane of its own there; dropped in the middle it joins that pane. */
private fun PaneDropTarget.tagEventFrom(tag: String, sourceId: PaneId): SessionUiModel.Event {
    val orientation = region.orientation
    val side = region.side
    return if (orientation != null && side != null) {
        OnTagSplitOnto(tag, sourceId, paneId, orientation, side)
    } else {
        OnTagMoved(tag, sourceId, paneId)
    }
}

/** An edge drop inserts the pane beside its target; the middle trades the two places. */
private fun PaneDropTarget.eventFrom(sourceId: PaneId): SessionUiModel.Event {
    val orientation = region.orientation
    val side = region.side
    return if (orientation != null && side != null) {
        OnPaneMoved(sourceId, paneId, orientation, side)
    } else {
        OnPanesSwapped(sourceId, paneId)
    }
}

@Composable
private fun SessionToolbar(
    uiModel: SessionUiModel,
    viewActions: ViewActions,
    grip: ReorderGrip,
    onDeleteRequested: (String) -> Unit,
) {
    var isSourceMenuOpen by remember { mutableStateOf(false) }
    var isViewMenuOpen by remember { mutableStateOf(false) }
    var isExportMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
            .background(
                if (grip.isDragging) {
                    JewelTheme.globalColors.outlines.focused.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (grip.isEnabled) {
            Box(modifier = grip.modifier.pointerHoverIcon(MOVE_CURSOR), contentAlignment = Alignment.Center) {
                Icon(AllIconsKeys.General.Drag, contentDescription = "Drag to move this session")
            }
        }

        Box {
            Tooltip(tooltip = { Text("Point this session at another device or a recording") }) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { isSourceMenuOpen = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = uiModel.source.label,
                        style = JewelTheme.typography.regular,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = SOURCE_NAME_MAX_WIDTH),
                    )
                    Icon(AllIconsKeys.General.ChevronDown, contentDescription = null)
                }
            }

            if (isSourceMenuOpen) {
                SourceMenu(uiModel) { isSourceMenuOpen = false }
            }
        }

        ConnectionBadge(uiModel.connection)

        Box {
            Tooltip(tooltip = { Text("Save, apply, or share this pane setup") }) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { isViewMenuOpen = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(AllIconsKeys.General.Layout, contentDescription = "Views")
                    Text(
                        text = uiModel.viewLabel,
                        style = JewelTheme.typography.small,
                        color = if (uiModel.view.name.isBlank()) {
                            JewelTheme.globalColors.text.info
                        } else {
                            JewelTheme.globalColors.text.normal
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = VIEW_NAME_MAX_WIDTH),
                    )
                    Icon(AllIconsKeys.General.ChevronDown, contentDescription = null)
                }
            }

            if (isViewMenuOpen) {
                ViewMenuPopup(
                    current = uiModel.view,
                    // The session decides whether replacing its arrangement needs asking about first,
                    // and a delete is held here until it is confirmed.
                    actions = viewActions.copy(
                        onApply = { uiModel.eventHandler(OnViewApplyRequested(it)) },
                        onDelete = onDeleteRequested,
                    ),
                    onDismissRequest = { isViewMenuOpen = false },
                )
            }
        }

        Box(Modifier.weight(1f))

        MemoryMeter(uiModel)

        IconAction(
            key = AllIconsKeys.General.Add,
            description = "Add a filter pane",
            onClick = { uiModel.eventHandler(OnAddPaneClicked) },
        )

        IconAction(
            key = AllIconsKeys.Actions.GC,
            description = "Discard everything recorded so far",
            enabled = uiModel.stats.lineCount > 0L,
            onClick = { uiModel.eventHandler(OnClearClicked) },
        )

        IconAction(
            key = if (uiModel.isPaused) AllIconsKeys.Actions.Resume else AllIconsKeys.Actions.Pause,
            description = if (uiModel.isPaused) "Resume capture" else "Pause capture",
            enabled = uiModel.isLive,
            onClick = { uiModel.eventHandler(OnPauseToggled) },
        )

        IconAction(
            key = AllIconsKeys.Actions.Refresh,
            description = "Restart the logcat stream",
            enabled = uiModel.isLive,
            onClick = { uiModel.eventHandler(OnReconnectClicked) },
        )

        Box {
            IconAction(
                key = AllIconsKeys.Actions.Upload,
                description = if (uiModel.isExporting) "Exporting…" else "Export this session",
                enabled = !uiModel.isExporting && uiModel.stats.lineCount > 0L,
                onClick = { isExportMenuOpen = true },
            )

            if (isExportMenuOpen) {
                ExportMenu(uiModel) { isExportMenuOpen = false }
            }
        }

        IconAction(
            key = AllIconsKeys.Actions.SplitVertically,
            description = "Open another source to the right",
            onClick = { uiModel.eventHandler(OnSplitRequested(SplitOrientation.Horizontal)) },
        )

        IconAction(
            key = AllIconsKeys.Actions.SplitHorizontally,
            description = "Open another source below",
            onClick = { uiModel.eventHandler(OnSplitRequested(SplitOrientation.Vertical)) },
        )

        IconAction(
            key = AllIconsKeys.General.Close,
            description = "Close this session",
            onClick = { uiModel.eventHandler(OnCloseSessionClicked) },
        )
    }
}

@Composable
private fun ExportMenu(uiModel: SessionUiModel, onDismissRequest: () -> Unit) {
    val suggestedName = uiModel.source.label.replace(' ', '-')

    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.End,
        modifier = Modifier.width(MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 3.dp)) {
            MenuRow(
                label = "Logs and view…",
                icon = AllIconsKeys.Actions.Upload,
                onClick = {
                    FileDialogs.saveFile(
                        title = "Export this session and its view",
                        suggestedName = suggestedName,
                        extension = BUNDLE_EXTENSION,
                    )?.let { uiModel.eventHandler(OnExportBundleRequested(it)) }
                    onDismissRequest()
                },
            )

            MenuRow(
                label = "Logs only…",
                icon = AllIconsKeys.Actions.Upload,
                onClick = {
                    FileDialogs.saveFile(
                        title = "Export this recording",
                        suggestedName = suggestedName,
                        extension = RECORDING_EXTENSION,
                    )?.let { uiModel.eventHandler(OnExportRecordingRequested(it)) }
                    onDismissRequest()
                },
            )
        }
    }
}

@Composable
private fun SourceMenu(uiModel: SessionUiModel, onDismissRequest: () -> Unit) {
    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 3.dp)) {
            val streamable = uiModel.availableDevices.filter { it.state.canStreamLogs }

            if (streamable.isEmpty()) {
                Text(
                    text = "No devices attached",
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                streamable.forEach { device ->
                    val isCurrent = device.serial == uiModel.currentSerial
                    MenuRow(
                        label = if (isCurrent) "${device.displayName} · current" else device.displayName,
                        icon = if (isCurrent) AllIconsKeys.Actions.Checked else AllIconsKeys.General.Mouse,
                        enabled = !isCurrent,
                        onClick = {
                            uiModel.eventHandler(OnDeviceSwitched(device))
                            onDismissRequest()
                        },
                    )
                }
            }

            Divider(Orientation.Horizontal, modifier = Modifier.padding(vertical = 3.dp))

            MenuRow(
                label = "Open a recording or bundle…",
                icon = AllIconsKeys.Actions.MenuOpen,
                onClick = {
                    FileDialogs.openRecording()?.let { uiModel.eventHandler(OnRecordingSwitched(it)) }
                    onDismissRequest()
                },
            )

            Text(
                text = "Switching keeps this pane setup and starts a fresh recording.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: org.jetbrains.jewel.ui.icon.IconKey,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ROW_HEIGHT)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(
            text = label,
            style = JewelTheme.typography.regular,
            color = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ConnectionBadge(connection: ConnectionState) {
    val color = when (connection) {
        ConnectionState.Streaming -> colorFor(LogLevel.INFO)
        ConnectionState.Connecting -> JewelTheme.globalColors.text.info
        ConnectionState.Recorded -> JewelTheme.globalColors.text.info
        ConnectionState.Paused -> colorFor(LogLevel.WARN)
        is ConnectionState.Waiting -> colorFor(LogLevel.WARN)
        is ConnectionState.Failed -> colorFor(LogLevel.ERROR)
    }

    Text(
        text = connection.label,
        style = JewelTheme.typography.small,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = STATUS_MAX_WIDTH),
    )
}

@Composable
private fun MemoryMeter(uiModel: SessionUiModel) {
    val stats = uiModel.stats
    val tooltip = buildString {
        append(formatCount(stats.lineCount)).append(" lines held, ")
        append(formatCount(stats.totalLinesSeen)).append(" seen this session")
        if (stats.hasDropped) {
            append('\n').append(formatCount(stats.droppedLines))
                .append(" oldest lines dropped to stay under the limit")
        }
    }

    Tooltip(tooltip = { Text(tooltip) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatCount(stats.lineCount)} lines",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
            )

            HorizontalProgressBar(progress = stats.usedFraction, modifier = Modifier.width(MEMORY_BAR_WIDTH))

            Text(
                text = "${formatBytes(stats.estimatedBytes)} / ${formatBytes(stats.capacityBytes)}",
                style = JewelTheme.typography.small,
                color = if (stats.hasDropped) colorFor(LogLevel.WARN) else JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val TOOLBAR_HEIGHT = 28.dp
private val MEMORY_BAR_WIDTH = 56.dp
private val SOURCE_NAME_MAX_WIDTH = 140.dp
private val VIEW_NAME_MAX_WIDTH = 110.dp
private val STATUS_MAX_WIDTH = 110.dp
private val MENU_WIDTH = 250.dp
private val MENU_ROW_HEIGHT = 26.dp
private val MIN_PANE_SIZE = 150.dp
private val MOVE_CURSOR = PointerIcon(Cursor(Cursor.MOVE_CURSOR))
