package io.sweatshop.herekitty.features.session

import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.leaves
import io.sweatshop.herekitty.domain.features.views.model.nextId
import io.sweatshop.herekitty.domain.features.views.model.updatePane

/** Which side of the pane being split the new one lands on. */
enum class SplitSide {
    Before,
    After,
}

/** Appends a pane to the outermost row, which is what the session's add button does. */
internal fun addPane(root: PaneNode): PaneNode {
    val newLeaf = PaneNode.Leaf(PaneConfig(root.nextId()))

    return when {
        root is PaneNode.Split && root.orientation == LayoutOrientation.Horizontal ->
            root.copy(children = root.children + newLeaf)

        else -> PaneNode.Split(
            id = PaneId(root.nextId().value + 1L),
            orientation = LayoutOrientation.Horizontal,
            children = listOf(root, newLeaf),
        )
    }
}

/** Returns null when removing [paneId] empties the tree. Splits left with one child collapse. */
internal fun closePane(root: PaneNode, paneId: PaneId): PaneNode? = when (root) {
    is PaneNode.Leaf -> if (root.id == paneId) null else root
    is PaneNode.Split -> {
        val kept = root.children.mapNotNull { closePane(it, paneId) }
        when (kept.size) {
            0 -> null
            1 -> kept.single()
            else -> root.copy(children = kept)
        }
    }
}

/**
 * Gives [tag] a pane of its own beside [targetId], taking it out of [sourceId] on the way.
 *
 * The tag leaves its old pane *first*, so the new pane cannot be placed beside a pane that the
 * departure collapsed away. Aiming it at its own pane is the ordinary case of this, not a special one:
 * the pane keeps its other tags and the extracted one appears beside it.
 */
internal fun splitTagOnto(
    root: PaneNode,
    tag: String,
    sourceId: PaneId,
    targetId: PaneId,
    orientation: LayoutOrientation,
    side: SplitSide,
): PaneNode {
    val leaves = root.leaves()
    val source = leaves.firstOrNull { it.id == sourceId } ?: return root
    if (leaves.none { it.id == targetId }) return root
    if (tag !in source.filter.tags) return root

    val extracted = source.filter.copy(tags = setOf(tag))

    val remaining = if (source.filter.tags.size == 1) {
        closePane(root, sourceId) ?: return root
    } else {
        root.updatePane(source.copy(filter = source.filter.copy(tags = source.filter.tags - tag)))
    }

    val target = remaining.leaves().firstOrNull { it.id == targetId } ?: return root
    val addition = PaneNode.Leaf(source.copy(id = remaining.nextId(), filter = extracted))

    return remaining.insertBeside(targetId, PaneNode.Leaf(target), addition, orientation, side) {
        PaneId(remaining.nextId().value + 1L)
    }
}

/** Splitting a tag out of the pane it is already in is the same move aimed at itself. */
internal fun splitTagOut(
    root: PaneNode,
    paneId: PaneId,
    tag: String,
    orientation: LayoutOrientation,
    side: SplitSide,
): PaneNode = splitTagOnto(root, tag, sourceId = paneId, targetId = paneId, orientation, side)

/** Adds an unfiltered pane beside [paneId], in the direction asked for. */
internal fun splitPane(
    root: PaneNode,
    paneId: PaneId,
    orientation: LayoutOrientation,
    side: SplitSide,
): PaneNode {
    val existing = root.leaves().firstOrNull { it.id == paneId } ?: return root
    val fresh = PaneNode.Leaf(PaneConfig(root.nextId()))

    return root.insertBeside(paneId, PaneNode.Leaf(existing), fresh, orientation, side) {
        PaneId(root.nextId().value + 1L)
    }
}

internal fun movePane(
    root: PaneNode,
    sourceId: PaneId,
    targetId: PaneId,
    orientation: LayoutOrientation,
    side: SplitSide,
): PaneNode {
    if (sourceId == targetId) return root

    val source = root.leaves().firstOrNull { it.id == sourceId } ?: return root
    if (root.leaves().none { it.id == targetId }) return root

    val without = closePane(root, sourceId) ?: return root
    // The target may have been the sibling that got collapsed away with the source's old split.
    if (without.leaves().none { it.id == targetId }) return root

    val target = without.leaves().first { it.id == targetId }
    return without.insertBeside(targetId, PaneNode.Leaf(target), PaneNode.Leaf(source), orientation, side) {
        PaneId(without.nextId().value + 1L)
    }
}

