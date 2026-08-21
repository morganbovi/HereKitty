package io.sweatshop.herekitty.features.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.ui.split.ReorderGrip
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** What is currently in the air over a session's panes. */
private sealed interface PaneDrag {
    val sourceId: PaneId

    data class WholePane(override val sourceId: PaneId) : PaneDrag

    data class Tag(val tag: String, override val sourceId: PaneId) : PaneDrag
}

/**
 * Tracks what is being dragged across a session's panes, so the drop it is heading for can be
 * previewed. A whole pane lands in half of its target; a single tag lands in the target entire, which
 * is what tells the two apart on screen.
 *
 * Everything is kept in the host's own coordinate space, reached by subtracting the host's window
 * origin. Window positions are re-read on every layout rather than by holding a [LayoutCoordinates],
 * which stops referring to anything the moment its pane is removed.
 */
class PaneDragState {
    private val paneBounds = mutableStateMapOf<PaneId, Rect>()
    private var hostOrigin by mutableStateOf(Offset.Zero)
    private var pointer by mutableStateOf(Offset.Zero)
    private var activeDrag by mutableStateOf<PaneDrag?>(null)

    val draggedPaneId: PaneId?
        get() = (activeDrag as? PaneDrag.WholePane)?.sourceId

    /**
     * A pane cannot land beside where it already is, so it excludes itself. A tag can: the edge of its
     * own pane is where you send it to get a pane of its own.
     */
    private val dropTarget: PaneDropTarget?
        get() = when (val drag = activeDrag) {
            is PaneDrag.WholePane -> dropTargetAt(paneBounds, pointer, excluding = drag.sourceId)
            is PaneDrag.Tag -> dropTargetAt(paneBounds, pointer, excluding = null)
            null -> null
        }

    internal val dropPreview: Rect?
        get() = dropTarget?.let { target -> paneBounds[target.paneId]?.let(target.region::previewIn) }

    internal fun hostPositioned(coordinates: LayoutCoordinates) {
        hostOrigin = coordinates.positionInWindow()
    }

    internal fun panePositioned(paneId: PaneId, coordinates: LayoutCoordinates) {
        paneBounds[paneId] = Rect(coordinates.positionInWindow() - hostOrigin, coordinates.size.toSize())
    }

    internal fun paneRemoved(paneId: PaneId) {
        paneBounds.remove(paneId)
    }

    /**
     * The handle that lifts [paneId] out of the layout.
     *
     * The gesture reports where the drop landed rather than acting on the tree itself, so the pane
     * order only ever changes once, on release — a layout that rearranged under a live drag would
     * tear down the gesture doing the dragging.
     */
    @Composable
    internal fun gripFor(
        paneId: PaneId,
        isEnabled: Boolean,
        onDrop: (PaneDropTarget) -> Unit,
    ): ReorderGrip {
        if (!isEnabled) return ReorderGrip.Disabled

        var gripOrigin by remember { mutableStateOf(Offset.Zero) }
        val currentOnDrop by rememberUpdatedState(onDrop)

        return ReorderGrip(
            isDragging = draggedPaneId == paneId,
            isEnabled = true,
            modifier = Modifier
                .onGloballyPositioned { gripOrigin = it.positionInWindow() - hostOrigin }
                .pointerInput(paneId) {
                    detectDragGestures(
                        onDragStart = { grabbedAt ->
                            pointer = gripOrigin + grabbedAt
                            activeDrag = PaneDrag.WholePane(paneId)
                        },
                        onDrag = { _, movement -> pointer += movement },
                        onDragEnd = {
                            dropTarget?.let { currentOnDrop(it) }
                            activeDrag = null
                        },
                        onDragCancel = { activeDrag = null },
                    )
                },
        )
    }

    /**
     * Makes a tag chip draggable onto another pane, fading it while it is in the air.
     *
     * Dropped on an edge the tag gets a pane of its own there; dropped in the middle it joins that
     * pane's filter. Same five regions as dragging a whole pane, so the gesture reads the same way.
     *
     * `draggable2D` rather than a raw pointer handler because it waits for touch slop before claiming
     * the gesture — so the chip's own click, and the cross that removes the tag, keep working.
     */
    @Composable
    internal fun tagGrip(tag: String, sourceId: PaneId, onDrop: (PaneDropTarget) -> Unit): Modifier {
        var chipOrigin by remember { mutableStateOf(Offset.Zero) }
        val currentOnDrop by rememberUpdatedState(onDrop)
        val gesture = rememberDraggable2DState { movement -> pointer += movement }
        val isDragging = activeDrag == PaneDrag.Tag(tag, sourceId)

        return Modifier
            .alpha(if (isDragging) DRAGGED_ALPHA else 1f)
            .onGloballyPositioned { chipOrigin = it.positionInWindow() - hostOrigin }
            .draggable2D(
                state = gesture,
                onDragStarted = { grabbedAt ->
                    pointer = chipOrigin + grabbedAt
                    activeDrag = PaneDrag.Tag(tag, sourceId)
                },
                onDragStopped = {
                    dropTarget?.let { currentOnDrop(it) }
                    activeDrag = null
                },
            )
    }
}

/** Wraps a session's panes so a drag over them can be painted above them. */
@Composable
internal fun PaneDragHost(
    dragState: PaneDragState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused

    Box(modifier.onGloballyPositioned(dragState::hostPositioned)) {
        content()

        dragState.dropPreview?.let { preview ->
            Canvas(Modifier.fillMaxSize()) {
                drawRect(accent.copy(alpha = PREVIEW_FILL_ALPHA), preview.topLeft, preview.size)
                drawRect(accent, preview.topLeft, preview.size, style = Stroke(PREVIEW_BORDER.toPx()))
            }
        }
    }
}

/** How much of itself a pane or tag keeps showing in its old place while being dragged elsewhere. */
internal const val DRAGGED_ALPHA = 0.4f

private const val PREVIEW_FILL_ALPHA = 0.22f
private val PREVIEW_BORDER = 2.dp
