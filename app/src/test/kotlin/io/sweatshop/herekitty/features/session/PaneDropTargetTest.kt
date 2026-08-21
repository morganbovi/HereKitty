package io.sweatshop.herekitty.features.session

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaneDropTargetTest {

    private val left = PaneId(0L)
    private val right = PaneId(1L)
    private val dragged = PaneId(9L)

    // Two panes side by side, each 200 wide and 100 tall.
    private val bounds = mapOf(
        left to Rect(0f, 0f, 200f, 100f),
        right to Rect(200f, 0f, 400f, 100f),
    )

    private fun regionAt(x: Float, y: Float) = dropTargetAt(bounds, Offset(x, y), dragged)

    @Test
    fun `the middle of a pane swaps rather than splits`() {
        assertEquals(PaneDropTarget(left, PaneDropRegion.Centre), regionAt(100f, 50f))
    }

    @Test
    fun `each edge band offers its own side`() {
        assertEquals(PaneDropTarget(left, PaneDropRegion.Left), regionAt(10f, 50f))
        assertEquals(PaneDropTarget(left, PaneDropRegion.Right), regionAt(190f, 50f))
        assertEquals(PaneDropTarget(left, PaneDropRegion.Top), regionAt(100f, 5f))
        assertEquals(PaneDropTarget(left, PaneDropRegion.Bottom), regionAt(100f, 95f))
    }

    @Test
    fun `the pane under the pointer is the one that answers`() {
        assertEquals(PaneDropTarget(right, PaneDropRegion.Left), regionAt(210f, 50f))
        assertEquals(PaneDropTarget(right, PaneDropRegion.Centre), regionAt(300f, 50f))
    }

    /** Dropping a pane on itself is not a move, so it offers nothing. */
    @Test
    fun `the pane being dragged is not a target`() {
        val withDragged = bounds + (dragged to Rect(0f, 0f, 200f, 100f))

        assertEquals(right, dropTargetAt(withDragged, Offset(300f, 50f), dragged)?.paneId)
        assertNull(dropTargetAt(mapOf(dragged to Rect(0f, 0f, 200f, 100f)), Offset(100f, 50f), dragged))
    }

    @Test
    fun `a pointer outside every pane has no target`() {
        assertNull(regionAt(500f, 50f))
        assertNull(regionAt(100f, 500f))
    }

    /** Bands are proportional, so a tall narrow pane still offers left and right. */
    @Test
    fun `edges stay reachable whatever the pane's shape`() {
        val tall = mapOf(left to Rect(0f, 0f, 40f, 800f))

        assertEquals(PaneDropRegion.Left, dropTargetAt(tall, Offset(4f, 400f), dragged)?.region)
        assertEquals(PaneDropRegion.Right, dropTargetAt(tall, Offset(36f, 400f), dragged)?.region)
        assertEquals(PaneDropRegion.Centre, dropTargetAt(tall, Offset(20f, 400f), dragged)?.region)
    }

    @Test
    fun `a collapsed pane is not a target`() {
        assertNull(dropTargetAt(mapOf(left to Rect(0f, 0f, 0f, 0f)), Offset(0f, 0f), dragged))
    }

    @Test
    fun `each region maps to the split it would create`() {
        assertEquals(LayoutOrientation.Horizontal, PaneDropRegion.Left.orientation)
        assertEquals(SplitSide.Before, PaneDropRegion.Left.side)
        assertEquals(LayoutOrientation.Horizontal, PaneDropRegion.Right.orientation)
        assertEquals(SplitSide.After, PaneDropRegion.Right.side)
        assertEquals(LayoutOrientation.Vertical, PaneDropRegion.Top.orientation)
        assertEquals(SplitSide.Before, PaneDropRegion.Top.side)
        assertEquals(LayoutOrientation.Vertical, PaneDropRegion.Bottom.orientation)
        assertEquals(SplitSide.After, PaneDropRegion.Bottom.side)
        assertNull(PaneDropRegion.Centre.orientation)
        assertNull(PaneDropRegion.Centre.side)
    }

    @Test
    fun `the preview covers the half a drop would take`() {
        val pane = Rect(0f, 0f, 200f, 100f)

        assertEquals(Rect(0f, 0f, 100f, 100f), PaneDropRegion.Left.previewIn(pane))
        assertEquals(Rect(100f, 0f, 200f, 100f), PaneDropRegion.Right.previewIn(pane))
        assertEquals(Rect(0f, 0f, 200f, 50f), PaneDropRegion.Top.previewIn(pane))
        assertEquals(Rect(0f, 50f, 200f, 100f), PaneDropRegion.Bottom.previewIn(pane))
        assertEquals(pane, PaneDropRegion.Centre.previewIn(pane))
    }

    /** A tag excludes nothing: the edge of its own pane is where you send it to get a pane of its own. */
    @Test
    fun `excluding nothing offers the pane the drag came from`() {
        assertEquals(
            PaneDropTarget(left, PaneDropRegion.Right),
            dropTargetAt(bounds, Offset(190f, 50f), excluding = null),
        )
        assertNull(dropTargetAt(bounds, Offset(190f, 50f), excluding = left))
    }
}
