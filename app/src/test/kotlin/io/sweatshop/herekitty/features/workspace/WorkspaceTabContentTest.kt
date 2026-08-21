package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.split.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the last tab's cross is offered, which is what makes "close everything" reach a blank slate
 * rather than stopping one tab short of one.
 */
class WorkspaceTabContentTest {

    private var nextId = 0L
    private fun id() = NodeId(nextId++)

    private fun tab(root: WorkspaceNode) = WorkspaceTab(TabId(99L), root)

    private fun viewOf(vararg tags: String) = ViewConfig(
        name = "",
        root = ViewConfig.panesRow(
            tags.mapIndexed { index, tag ->
                PaneConfig(PaneId(index.toLong()), filter = LogFilter(tags = setOf(tag)))
            },
        ),
    )

    @Test
    fun `a fresh tab has nothing to close`() {
        assertFalse(tab(WorkspaceNode.Slot(id())).hasContent)
    }

    /** This is the case that used to look blank while still carrying a restored view. */
    @Test
    fun `a slot with no session but a set up view still counts as content`() {
        val slot = WorkspaceNode.Slot(id(), session = null, view = viewOf("Radio"))

        assertTrue(tab(slot).hasContent)
    }

    @Test
    fun `a named view counts even when its panes are the default`() {
        val slot = WorkspaceNode.Slot(id(), view = ViewConfig.Default.copy(name = "Debug"))

        assertFalse(tab(slot).hasContent, "an empty setup is empty whatever it is called")
    }

    @Test
    fun `a split counts as content even with default views`() {
        val split = WorkspaceNode.Split(
            id(),
            SplitOrientation.Horizontal,
            listOf(WorkspaceNode.Slot(id()), WorkspaceNode.Slot(id())),
        )

        assertTrue(tab(split).hasContent)
    }

    @Test
    fun `the cross is offered whenever any tab holds something`() {
        val blank = tab(WorkspaceNode.Slot(id()))
        val filled = WorkspaceTab(TabId(1L), WorkspaceNode.Slot(id(), view = viewOf("Radio")))

        assertFalse(uiModelWith(listOf(blank)).canCloseTabs)
        assertTrue(uiModelWith(listOf(filled)).canCloseTabs)
        assertTrue(uiModelWith(listOf(blank, blank)).canCloseTabs, "more than one tab is always closable")
    }

    private fun uiModelWith(tabs: List<WorkspaceTab>) = WorkspaceUiModel(
        tabs = tabs,
        activeTabId = tabs.first().id,
        savedViews = emptyList(),
        notifications = emptyList(),
        eventHandler = io.sweatshop.herekitty.ui.presenter.EventHandler {},
    )
}
