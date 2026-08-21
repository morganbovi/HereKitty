package io.sweatshop.herekitty.ui.split

import androidx.compose.ui.Modifier

/**
 * Handed to a child of [ResizableSplit] so it can decide where its drag handle lives. The split owns
 * the geometry; the child only says which part of itself is grabbable.
 */
class ReorderGrip internal constructor(
    val modifier: Modifier,
    val isDragging: Boolean,
    val isEnabled: Boolean,
) {
    internal companion object {
        val Disabled = ReorderGrip(Modifier, isDragging = false, isEnabled = false)
    }
}
