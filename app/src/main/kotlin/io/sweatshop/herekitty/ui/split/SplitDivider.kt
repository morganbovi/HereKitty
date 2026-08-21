package io.sweatshop.herekitty.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.dividerStyle

/**
 * A hairline that is easy to grab: the hit area is much wider than the line, and the line thickens
 * and picks up the accent colour while hovered or dragged, the way IntelliJ's splitters do.
 */
@Composable
internal fun SplitDivider(orientation: SplitOrientation, onDrag: (Float) -> Unit) {
    // The lambda closes over sibling keys, which change when children are reordered.
    val currentOnDrag by rememberUpdatedState(onDrag)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActive = isHovered || isDragged

    val style = JewelTheme.dividerStyle
    val lineColor = if (isActive) JewelTheme.globalColors.outlines.focused else style.color
    val lineThickness = if (isActive) ACTIVE_LINE_THICKNESS else style.metrics.thickness
    val trackColor = if (isActive) {
        JewelTheme.globalColors.outlines.focused.copy(alpha = TRACK_ALPHA)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    val horizontal = orientation == SplitOrientation.Horizontal

    Box(
        modifier = Modifier
            .then(if (horizontal) Modifier.fillMaxHeight().width(HIT_SIZE) else Modifier.fillMaxWidth().height(HIT_SIZE))
            .background(trackColor)
            .hoverable(interactionSource)
            .pointerHoverIcon(if (horizontal) HorizontalResizeCursor else VerticalResizeCursor)
            .draggable(
                state = rememberDraggableState { currentOnDrag(it) },
                orientation = orientation.gestureOrientation,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(
                    if (horizontal) {
                        Modifier.fillMaxHeight().width(lineThickness)
                    } else {
                        Modifier.fillMaxWidth().height(lineThickness)
                    },
                )
                .background(lineColor),
        )
    }
}

internal val HIT_SIZE = 7.dp
private val ACTIVE_LINE_THICKNESS = 2.dp
private const val TRACK_ALPHA = 0.12f
private val HorizontalResizeCursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
private val VerticalResizeCursor = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
