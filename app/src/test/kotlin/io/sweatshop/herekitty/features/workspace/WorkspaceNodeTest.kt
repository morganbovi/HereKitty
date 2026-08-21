package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.ui.split.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceNodeTest {

    private var counter = 100L
    private fun nextId(): NodeId = NodeId(counter++)

    private fun slot(id: Long) = WorkspaceNode.Slot(NodeId(id))

    private fun WorkspaceNode.slotIds() = slots().map { it.id.value }

    @Test
    fun `splitting the only slot wraps it in a split of two`() {
        val root = slot(1).splitSlot(NodeId(1), SplitOrientation.Horizontal, slot(2), ::nextId)

        val split = root as WorkspaceNode.Split
        assertEquals(SplitOrientation.Horizontal, split.orientation)
        assertEquals(listOf(1L, 2L), root.slotIds())
    }

    /** Three panes in a row should stay one row, not a lopsided chain of nested pairs. */
    @Test
    fun `splitting along the axis a parent already uses adds a sibling`() {
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2)))

        val result = row.splitSlot(NodeId(1), SplitOrientation.Horizontal, slot(3), ::nextId) as WorkspaceNode.Split

        assertEquals(3, result.children.size)
        assertTrue(result.children.all { it is WorkspaceNode.Slot })
        assertEquals(listOf(1L, 3L, 2L), result.slotIds())
    }

    @Test
    fun `splitting across the parent's axis nests only the target`() {
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2)))

        val result = row.splitSlot(NodeId(2), SplitOrientation.Vertical, slot(3), ::nextId) as WorkspaceNode.Split

        assertEquals(2, result.children.size)
        assertEquals(SplitOrientation.Horizontal, result.orientation)
        val nested = result.children[1] as WorkspaceNode.Split
        assertEquals(SplitOrientation.Vertical, nested.orientation)
        assertEquals(listOf(1L, 2L, 3L), result.slotIds())
    }

    @Test
    fun `a new slot lands immediately after the one it was split from`() {
        val row = WorkspaceNode.Split(
            NodeId(0),
            SplitOrientation.Horizontal,
            listOf(slot(1), slot(2), slot(3)),
        )

        val result = row.splitSlot(NodeId(2), SplitOrientation.Horizontal, slot(9), ::nextId)

        assertEquals(listOf(1L, 2L, 9L, 3L), result.slotIds())
    }

    @Test
    fun `closing a slot collapses a split that is left with one child`() {
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2)))

        val result = row.withoutSlot(NodeId(2))

        assertEquals(slot(1), result)
    }

    @Test
    fun `closing a slot leaves a split of three as a split of two`() {
        val row = WorkspaceNode.Split(
            NodeId(0),
            SplitOrientation.Horizontal,
            listOf(slot(1), slot(2), slot(3)),
        )

        val result = row.withoutSlot(NodeId(2)) as WorkspaceNode.Split

        assertEquals(listOf(1L, 3L), result.slotIds())
    }

    @Test
    fun `closing the last slot leaves nothing for the caller to replace`() {
        assertNull(slot(1).withoutSlot(NodeId(1)))
    }

    @Test
    fun `closing a slot collapses nested splits from the inside out`() {
        val nested = WorkspaceNode.Split(NodeId(10), SplitOrientation.Vertical, listOf(slot(2), slot(3)))
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), nested))

        val result = row.withoutSlot(NodeId(3)) as WorkspaceNode.Split

        assertEquals(listOf(1L, 2L), result.slotIds())
        assertTrue(result.children.all { it is WorkspaceNode.Slot }, "the emptied split should be gone")
    }

    @Test
    fun `closing an unknown slot changes nothing`() {
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2)))

        assertEquals(row, row.withoutSlot(NodeId(99)))
    }

    @Test
    fun `slots are listed in the order they are laid out`() {
        val nested = WorkspaceNode.Split(NodeId(10), SplitOrientation.Vertical, listOf(slot(2), slot(3)))
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), nested, slot(4)))

        assertEquals(listOf(1L, 2L, 3L, 4L), row.slotIds())
    }

    @Test
    fun `a single slot reports that it cannot be closed`() {
        assertTrue(!slot(1).hasMultipleSlots())
        assertTrue(WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2))).hasMultipleSlots())
    }

    @Test
    fun `reordering moves a child to its new position`() {
        val row = WorkspaceNode.Split(
            NodeId(0),
            SplitOrientation.Horizontal,
            listOf(slot(1), slot(2), slot(3)),
        )

        assertEquals(listOf(2L, 1L, 3L), row.withReorderedChildren(NodeId(0), from = 0, to = 1).slotIds())
        assertEquals(listOf(3L, 1L, 2L), row.withReorderedChildren(NodeId(0), from = 2, to = 0).slotIds())
    }

    @Test
    fun `reordering reaches a nested split`() {
        val nested = WorkspaceNode.Split(NodeId(10), SplitOrientation.Vertical, listOf(slot(2), slot(3)))
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), nested))

        assertEquals(listOf(1L, 3L, 2L), row.withReorderedChildren(NodeId(10), 0, 1).slotIds())
    }

    @Test
    fun `an out of range reorder is ignored`() {
        val row = WorkspaceNode.Split(NodeId(0), SplitOrientation.Horizontal, listOf(slot(1), slot(2)))

        assertEquals(row, row.withReorderedChildren(NodeId(0), from = 0, to = 5))
    }
}
