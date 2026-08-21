package io.sweatshop.herekitty.domain.features.views.model

/** Which way a split divides its children. Shared by pane splits and the workspace layout. */
enum class LayoutOrientation {
    Horizontal,
    Vertical;

    val opposite: LayoutOrientation get() = if (this == Horizontal) Vertical else Horizontal
}
