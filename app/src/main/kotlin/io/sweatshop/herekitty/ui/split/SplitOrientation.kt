package io.sweatshop.herekitty.ui.split

import androidx.compose.foundation.gestures.Orientation
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation

enum class SplitOrientation {
    Horizontal,
    Vertical;

    val opposite: SplitOrientation get() = if (this == Horizontal) Vertical else Horizontal

    internal val gestureOrientation: Orientation
        get() = if (this == Horizontal) Orientation.Horizontal else Orientation.Vertical
}

/** Bridges the stored orientation to the one the layout primitives use. */
fun LayoutOrientation.toSplitOrientation(): SplitOrientation = when (this) {
    LayoutOrientation.Horizontal -> SplitOrientation.Horizontal
    LayoutOrientation.Vertical -> SplitOrientation.Vertical
}
