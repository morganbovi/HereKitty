package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.split.SplitOrientation

@JvmInline
value class NodeId(val value: Long)

/**
 * The layout of one tab as a tree, so a session can be split either beside or below another.
 *
 * A [Split] holds any number of children rather than exactly two: splitting along the axis a parent
 * already uses adds a sibling, which keeps three panes in a row as one row instead of a lopsided
 * chain of nested pairs.
 */
sealed interface WorkspaceNode {
    val id: NodeId

    /**
     * One session's place in the layout. The slot owns the [view] rather than the session owning it,
     * which is what lets the same pane setup survive switching to another device or to a recording.
     */
    data class Slot(
        override val id: NodeId,
        val session: LogSession? = null,
        val view: ViewConfig = ViewConfig.Default,
    ) : WorkspaceNode

    data class Split(
        override val id: NodeId,
        val orientation: SplitOrientation,
        val children: List<WorkspaceNode>,
    ) : WorkspaceNode
}

fun WorkspaceNode.slots(): List<WorkspaceNode.Slot> = when (this) {
    is WorkspaceNode.Slot -> listOf(this)
    is WorkspaceNode.Split -> children.flatMap { it.slots() }
}

fun WorkspaceNode.hasMultipleSlots(): Boolean = slots().size > 1

fun WorkspaceNode.updateSlot(slotId: NodeId, change: (WorkspaceNode.Slot) -> WorkspaceNode.Slot): WorkspaceNode =
    when (this) {
        is WorkspaceNode.Slot -> if (id == slotId) change(this) else this
        is WorkspaceNode.Split -> copy(children = children.map { it.updateSlot(slotId, change) })
    }

/** Rewrites every slot in the tree, keeping the structure intact. */
fun WorkspaceNode.mapSlots(change: (WorkspaceNode.Slot) -> WorkspaceNode.Slot): WorkspaceNode = when (this) {
    is WorkspaceNode.Slot -> change(this)
    is WorkspaceNode.Split -> copy(children = children.map { it.mapSlots(change) })
}

fun WorkspaceNode.splitSlot(
    slotId: NodeId,
    orientation: SplitOrientation,
    newSlot: WorkspaceNode.Slot,
    nextId: () -> NodeId,
): WorkspaceNode = when (this) {
    is WorkspaceNode.Slot ->
        if (id == slotId) WorkspaceNode.Split(nextId(), orientation, listOf(this, newSlot)) else this

    is WorkspaceNode.Split -> {
        val directIndex = children.indexOfFirst { it is WorkspaceNode.Slot && it.id == slotId }
        if (directIndex >= 0 && this.orientation == orientation) {
            copy(children = children.toMutableList().apply { add(directIndex + 1, newSlot) })
        } else {
            copy(children = children.map { it.splitSlot(slotId, orientation, newSlot, nextId) })
        }
    }
}

/** Returns null when removing [slotId] empties this subtree. Splits left with one child collapse. */
fun WorkspaceNode.withoutSlot(slotId: NodeId): WorkspaceNode? = when (this) {
    is WorkspaceNode.Slot -> if (id == slotId) null else this
    is WorkspaceNode.Split -> {
        val kept = children.mapNotNull { it.withoutSlot(slotId) }
        when (kept.size) {
            0 -> null
            1 -> kept.single()
            else -> copy(children = kept)
        }
    }
}

fun WorkspaceNode.withReorderedChildren(splitId: NodeId, from: Int, to: Int): WorkspaceNode = when (this) {
    is WorkspaceNode.Slot -> this
    is WorkspaceNode.Split ->
        if (id == splitId) {
            if (from !in children.indices || to !in children.indices) {
                this
            } else {
                copy(children = children.toMutableList().apply { add(to, removeAt(from)) })
            }
        } else {
            copy(children = children.map { it.withReorderedChildren(splitId, from, to) })
        }
}
