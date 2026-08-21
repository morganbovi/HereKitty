package io.sweatshop.herekitty.domain.features.settings.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogFontScaleTest {

    @Test
    fun `stepping up and down walks the ladder`() {
        assertEquals(1.1f, LogFontScale.increased(1f))
        assertEquals(1.25f, LogFontScale.increased(1.1f))
        assertEquals(0.9f, LogFontScale.decreased(1f))
        assertEquals(0.8f, LogFontScale.decreased(0.9f))
    }

    /** Holding the shortcut down must settle at the end rather than run away. */
    @Test
    fun `the ladder stops at both ends`() {
        assertEquals(LogFontScale.Steps.last(), LogFontScale.increased(LogFontScale.Steps.last()))
        assertEquals(LogFontScale.Steps.first(), LogFontScale.decreased(LogFontScale.Steps.first()))
    }

    @Test
    fun `a rung is never treated as bigger or smaller than itself`() {
        LogFontScale.Steps.forEach { rung ->
            assertTrue(LogFontScale.increased(rung) > rung || rung == LogFontScale.Steps.last())
            assertTrue(LogFontScale.decreased(rung) < rung || rung == LogFontScale.Steps.first())
        }
    }

    /** A value between rungs — an older settings file — moves the way it was asked to. */
    @Test
    fun `an off-ladder value steps onto the ladder`() {
        assertEquals(1.25f, LogFontScale.increased(1.15f))
        assertEquals(1.1f, LogFontScale.decreased(1.15f))
    }

    @Test
    fun `stored values are clamped and nonsense falls back to the default`() {
        assertEquals(LogFontScale.Steps.last(), LogFontScale.sanitised(99f))
        assertEquals(LogFontScale.Steps.first(), LogFontScale.sanitised(0.01f))
        assertEquals(LogFontScale.Default, LogFontScale.sanitised(Float.NaN))
        assertEquals(LogFontScale.Default, LogFontScale.sanitised(Float.POSITIVE_INFINITY))
        assertEquals(1.5f, LogFontScale.sanitised(1.5f))
    }

    @Test
    fun `the default is a rung, so stepping from it is predictable`() {
        assertTrue(LogFontScale.Default in LogFontScale.Steps)
    }
}
