package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.LogLineLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectiveColumnsTest {

    private val tagsOff = LogColumns(tag = false, layout = LogLineLayout.Stacked)

    /** With several tags in one stacked pane, hiding the tag leaves no way to tell lines apart. */
    @Test
    fun `a stacked pane watching several tags shows the tag anyway`() {
        val filter = LogFilter(tags = setOf("Radio", "Outcome"))

        assertTrue(effectiveColumnsFor(tagsOff, filter).tag)
    }

    @Test
    fun `a stacked pane pinned to one tag leaves the setting alone`() {
        val filter = LogFilter(tags = setOf("Radio"))

        assertFalse(effectiveColumnsFor(tagsOff, filter).tag)
    }

    @Test
    fun `a stacked pane with no tag filter leaves the setting alone`() {
        assertFalse(effectiveColumnsFor(tagsOff, LogFilter()).tag)
    }

    @Test
    fun `the columnar layout is never overridden`() {
        val columnar = LogColumns(tag = false, layout = LogLineLayout.Columns)
        val filter = LogFilter(tags = setOf("Radio", "Outcome"))

        assertFalse(effectiveColumnsFor(columnar, filter).tag)
    }

    @Test
    fun `nothing else about the columns is touched`() {
        val columns = LogColumns(
            timestamp = false,
            tag = false,
            level = false,
            processIds = true,
            softWrap = true,
            layout = LogLineLayout.Stacked,
        )

        val effective = effectiveColumnsFor(columns, LogFilter(tags = setOf("A", "B")))

        assertEquals(columns.copy(tag = true), effective)
    }

    @Test
    fun `a tag that is already on stays on`() {
        val columns = LogColumns(tag = true, layout = LogLineLayout.Stacked)

        assertTrue(effectiveColumnsFor(columns, LogFilter(tags = setOf("A", "B"))).tag)
    }

    /** A pinned tag repeated above every line, with nothing beside it, is pure noise. */
    @Test
    fun `a lone pinned tag with nothing else to show loses its header`() {
        val onlyTag = LogColumns(
            timestamp = false,
            level = false,
            tag = true,
            processIds = false,
            layout = LogLineLayout.Stacked,
        )

        assertFalse(effectiveColumnsFor(onlyTag, LogFilter(tags = setOf("Radio"))).tag)
    }

    @Test
    fun `a lone pinned tag is kept when something else shares the header`() {
        val withTimestamp = LogColumns(
            timestamp = true,
            level = false,
            tag = true,
            processIds = false,
            layout = LogLineLayout.Stacked,
        )
        val withLevel = withTimestamp.copy(timestamp = false, level = true)
        val withIds = withTimestamp.copy(timestamp = false, processIds = true)
        val filter = LogFilter(tags = setOf("Radio"))

        assertTrue(effectiveColumnsFor(withTimestamp, filter).tag)
        assertTrue(effectiveColumnsFor(withLevel, filter).tag)
        assertTrue(effectiveColumnsFor(withIds, filter).tag)
    }

    @Test
    fun `an unfiltered pane keeps its lone tag, since the tags vary`() {
        val onlyTag = LogColumns(
            timestamp = false,
            level = false,
            tag = true,
            processIds = false,
            layout = LogLineLayout.Stacked,
        )

        assertTrue(effectiveColumnsFor(onlyTag, LogFilter()).tag)
    }

    @Test
    fun `several tags still win even with nothing else showing`() {
        val onlyTag = LogColumns(
            timestamp = false,
            level = false,
            tag = true,
            processIds = false,
            layout = LogLineLayout.Stacked,
        )

        assertTrue(effectiveColumnsFor(onlyTag, LogFilter(tags = setOf("Radio", "Outcome"))).tag)
    }

    @Test
    fun `the columnar layout keeps its lone tag`() {
        val onlyTag = LogColumns(
            timestamp = false,
            level = false,
            tag = true,
            processIds = false,
            layout = LogLineLayout.Columns,
        )

        assertTrue(effectiveColumnsFor(onlyTag, LogFilter(tags = setOf("Radio"))).tag)
    }
}
