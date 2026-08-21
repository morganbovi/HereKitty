package io.sweatshop.herekitty.domain.features.views

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewConfigTest {

    private fun view(name: String, vararg panes: PaneConfig) =
        ViewConfig(name, ViewConfig.panesRow(panes.toList()))

    private fun pane(id: Long, tag: String, query: String = "", followTail: Boolean = true) =
        PaneConfig(PaneId(id), LogFilter(tags = setOf(tag), query = query), followTail)

    /** A view reloaded from disk gets new pane ids; that must not read as an edit. */
    @Test
    fun `two views match when only their pane ids differ`() {
        val saved = view("flow", pane(0L, "Radio"), pane(1L, "Outcome"))
        val live = view("flow", pane(77L, "Radio"), pane(78L, "Outcome"))

        assertTrue(live.hasSameSetupAs(saved))
    }

    @Test
    fun `the name is not part of the setup`() {
        assertTrue(view("one", pane(0L, "Radio")).hasSameSetupAs(view("two", pane(0L, "Radio"))))
    }

    @Test
    fun `a changed filter makes them differ`() {
        val saved = view("flow", pane(0L, "Radio"))

        assertFalse(view("flow", pane(0L, "Outcome")).hasSameSetupAs(saved))
        assertFalse(view("flow", pane(0L, "Radio", query = "timeout")).hasSameSetupAs(saved))
    }

    @Test
    fun `adding or removing a pane makes them differ`() {
        val saved = view("flow", pane(0L, "Radio"))

        assertFalse(view("flow", pane(0L, "Radio"), pane(1L, "Outcome")).hasSameSetupAs(saved))
        assertFalse(view("flow").hasSameSetupAs(saved))
    }

    @Test
    fun `reordering panes makes them differ`() {
        val saved = view("flow", pane(0L, "Radio"), pane(1L, "Outcome"))
        val reordered = view("flow", pane(0L, "Outcome"), pane(1L, "Radio"))

        assertFalse(reordered.hasSameSetupAs(saved))
    }

    @Test
    fun `following the tail is part of the setup`() {
        val saved = view("flow", pane(0L, "Radio", followTail = true))

        assertFalse(view("flow", pane(0L, "Radio", followTail = false)).hasSameSetupAs(saved))
    }

    @Test
    fun `summarises its panes for a listing`() {
        val v = ViewConfig(
            "flow",
            ViewConfig.panesRow(
                listOf(
                    PaneConfig(PaneId(0L), LogFilter(tags = setOf("Radio"))),
                    PaneConfig(PaneId(1L), LogFilter(query = "timeout")),
                    PaneConfig(PaneId(2L), LogFilter(tags = setOf("A", "B"))),
                    PaneConfig(PaneId(3L), LogFilter(minLevel = LogLevel.WARN)),
                ),
            ),
        )

        assertEquals("Radio | \"timeout\" | 2 tags | All", v.summary)
    }

    @Test
    fun `the default view is a single unfiltered pane`() {
        assertEquals(1, ViewConfig.Default.panes.size)
        assertTrue(ViewConfig.Default.panes.single().filter.isPassThrough)
    }
}
