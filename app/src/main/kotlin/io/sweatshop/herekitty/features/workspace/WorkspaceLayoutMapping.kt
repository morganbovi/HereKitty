package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.workspace.model.StoredLayout
import io.sweatshop.herekitty.domain.features.workspace.model.StoredNode
import io.sweatshop.herekitty.domain.features.workspace.model.StoredTab
import io.sweatshop.herekitty.ui.split.SplitOrientation

/**
 * Converts between the live layout, which holds running sessions, and the form that survives a
 * restart, which holds only views and the serial each slot was attached to.
 */
fun List<WorkspaceTab>.toStoredLayout(): StoredLayout =
    StoredLayout(tabs = map { StoredTab(it.root.toStoredNode()) })

fun WorkspaceNode.toStoredNode(): StoredNode = when (this) {
    is WorkspaceNode.Slot -> StoredNode.Slot(
        view = view,
        deviceSerial = (session?.source?.value as? SessionSource.Device)?.device?.serial,
    )

    is WorkspaceNode.Split -> StoredNode.Split(
        orientation = orientation.toLayoutOrientation(),
        children = children.map { it.toStoredNode() },
    )
}

/**
 * Rebuilds the layout with every slot empty. Sessions are reattached afterwards, once adb has said
 * which devices are actually here.
 */
fun StoredLayout.toWorkspaceTabs(nextId: () -> Long): List<WorkspaceTab> =
    tabs.map { tab -> WorkspaceTab(TabId(nextId()), tab.root.toWorkspaceNode(nextId)) }

fun StoredNode.toWorkspaceNode(nextId: () -> Long): WorkspaceNode = when (this) {
    is StoredNode.Slot -> WorkspaceNode.Slot(id = NodeId(nextId()), session = null, view = view)

    is StoredNode.Split -> WorkspaceNode.Split(
        id = NodeId(nextId()),
        orientation = orientation.toSplitOrientation(),
        children = children.map { it.toWorkspaceNode(nextId) },
    )
}

/** Pairs each rebuilt slot with the serial it should reattach to, in layout order. */
fun StoredLayout.serialsByPosition(): List<String?> = tabs.flatMap { it.root.serialsByPosition() }

private fun StoredNode.serialsByPosition(): List<String?> = when (this) {
    is StoredNode.Slot -> listOf(deviceSerial)
    is StoredNode.Split -> children.flatMap { it.serialsByPosition() }
}

private fun SplitOrientation.toLayoutOrientation(): LayoutOrientation = when (this) {
    SplitOrientation.Horizontal -> LayoutOrientation.Horizontal
    SplitOrientation.Vertical -> LayoutOrientation.Vertical
}

private fun LayoutOrientation.toSplitOrientation(): SplitOrientation = when (this) {
    LayoutOrientation.Horizontal -> SplitOrientation.Horizontal
    LayoutOrientation.Vertical -> SplitOrientation.Vertical
}
