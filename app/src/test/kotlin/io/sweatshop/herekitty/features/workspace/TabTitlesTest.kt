package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.split.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

class TabTitlesTest {

    private var nextId = 0L
    private fun id() = NodeId(nextId++)

    private fun view(name: String) =
        ViewConfig(name, ViewConfig.panesRow(listOf(PaneConfig(PaneId(0L), LogFilter(tags = setOf("Radio"))))))

    private fun slot(viewName: String) = WorkspaceNode.Slot(id(), session = null, view = view(viewName))

    private fun tab(vararg viewNames: String): WorkspaceTab {
        val slots = viewNames.map { slot(it) }
        val root = if (slots.size == 1) {
            slots.single()
        } else {
            WorkspaceNode.Split(id(), SplitOrientation.Horizontal, slots)
        }
        return WorkspaceTab(TabId(nextId++), root)
    }

    @Test
    fun `a tab takes the name of the view it runs`() {
        assertEquals("new-flow", tab("new-flow").baseTitle)
    }

    @Test
    fun `an unnamed setup is a new tab`() {
        assertEquals("New tab", tab("").baseTitle)
        assertEquals("New tab", tab("   ").baseTitle)
    }

    @Test
    fun `a split tab lists the views it is running`() {
        assertEquals("flow + node-debug", tab("flow", "node-debug").baseTitle)
    }

    @Test
    fun `the same view in both halves is named once`() {
        assertEquals("flow", tab("flow", "flow").baseTitle)
    }

    @Test
    fun `unnamed halves do not dilute a named one`() {
        assertEquals("flow", tab("flow", "").baseTitle)
    }

    @Test
    fun `many views are summarised rather than run on`() {
        assertEquals("a + b +2", tab("a", "b", "c", "d").baseTitle)
    }

    /** A "1" on a tab that was already unmistakable is noise. */
    @Test
    fun `a title that stands alone is left alone`() {
        assertEquals(listOf("flow", "node-debug"), disambiguateTitles(listOf("flow", "node-debug")))
        assertEquals(listOf("New tab"), disambiguateTitles(listOf("New tab")))
    }

    @Test
    fun `repeated titles are numbered in order`() {
        assertEquals(
            listOf("New tab 1", "New tab 2", "New tab 3"),
            disambiguateTitles(listOf("New tab", "New tab", "New tab")),
        )
    }

    @Test
    fun `only the clashing titles get numbers`() {
        assertEquals(
            listOf("flow 1", "node-debug", "flow 2"),
            disambiguateTitles(listOf("flow", "node-debug", "flow")),
        )
    }

    @Test
    fun `no tabs means no titles`() {
        assertEquals(emptyList(), disambiguateTitles(emptyList()))
    }
}
