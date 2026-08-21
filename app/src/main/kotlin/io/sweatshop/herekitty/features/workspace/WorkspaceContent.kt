package io.sweatshop.herekitty.features.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.features.session.SessionContent
import io.sweatshop.herekitty.features.session.rememberSessionUiModel
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerContent
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerPresenter
import io.sweatshop.herekitty.features.views.ViewActions
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnChildrenReordered
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnDeviceChosen
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnRecordingChosen
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSlotClosed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSplitRequested
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSwitchSourceRequested
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewChanged
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewDeleted
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewExported
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewImported
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewSaved
import io.sweatshop.herekitty.ui.files.FileDialogs
import io.sweatshop.herekitty.ui.split.ReorderGrip
import io.sweatshop.herekitty.ui.split.ResizableSplit
import org.koin.compose.koinInject

@Composable
fun WorkspaceContent(uiModel: WorkspaceUiModel, modifier: Modifier = Modifier) {
    val root = uiModel.activeRoot ?: return
    Box(modifier.fillMaxSize()) {
        WorkspaceNodeContent(root, uiModel)
    }
}

@Composable
private fun WorkspaceNodeContent(
    node: WorkspaceNode,
    uiModel: WorkspaceUiModel,
    modifier: Modifier = Modifier,
    grip: ReorderGrip = ReorderGrip.Disabled,
) {
    when (node) {
        is WorkspaceNode.Slot -> SlotContent(node, uiModel, modifier, grip)

        is WorkspaceNode.Split -> ResizableSplit(
            items = node.children,
            key = { it.id.value },
            orientation = node.orientation,
            modifier = modifier,
            onReorder = { from, to -> uiModel.eventHandler(OnChildrenReordered(node.id, from, to)) },
        ) { child, childGrip ->
            WorkspaceNodeContent(child, uiModel, grip = childGrip)
        }
    }
}

@Composable
private fun SlotContent(
    slot: WorkspaceNode.Slot,
    uiModel: WorkspaceUiModel,
    modifier: Modifier,
    grip: ReorderGrip,
) {
    val session = slot.session
    if (session == null) {
        SourcePickerSlot(slot, uiModel, modifier)
        return
    }

    val sessionUiModel = rememberSessionUiModel(
        session = session,
        view = slot.view,
        savedViews = uiModel.savedViews,
        canCloseSession = true,
        onViewChanged = { uiModel.eventHandler(OnViewChanged(slot.id, it)) },
        onSaveView = { uiModel.eventHandler(OnViewSaved(it)) },
        onSplit = { orientation -> uiModel.eventHandler(OnSplitRequested(slot.id, orientation)) },
        onSwitchToDevice = { uiModel.eventHandler(OnDeviceChosen(slot.id, it)) },
        onSwitchToRecording = { uiModel.eventHandler(OnRecordingChosen(slot.id, it)) },
        onCloseSession = { uiModel.eventHandler(OnSlotClosed(slot.id)) },
        onNotice = { uiModel.eventHandler(WorkspaceUiModel.Event.OnNoticeRaised(it)) },
    )

    SessionContent(
        uiModel = sessionUiModel,
        viewActions = viewActionsFor(slot, uiModel),
        modifier = modifier,
        grip = grip,
    )
}

private fun viewActionsFor(slot: WorkspaceNode.Slot, uiModel: WorkspaceUiModel) = ViewActions(
    savedViews = uiModel.savedViews,
    onApply = { view -> uiModel.eventHandler(OnViewChanged(slot.id, view)) },
    onSave = { view -> uiModel.eventHandler(OnViewSaved(view)) },
    onDelete = { name -> uiModel.eventHandler(OnViewDeleted(name)) },
    onExport = { view ->
        FileDialogs.saveFile(
            title = "Export this view",
            suggestedName = view.name.ifBlank { "herekitty-view" },
            extension = ViewConfig.FILE_EXTENSION,
        )?.let { uiModel.eventHandler(OnViewExported(view, it)) }
    },
    onImport = {
        FileDialogs.openFile("Import a view", ViewConfig.FILE_EXTENSION)
            ?.let { uiModel.eventHandler(OnViewImported(slot.id, it)) }
    },
)

@Composable
private fun SourcePickerSlot(
    slot: WorkspaceNode.Slot,
    uiModel: WorkspaceUiModel,
    modifier: Modifier,
    presenter: SourcePickerPresenter = koinInject(),
) {
    val pickerUiModel = presenter.present(
        pendingView = slot.view,
        canClose = uiModel.canCloseSlots,
        onDeviceChosen = { uiModel.eventHandler(OnDeviceChosen(slot.id, it)) },
        onRecordingChosen = { uiModel.eventHandler(OnRecordingChosen(slot.id, it)) },
        onClearPendingView = { uiModel.eventHandler(OnViewChanged(slot.id, ViewConfig.Default)) },
        onClose = { uiModel.eventHandler(OnSlotClosed(slot.id)) },
    )

    SourcePickerContent(pickerUiModel, modifier)
}
