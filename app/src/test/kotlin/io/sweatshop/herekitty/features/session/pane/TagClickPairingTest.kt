package io.sweatshop.herekitty.features.session.pane

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagClickPairingTest {

    private var now = 0L
    private val pairing = TagClickPairing(windowMillis = 300L, nowMillis = { now })

    private fun clickAfter(millis: Long, tag: String): Boolean {
        now += millis
        return pairing.isDoubleClick(tag)
    }

    @Test
    fun `two quick clicks on the same tag pair up`() {
        assertFalse(clickAfter(0, "Radio"))
        assertTrue(clickAfter(120, "Radio"))
    }

    @Test
    fun `a slow second click does not pair`() {
        assertFalse(clickAfter(0, "Radio"))
        assertFalse(clickAfter(400, "Radio"))
    }

    /** The row scrolls away between clicks, so what matters is the tag, not which row carried it. */
    @Test
    fun `clicks pair across different rows of the same tag`() {
        assertFalse(clickAfter(0, "Radio"))
        assertTrue(clickAfter(50, "Radio"))
    }

    @Test
    fun `clicks on different tags never pair`() {
        assertFalse(clickAfter(0, "Radio"))
        assertFalse(clickAfter(20, "Outcome"))
        assertFalse(clickAfter(20, "Radio"))
    }

    /** Four clicks are two double clicks, not three overlapping ones. */
    @Test
    fun `a pair is consumed rather than sliding along`() {
        assertFalse(clickAfter(0, "Radio"))
        assertTrue(clickAfter(50, "Radio"))
        assertFalse(clickAfter(50, "Radio"))
        assertTrue(clickAfter(50, "Radio"))
    }

    @Test
    fun `a click exactly on the window still pairs`() {
        assertFalse(clickAfter(0, "Radio"))
        assertTrue(clickAfter(300, "Radio"))
    }
}
