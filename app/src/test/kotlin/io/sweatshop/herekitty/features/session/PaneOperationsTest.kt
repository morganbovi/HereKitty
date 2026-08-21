package io.sweatshop.herekitty.features.session

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.leaves
import io.sweatshop.herekitty.domain.features.views.model.nodeIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaneOperationsTest {

    private fun pane(id: Long, vararg tags: String, query: String = "") =
        PaneConfig(PaneId(id), LogFilter(tags = tags.toSet(), query = query))

    private fun leaf(id: Long, vararg tags: String, query: String = "") =
        PaneNode.Leaf(pane(id, *tags, query = query))

    private fun row(id: Long, vararg children: PaneNode) =
        PaneNode.Split(PaneId(id), LayoutOrientation.Horizontal, children.toList())

    private fun column(id: Long, vararg children: PaneNode) =
        PaneNode.Split(PaneId(id), LayoutOrientation.Vertical, children.toList())

    private fun PaneNode.tagSets() = leaves().map { it.filter.tags }

    private fun PaneNode.shape(): String = when (this) {
        is PaneNode.Leaf -> pane.filter.tags.sorted().joinToString("+").ifEmpty { "all" }
        is PaneNode.Split -> {
            val separator = if (orientation == LayoutOrientation.Horizontal) " | " else " / "
            "(" + children.joinToString(separator) { it.shape() } + ")"
        }
    }

    @Test
    fun `adding a pane to a single one makes a row of two`() {
        assertEquals("(Radio | all)", addPane(leaf(0L, "Radio")).shape())
    }

    @Test
    fun `adding a pane to a row extends it rather than nesting`() {
        val result = addPane(row(9L, leaf(0L, "A"), leaf(1L, "B")))

        assertEquals("(A | B | all)", result.shape())
    }

    /** Adding to a column has to nest, since the new pane belongs beside it, not in it. */
    @Test
    fun `adding a pane to a column wraps it in a row`() {
        val result = addPane(column(9L, leaf(0L, "A"), leaf(1L, "B")))

        assertEquals("((A / B) | all)", result.shape())
    }

    @Test
    fun `splitting a tag sideways puts it in a new pane beside its own`() {
        val root = leaf(0L, "Radio", "Outcome")

        val after = splitTagOut(root, PaneId(0L), "Outcome", LayoutOrientation.Horizontal, SplitSide.After)
        val before = splitTagOut(root, PaneId(0L), "Outcome", LayoutOrientation.Horizontal, SplitSide.Before)

        assertEquals("(Radio | Outcome)", after.shape())
        assertEquals("(Outcome | Radio)", before.shape())
    }

    @Test
    fun `splitting a tag downwards stacks it instead`() {
        val result = splitTagOut(leaf(0L, "Radio", "Outcome"), PaneId(0L), "Outcome", LayoutOrientation.Vertical, SplitSide.After)

        assertEquals("(Radio / Outcome)", result.shape())
    }

    /** This is the arrangement asked for: a stack on one side of a stack on the other. */
    @Test
    fun `splits compose into a stack beside a stack`() {
        var root: PaneNode = leaf(0L, "A", "B", "C", "D", "E")
        root = splitTagOut(root, PaneId(0L), "B", LayoutOrientation.Vertical, SplitSide.After)
        root = splitTagOut(root, PaneId(0L), "C", LayoutOrientation.Vertical, SplitSide.After)
        root = splitTagOut(root, PaneId(0L), "D", LayoutOrientation.Horizontal, SplitSide.After)
        root = splitTagOut(root, PaneId(0L), "E", LayoutOrientation.Vertical, SplitSide.After)

        // A and E stacked, beside D, all of it stacked above C and B.
        assertEquals("(((A / E) | D) / C / B)", root.shape())
        assertEquals(5, root.leaves().size)
    }

    @Test
    fun `splitting along the axis a parent already uses adds a sibling`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        val result = splitTagOut(root, PaneId(0L), "B", LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals("(A | B | C)", result.shape())
    }

    @Test
    fun `a pane with one tag cannot be split`() {
        val root = leaf(0L, "Radio")

        assertEquals(root, splitTagOut(root, PaneId(0L), "Radio", LayoutOrientation.Horizontal, SplitSide.After))
    }

    @Test
    fun `splitting a tag the pane does not watch changes nothing`() {
        val root = leaf(0L, "Radio", "Outcome")

        assertEquals(root, splitTagOut(root, PaneId(0L), "Elsewhere", LayoutOrientation.Horizontal, SplitSide.After))
        assertEquals(root, splitTagOut(root, PaneId(7L), "Radio", LayoutOrientation.Horizontal, SplitSide.After))
    }

    @Test
    fun `the new pane inherits everything but the tags`() {
        val source = PaneConfig(
            id = PaneId(0L),
            filter = LogFilter(
                tags = setOf("Radio", "Outcome"),
                query = "timeout",
                minLevel = LogLevel.WARN,
                matchCase = true,
            ),
            followTail = false,
            collapseDuplicates = true,
        )

        val result = splitTagOut(PaneNode.Leaf(source), PaneId(0L), "Outcome", LayoutOrientation.Horizontal, SplitSide.After)

        val extracted = result.leaves().first { it.filter.tags == setOf("Outcome") }
        assertEquals("timeout", extracted.filter.query)
        assertEquals(LogLevel.WARN, extracted.filter.minLevel)
        assertTrue(extracted.filter.matchCase)
        assertEquals(false, extracted.followTail)
        assertTrue(extracted.collapseDuplicates)
    }

    @Test
    fun `every node keeps a distinct id after splitting`() {
        var root: PaneNode = leaf(0L, "A", "B", "C")
        root = splitTagOut(root, PaneId(0L), "B", LayoutOrientation.Horizontal, SplitSide.After)
        root = splitTagOut(root, PaneId(0L), "C", LayoutOrientation.Vertical, SplitSide.After)

        val ids = root.nodeIds()
        assertEquals(ids.size, ids.distinct().size, "ids repeat: $ids")
    }

    @Test
    fun `splitting an empty pane adds an unfiltered one beside it`() {
        val result = splitPane(leaf(0L, "Radio"), PaneId(0L), LayoutOrientation.Vertical, SplitSide.After)

        assertEquals("(Radio / all)", result.shape())
    }

    @Test
    fun `closing a pane collapses a split left with one child`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        assertEquals("A", closePane(root, PaneId(1L))?.shape())
    }

    @Test
    fun `closing a pane collapses nested splits from the inside out`() {
        val root = row(9L, leaf(0L, "A"), column(8L, leaf(1L, "B"), leaf(2L, "C")))

        assertEquals("(A | B)", closePane(root, PaneId(2L))?.shape())
    }

    @Test
    fun `closing the last pane leaves nothing for the caller to replace`() {
        assertNull(closePane(leaf(0L, "A"), PaneId(0L)))
    }

    @Test
    fun `merging folds the source's tags into the target and drops the source`() {
        val root = row(9L, leaf(0L, "Radio"), leaf(1L, "Outcome"))

        val result = mergePaneInto(root, sourceId = PaneId(1L), targetId = PaneId(0L))

        assertEquals(listOf(setOf("Radio", "Outcome")), result.tagSets())
        assertEquals(1, result.leaves().size)
    }

    @Test
    fun `the target keeps its own query and the source's is dropped`() {
        val root = row(9L, leaf(0L, "Radio", query = "keep me"), leaf(1L, "Outcome", query = "lose me"))

        val result = mergePaneInto(root, sourceId = PaneId(1L), targetId = PaneId(0L))

        assertEquals("keep me", result.leaves().single().filter.query)
    }

    /** An empty tag set means every tag, so a merge must not accidentally narrow it. */
    @Test
    fun `merging with an unfiltered pane leaves it unfiltered`() {
        val intoAll = mergePaneInto(row(9L, leaf(0L), leaf(1L, "Outcome")), PaneId(1L), PaneId(0L))
        assertEquals(listOf(emptySet()), intoAll.tagSets())

        val fromAll = mergePaneInto(row(9L, leaf(0L, "Radio"), leaf(1L)), PaneId(1L), PaneId(0L))
        assertEquals(listOf(emptySet()), fromAll.tagSets())
    }

    @Test
    fun `merging across a nested split keeps the rest of the shape`() {
        val root = row(9L, leaf(0L, "A"), column(8L, leaf(1L, "B"), leaf(2L, "C")))

        val result = mergePaneInto(root, sourceId = PaneId(1L), targetId = PaneId(0L))

        assertEquals("(A+B | C)", result.shape())
        assertEquals(setOf("A", "B"), result.leaves().first().filter.tags)
    }

    @Test
    fun `merging a pane into itself, or an unknown pane, changes nothing`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        assertEquals(root, mergePaneInto(root, PaneId(0L), PaneId(0L)))
        assertEquals(root, mergePaneInto(root, PaneId(7L), PaneId(0L)))
        assertEquals(root, mergePaneInto(root, PaneId(0L), PaneId(7L)))
    }




    @Test
    fun `moving a pane puts it beside the target`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"), leaf(2L, "C"))

        val result = movePane(root, sourceId = PaneId(0L), targetId = PaneId(2L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals("(B | C | A)", result.shape())
    }

    @Test
    fun `moving a pane across the axis stacks it on the target`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        val result = movePane(root, PaneId(0L), PaneId(1L), LayoutOrientation.Vertical, SplitSide.After)

        assertEquals("(B / A)", result.shape())
    }

    /** Taking the source out can collapse the split its target lived in; that must not lose the target. */
    @Test
    fun `moving between the last two panes is refused rather than losing one`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        val result = movePane(root, PaneId(0L), PaneId(1L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals(2, result.leaves().size)
    }

    @Test
    fun `moving a pane out of a nested split collapses what it leaves behind`() {
        val root = row(9L, leaf(0L, "A"), column(8L, leaf(1L, "B"), leaf(2L, "C")))

        val result = movePane(root, PaneId(1L), PaneId(0L), LayoutOrientation.Vertical, SplitSide.After)

        assertEquals("((A / B) | C)", result.shape())
    }

    @Test
    fun `moving onto itself or an unknown pane changes nothing`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        assertEquals(root, movePane(root, PaneId(0L), PaneId(0L), LayoutOrientation.Horizontal, SplitSide.After))
        assertEquals(root, movePane(root, PaneId(7L), PaneId(0L), LayoutOrientation.Horizontal, SplitSide.After))
        assertEquals(root, movePane(root, PaneId(0L), PaneId(7L), LayoutOrientation.Horizontal, SplitSide.After))
    }

    @Test
    fun `swapping exchanges two panes and leaves the shape alone`() {
        val root = row(9L, leaf(0L, "A"), column(8L, leaf(1L, "B"), leaf(2L, "C")))

        val result = swapPanes(root, PaneId(0L), PaneId(2L))

        assertEquals("(C | (B / A))", result.shape())
    }

    @Test
    fun `swapping a pane with itself or an unknown pane changes nothing`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "B"))

        assertEquals(root, swapPanes(root, PaneId(0L), PaneId(0L)))
        assertEquals(root, swapPanes(root, PaneId(0L), PaneId(7L)))
    }

    @Test
    fun `dragging a tag to another pane moves it there`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        val result = moveTag(root, tag = "B", sourceId = PaneId(0L), targetId = PaneId(1L))

        assertEquals(listOf(setOf("A"), setOf("C", "B")), result.tagSets())
    }

    /** Its only tag gone, the source pane would show everything — so it closes instead. */
    @Test
    fun `dragging a pane's last tag away closes that pane`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "C"))

        val result = moveTag(root, tag = "A", sourceId = PaneId(0L), targetId = PaneId(1L))

        assertEquals("A+C", result.shape())
        assertEquals(1, result.leaves().size)
    }

    @Test
    fun `a tag the target already watches still leaves the source`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "B"))

        val result = moveTag(root, tag = "B", sourceId = PaneId(0L), targetId = PaneId(1L))

        assertEquals(listOf(setOf("A"), setOf("B")), result.tagSets())
    }

    @Test
    fun `dropping a tag on an unfiltered pane pins it to that tag`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L))

        val result = moveTag(root, tag = "B", sourceId = PaneId(0L), targetId = PaneId(1L))

        assertEquals(listOf(setOf("A"), setOf("B")), result.tagSets())
    }

    @Test
    fun `moving a tag out of a nested split collapses what it leaves behind`() {
        val root = row(9L, leaf(0L, "A"), column(8L, leaf(1L, "B"), leaf(2L, "C")))

        val result = moveTag(root, tag = "B", sourceId = PaneId(1L), targetId = PaneId(0L))

        assertEquals("(A+B | C)", result.shape())
    }

    @Test
    fun `a tag the source does not have, or a pane that is not there, changes nothing`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "C"))

        assertEquals(root, moveTag(root, "Z", PaneId(0L), PaneId(1L)))
        assertEquals(root, moveTag(root, "A", PaneId(0L), PaneId(0L)))
        assertEquals(root, moveTag(root, "A", PaneId(7L), PaneId(1L)))
        assertEquals(root, moveTag(root, "A", PaneId(0L), PaneId(7L)))
    }

    @Test
    fun `dropping a tag on another pane's edge gives it a pane there`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        val result = splitTagOnto(root, "B", PaneId(0L), PaneId(1L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals("(A | C | B)", result.shape())
    }

    @Test
    fun `an edge drop across the axis stacks the new pane on the target`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        val result = splitTagOnto(root, "B", PaneId(0L), PaneId(1L), LayoutOrientation.Vertical, SplitSide.Before)

        assertEquals("(A | (B / C))", result.shape())
    }

    /** Aiming a tag at its own pane's edge is the plain split, not a special case. */
    @Test
    fun `dropping a tag on its own pane's edge splits it out beside itself`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        val result = splitTagOnto(root, "B", PaneId(0L), PaneId(0L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals("(A | B | C)", result.shape())
    }

    /** The source pane goes when its last tag leaves, so the target must survive that collapse. */
    @Test
    fun `an edge drop of a pane's last tag closes the pane it came from`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "C"))

        val result = splitTagOnto(root, "A", PaneId(0L), PaneId(1L), LayoutOrientation.Vertical, SplitSide.After)

        assertEquals("(C / A)", result.shape())
        assertEquals(2, result.leaves().size)
    }

    @Test
    fun `splitting a lone tag onto its own pane changes nothing`() {
        val root = row(9L, leaf(0L, "A"), leaf(1L, "C"))

        val result = splitTagOnto(root, "A", PaneId(0L), PaneId(0L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals(root, result)
    }

    @Test
    fun `an edge drop keeps the rest of the source pane's filter`() {
        val root = row(9L, leaf(0L, "A", "B", query = "boom"), leaf(1L, "C"))

        val result = splitTagOnto(root, "B", PaneId(0L), PaneId(1L), LayoutOrientation.Horizontal, SplitSide.After)

        assertEquals("boom", result.leaves().single { it.filter.tags == setOf("B") }.filter.query)
    }

    @Test
    fun `an unknown tag or pane leaves an edge drop alone`() {
        val root = row(9L, leaf(0L, "A", "B"), leaf(1L, "C"))

        assertEquals(root, splitTagOnto(root, "Z", PaneId(0L), PaneId(1L), LayoutOrientation.Horizontal, SplitSide.After))
        assertEquals(root, splitTagOnto(root, "B", PaneId(7L), PaneId(1L), LayoutOrientation.Horizontal, SplitSide.After))
        assertEquals(root, splitTagOnto(root, "B", PaneId(0L), PaneId(7L), LayoutOrientation.Horizontal, SplitSide.After))
    }
}
