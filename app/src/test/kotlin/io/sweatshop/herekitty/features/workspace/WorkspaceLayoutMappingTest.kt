package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.workspace.model.StoredLayout
import io.sweatshop.herekitty.domain.features.workspace.model.StoredNode
import io.sweatshop.herekitty.domain.features.workspace.model.StoredTab
import io.sweatshop.herekitty.ui.split.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceLayoutMappingTest {

    private var counter = 0L
    private fun nextId(): Long = counter++

    private fun view(name: String, vararg tags: String) = ViewConfig(
        name = name,
        root = ViewConfig.panesRow(
            tags.mapIndexed { index, tag -> PaneConfig(PaneId(index.toLong()), LogFilter(tags = setOf(tag))) },
        ),
    )

    private fun slot(view: ViewConfig, serial: String? = null) =
        WorkspaceNode.Slot(NodeId(nextId()), session = null, view = view)
            .let { it to serial }

    @Test
    fun `a single slot survives the round trip with its view intact`() {
        val original = listOf(WorkspaceTab(TabId(0L), WorkspaceNode.Slot(NodeId(1L), view = view("flow", "Radio"))))

        val rebuilt = original.toStoredLayout().toWorkspaceTabs(::nextId)

        assertEquals(1, rebuilt.size)
        val restored = rebuilt.single().root as WorkspaceNode.Slot
        assertEquals("flow", restored.view.name)
        assertEquals(setOf("Radio"), restored.view.panes.single().filter.tags)
    }

    @Test
    fun `nested splits keep their shape and orientation`() {
        val nested = WorkspaceNode.Split(
            NodeId(10L),
            SplitOrientation.Vertical,
            listOf(
                WorkspaceNode.Slot(NodeId(11L), view = view("a", "A")),
                WorkspaceNode.Slot(NodeId(12L), view = view("b", "B")),
            ),
        )
        val root = WorkspaceNode.Split(
            NodeId(1L),
            SplitOrientation.Horizontal,
            listOf(WorkspaceNode.Slot(NodeId(2L), view = view("c", "C")), nested),
        )

        val rebuilt = listOf(WorkspaceTab(TabId(0L), root)).toStoredLayout().toWorkspaceTabs(::nextId)

        val outer = rebuilt.single().root as WorkspaceNode.Split
        assertEquals(SplitOrientation.Horizontal, outer.orientation)
        assertEquals(2, outer.children.size)
        val inner = outer.children[1] as WorkspaceNode.Split
        assertEquals(SplitOrientation.Vertical, inner.orientation)
        assertEquals(listOf("c", "a", "b"), outer.slots().map { it.view.name })
    }

    @Test
    fun `several tabs come back in order`() {
        val original = listOf(
            WorkspaceTab(TabId(0L), WorkspaceNode.Slot(NodeId(1L), view = view("first", "A"))),
            WorkspaceTab(TabId(1L), WorkspaceNode.Slot(NodeId(2L), view = view("second", "B"))),
        )

        val rebuilt = original.toStoredLayout().toWorkspaceTabs(::nextId)

        assertEquals(listOf("first", "second"), rebuilt.map { (it.root as WorkspaceNode.Slot).view.name })
    }

    @Test
    fun `every pane filter field is preserved`() {
        val pane = PaneConfig(
            id = PaneId(0L),
            filter = LogFilter(
                tags = setOf("/      ExampleTagImpl", "Radio"),
                query = "seq=\\d+",
                minLevel = LogLevel.WARN,
                matchCase = true,
                useRegex = true,
                excludeTags = setOf("noisy"),
            ),
            followTail = false,
        )
        val original = listOf(
            WorkspaceTab(
                TabId(0L),
                WorkspaceNode.Slot(NodeId(1L), view = ViewConfig("detail", ViewConfig.panesRow(listOf(pane)))),
            ),
        )

        val rebuilt = original.toStoredLayout().toWorkspaceTabs(::nextId)

        val restored = (rebuilt.single().root as WorkspaceNode.Slot).view.panes.single()
        assertEquals(pane.filter, restored.filter)
        assertEquals(false, restored.followTail)
    }

    /** Sessions are not persisted; adb decides what is actually available on the next launch. */
    @Test
    fun `rebuilt slots start with no session`() {
        val rebuilt = StoredLayout(
            listOf(StoredTab(StoredNode.Slot(view("x", "A"), deviceSerial = "EXAMPLE0001"))),
        ).toWorkspaceTabs(::nextId)

        assertNull((rebuilt.single().root as WorkspaceNode.Slot).session)
    }

    @Test
    fun `serials are listed in the order the slots are laid out`() {
        val layout = StoredLayout(
            listOf(
                StoredTab(
                    StoredNode.Split(
                        LayoutOrientation.Horizontal,
                        listOf(
                            StoredNode.Slot(view("a", "A"), "serial-one"),
                            StoredNode.Split(
                                LayoutOrientation.Vertical,
                                listOf(
                                    StoredNode.Slot(view("b", "B"), null),
                                    StoredNode.Slot(view("c", "C"), "serial-two"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("serial-one", null, "serial-two"), layout.serialsByPosition())
        assertEquals(3, layout.toWorkspaceTabs(::nextId).single().root.slots().size)
    }

    @Test
    fun `an empty layout produces no tabs so the caller can start fresh`() {
        assertTrue(StoredLayout.Empty.toWorkspaceTabs(::nextId).isEmpty())
        assertTrue(StoredLayout.Empty.isEmpty)
    }

    @Test
    fun `every rebuilt node gets a distinct id`() {
        val layout = StoredLayout(
            listOf(
                StoredTab(
                    StoredNode.Split(
                        LayoutOrientation.Horizontal,
                        listOf(StoredNode.Slot(view("a", "A"), null), StoredNode.Slot(view("b", "B"), null)),
                    ),
                ),
                StoredTab(StoredNode.Slot(view("c", "C"), null)),
            ),
        )

        val rebuilt = layout.toWorkspaceTabs(::nextId)

        val nodeIds = rebuilt.flatMap { tab -> tab.root.allIds() }
        assertEquals(nodeIds.size, nodeIds.distinct().size, "ids repeat: $nodeIds")
        assertEquals(rebuilt.size, rebuilt.map { it.id.value }.distinct().size)
    }

    private fun WorkspaceNode.allIds(): List<Long> = when (this) {
        is WorkspaceNode.Slot -> listOf(id.value)
        is WorkspaceNode.Split -> listOf(id.value) + children.flatMap { it.allIds() }
    }
}
