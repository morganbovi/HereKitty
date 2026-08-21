package io.sweatshop.herekitty.domain.features.views.model

/**
 * A session's panes as a tree, so three can sit stacked on the left of two more on the right.
 *
 * A [Split] holds any number of children rather than exactly two: splitting along the axis a parent
 * already uses adds a sibling, which keeps three panes in a row as one row instead of a lopsided
 * chain of nested pairs. Same shape as the workspace tree, one level down.
 *
 * A leaf is identified by its pane; splits draw ids from the same space, so no two nodes collide.
 */
sealed interface PaneNode {
    val id: PaneId

    data class Leaf(val pane: PaneConfig) : PaneNode {
        override val id: PaneId get() = pane.id
    }

    data class Split(
        override val id: PaneId,
        val orientation: LayoutOrientation,
        val children: List<PaneNode>,
    ) : PaneNode
}

fun PaneNode.leaves(): List<PaneConfig> = when (this) {
    is PaneNode.Leaf -> listOf(pane)
    is PaneNode.Split -> children.flatMap { it.leaves() }
}

fun PaneNode.nodeIds(): List<PaneId> = when (this) {
    is PaneNode.Leaf -> listOf(id)
    is PaneNode.Split -> listOf(id) + children.flatMap { it.nodeIds() }
}

/** The next free id, so a new node cannot clash with an existing pane or split. */
fun PaneNode.nextId(): PaneId = PaneId((nodeIds().maxOfOrNull { it.value } ?: -1L) + 1L)

fun PaneNode.pane(paneId: PaneId): PaneConfig? = leaves().firstOrNull { it.id == paneId }

fun PaneNode.updatePane(pane: PaneConfig): PaneNode = when (this) {
    is PaneNode.Leaf -> if (id == pane.id) PaneNode.Leaf(pane) else this
    is PaneNode.Split -> copy(children = children.map { it.updatePane(pane) })
}

/**
 * Whether two trees describe the same arrangement, ignoring node ids — those are minted fresh
 * whenever a view is loaded, so comparing them would call every saved view modified.
 */
fun PaneNode.describesSameAs(other: PaneNode): Boolean = when {
    this is PaneNode.Leaf && other is PaneNode.Leaf ->
        pane.filter == other.pane.filter &&
            pane.followTail == other.pane.followTail &&
            pane.collapseDuplicates == other.pane.collapseDuplicates

    this is PaneNode.Split && other is PaneNode.Split ->
        orientation == other.orientation &&
            children.size == other.children.size &&
            children.indices.all { children[it].describesSameAs(other.children[it]) }

    else -> false
}
