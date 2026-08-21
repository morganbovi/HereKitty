package io.sweatshop.herekitty.features.session

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneId

/** Which part of a pane a drag is hovering, and so what dropping there would do. */
enum class PaneDropRegion {
    Left,
    Right,
    Top,
    Bottom,
    Centre;

    val orientation: LayoutOrientation?
        get() = when (this) {
            Left, Right -> LayoutOrientation.Horizontal
            Top, Bottom -> LayoutOrientation.Vertical
            Centre -> null
        }

    val side: SplitSide?
        get() = when (this) {
            Left, Top -> SplitSide.Before
            Right, Bottom -> SplitSide.After
            Centre -> null
        }
}

data class PaneDropTarget(val paneId: PaneId, val region: PaneDropRegion)

/**
 * Where a drop at [position] would land.
 *
 * Each pane is divided into four edge bands and a middle: the edges insert beside, the middle swaps.
 * Whichever edge the pointer is *proportionally* nearest wins, so a tall narrow pane still offers
 * left and right rather than being all top and bottom.
 */
internal fun dropTargetAt(
    paneBounds: Map<PaneId, Rect>,
    position: Offset,
    excluding: PaneId?,
): PaneDropTarget? {
    val (paneId, bounds) = paneAt(paneBounds, position, excluding) ?: return null

    val fromLeft = (position.x - bounds.left) / bounds.width
    val fromTop = (position.y - bounds.top) / bounds.height

    val nearest = minOf(fromLeft, 1f - fromLeft, fromTop, 1f - fromTop)
    if (nearest > EDGE_BAND) return PaneDropTarget(paneId, PaneDropRegion.Centre)

    val region = when (nearest) {
        fromLeft -> PaneDropRegion.Left
        1f - fromLeft -> PaneDropRegion.Right
        fromTop -> PaneDropRegion.Top
        else -> PaneDropRegion.Bottom
    }
    return PaneDropTarget(paneId, region)
}

/**
 * The pane under [position], ignoring [excluding].
 *
 * A pane measured to nothing cannot be pointed at, so it is never a target. Dragging a whole pane
 * excludes itself, because a pane cannot land beside where it already is; dragging a *tag* excludes
 * nothing, because the edge of its own pane is a perfectly good place to send it.
 */
internal fun paneAt(
    paneBounds: Map<PaneId, Rect>,
    position: Offset,
    excluding: PaneId?,
): Pair<PaneId, Rect>? = paneBounds.entries
    .firstOrNull { (id, rect) ->
        id != excluding && rect.width > 0f && rect.height > 0f && rect.contains(position)
    }
    ?.let { it.key to it.value }

/** The area a drop would occupy, for the overlay to paint. */
internal fun PaneDropRegion.previewIn(bounds: Rect): Rect = when (this) {
    PaneDropRegion.Left -> Rect(bounds.left, bounds.top, bounds.left + bounds.width / 2f, bounds.bottom)
    PaneDropRegion.Right -> Rect(bounds.left + bounds.width / 2f, bounds.top, bounds.right, bounds.bottom)
    PaneDropRegion.Top -> Rect(bounds.left, bounds.top, bounds.right, bounds.top + bounds.height / 2f)
    PaneDropRegion.Bottom -> Rect(bounds.left, bounds.top + bounds.height / 2f, bounds.right, bounds.bottom)
    PaneDropRegion.Centre -> bounds
}

/** Below this fraction from an edge, a drop inserts beside rather than swapping. */
private const val EDGE_BAND = 0.25f
