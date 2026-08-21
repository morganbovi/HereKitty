package io.sweatshop.herekitty.features.session.pane

import kotlin.math.abs

/**
 * Whether a scroll gesture was the user deliberately moving back up the log, which is what stops a
 * pane following the tail.
 *
 * Trackpads report a little vertical drift on almost every sideways swipe, so treating any upward
 * component as intent made scrolling across a long line detach the pane from the bottom. A gesture
 * has to be both big enough to be deliberate and clearly more vertical than horizontal.
 */
internal fun isDeliberateUpwardScroll(deltaX: Float, deltaY: Float): Boolean {
    val isUpward = deltaY <= -MIN_VERTICAL_DELTA
    val leansVertical = abs(deltaY) > abs(deltaX) * VERTICAL_DOMINANCE
    return isUpward && leansVertical
}

/** Below this, a gesture is drift rather than intent. */
private const val MIN_VERTICAL_DELTA = 1.5f

/** How much the vertical component must outweigh the horizontal one to count as a vertical scroll. */
private const val VERTICAL_DOMINANCE = 1.5f