/** Exchanges two panes' positions, which is what dropping onto the middle of a pane means. */
internal fun swapPanes(root: PaneNode, first: PaneId, second: PaneId): PaneNode {
    if (first == second) return root

    val leaves = root.leaves()
    val a = leaves.firstOrNull { it.id == first } ?: return root
    val b = leaves.firstOrNull { it.id == second } ?: return root

    return root.mapLeaves { pane ->
        when (pane.id) {
            first -> b
            second -> a
            else -> pane
        }
    }
}

private fun PaneNode.mapLeaves(change: (PaneConfig) -> PaneConfig): PaneNode = when (this) {
    is PaneNode.Leaf -> PaneNode.Leaf(change(pane))
    is PaneNode.Split -> copy(children = children.map { it.mapLeaves(change) })
}

/**
 * Moves [tag] out of one pane's filter and into another's, which is what dragging a tag chip across
 * panes amounts to.
 *
 * A pane whose only tag is dragged away has nothing left to watch, and an empty tag set means *every*
 * tag — so rather than turn into a firehose it closes, exactly as merging it away would.
 */
internal fun moveTag(root: PaneNode, tag: String, sourceId: PaneId, targetId: PaneId): PaneNode {
    if (sourceId == targetId) return root

    val leaves = root.leaves()
    val source = leaves.firstOrNull { it.id == sourceId } ?: return root
    val target = leaves.firstOrNull { it.id == targetId } ?: return root
    if (tag !in source.filter.tags) return root

    val gained = target.copy(filter = target.filter.copy(tags = target.filter.tags + tag))

    if (source.filter.tags.size == 1) {
        val without = closePane(root, sourceId) ?: return root
        return without.updatePane(gained)
    }

    return root
        .updatePane(source.copy(filter = source.filter.copy(tags = source.filter.tags - tag)))
        .updatePane(gained)
}

/**
 * Folds one pane's tags into another and closes it.
 *
 * The target keeps its own query, since a merge is "watch these as well" rather than a new filter.
 */
internal fun mergePaneInto(root: PaneNode, sourceId: PaneId, targetId: PaneId): PaneNode {
    if (sourceId == targetId) return root

    val leaves = root.leaves()
    val source = leaves.firstOrNull { it.id == sourceId } ?: return root
    val target = leaves.firstOrNull { it.id == targetId } ?: return root

    // No tags means every tag, so merging one in cannot narrow the result.
    val tags = if (source.filter.tags.isEmpty() || target.filter.tags.isEmpty()) {
        emptySet()
    } else {
        target.filter.tags + source.filter.tags
    }

    val without = closePane(root, sourceId) ?: return root
    return without.updatePane(target.copy(filter = target.filter.copy(tags = tags)))
}

/**
 * Replaces the leaf [targetId] with [replacement] and puts [addition] beside it.
 *
 * Splitting along an axis a parent already uses adds a sibling to it rather than nesting a new pair,
 * which is what keeps three panes in a row one row instead of a lopsided chain.
 */
private fun PaneNode.insertBeside(
    targetId: PaneId,
    replacement: PaneNode,
    addition: PaneNode,
    orientation: LayoutOrientation,
    side: SplitSide,
    nextSplitId: () -> PaneId,
): PaneNode = when (this) {
    is PaneNode.Leaf ->
        if (id != targetId) {
            this
        } else {
            val ordered =
                if (side == SplitSide.Before) listOf(addition, replacement) else listOf(replacement, addition)
            PaneNode.Split(nextSplitId(), orientation, ordered)
        }

    is PaneNode.Split -> {
        val directIndex = children.indexOfFirst { it is PaneNode.Leaf && it.id == targetId }
        if (directIndex >= 0 && this.orientation == orientation) {
            val insertAt = if (side == SplitSide.Before) directIndex else directIndex + 1
            copy(
                children = children.toMutableList().apply {
                    this[directIndex] = replacement
                    add(insertAt, addition)
                },
            )
        } else {
            copy(
                children = children.map {
                    it.insertBeside(targetId, replacement, addition, orientation, side, nextSplitId)
                },
            )
        }
    }
}
