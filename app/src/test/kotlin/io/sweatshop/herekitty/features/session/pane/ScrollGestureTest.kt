package io.sweatshop.herekitty.features.session.pane

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollGestureTest {

    @Test
    fun `a clear upward flick detaches from the tail`() {
        assertTrue(isDeliberateUpwardScroll(deltaX = 0f, deltaY = -12f))
        assertTrue(isDeliberateUpwardScroll(deltaX = 0f, deltaY = -3f))
    }

    /** The reason this function exists: swiping across a long line must keep the pane at the bottom. */
    @Test
    fun `a sideways swipe with a little vertical drift does not`() {
        assertFalse(isDeliberateUpwardScroll(deltaX = -14f, deltaY = -0.6f))
        assertFalse(isDeliberateUpwardScroll(deltaX = 9f, deltaY = -2f))
        assertFalse(isDeliberateUpwardScroll(deltaX = -30f, deltaY = -4f))
    }

    @Test
    fun `tiny vertical jitter is ignored even with no sideways movement`() {
        assertFalse(isDeliberateUpwardScroll(deltaX = 0f, deltaY = -0.4f))
        assertFalse(isDeliberateUpwardScroll(deltaX = 0f, deltaY = -1.4f))
    }

    @Test
    fun `scrolling down never detaches`() {
        assertFalse(isDeliberateUpwardScroll(deltaX = 0f, deltaY = 12f))
        assertFalse(isDeliberateUpwardScroll(deltaX = 0f, deltaY = 0f))
    }

    @Test
    fun `a diagonal gesture counts only when it leans vertical`() {
        assertTrue(isDeliberateUpwardScroll(deltaX = 2f, deltaY = -10f))
        assertFalse(isDeliberateUpwardScroll(deltaX = -8f, deltaY = -10f))
    }

    @Test
    fun `pure horizontal movement is ignored`() {
        assertFalse(isDeliberateUpwardScroll(deltaX = -40f, deltaY = 0f))
        assertFalse(isDeliberateUpwardScroll(deltaX = 40f, deltaY = 0f))
    }
}
