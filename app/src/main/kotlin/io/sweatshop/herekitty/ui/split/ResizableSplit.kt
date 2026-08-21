package io.sweatshop.herekitty.ui.split

import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lays [items] out along one axis with a draggable divider between each pair, the way IntelliJ splits
 * an editor. Dragging a divider trades space between its two neighbours only, so panes further along
 * keep the size the user gave them.
 *
 * Sizes are held as weights keyed by item, so adding, closing, or reordering a child leaves the rest
 * alone. Pass [onReorder] to let children be dragged into a new position by their [ReorderGrip].
 */
@Composable
fun <T> ResizableSplit(
    items: List<T>,
    key: (T) -> Any,
    orientation: SplitOrientation,
    modifier: Modifier = Modifier,
    minChildSize: Dp = 180.dp,
    onReorder: ((from: Int, to: Int) -> Unit)? = null,
    content: @Composable (item: T, grip: ReorderGrip) -> Unit,
) {
    val keys = items.map(key)
    val weights = remember { mutableStateMapOf<Any, Float>() }
    val geometry = remember { SplitGeometry() }
    var activeDrag by remember { mutableStateOf<ReorderDrag?>(null) }

    // Drag callbacks outlive the sibling list they act on, so read both through the latest snapshot.
    val currentKeys by rememberUpdatedState(keys)
    val currentOnReorder by rememberUpdatedState(onReorder)

    val density = LocalDensity.current
    val minChildPx = with(density) { minChildSize.toPx() }
    val dividerPx = with(density) { HIT_SIZE.roundToPx() }

    LaunchedEffect(keys) {
        keys.forEach { weights.putIfAbsent(it, DEFAULT_WEIGHT) }
        weights.keys.retainAll(keys.toSet())

        // Whatever was being dragged is gone, so nothing should still look dragged.
        if (activeDrag?.key?.let { it !in keys } == true) activeDrag = null
    }

    Layout(
        modifier = modifier,
        content = {
            keys.forEachIndexed { index, itemKey ->
                if (index > 0) {
                    val previousKey = keys[index - 1]
                    SplitDivider(orientation) { delta ->
                        weights.tradeSpace(previousKey, itemKey, delta, geometry, minChildPx, currentKeys)
                    }
                }

                Box {
                    // The drag gesture is deliberately *outside* the key() below. Reordering swaps
                    // which key sits at this position, which tears down keyed content — and with it a
                    // gesture in flight, so onDragStopped would never run and the drag highlight
                    // would stay on forever. Anchored to the position instead, the gesture survives,
                    // and the drag remembers which item it grabbed by key.
                    val grip = if (currentOnReorder == null) {
                        ReorderGrip.Disabled
                    } else {
                        ReorderGrip(
                            isDragging = activeDrag?.key == itemKey,
                            isEnabled = true,
                            modifier = Modifier.draggable(
                                orientation = orientation.gestureOrientation,
                                state = rememberDraggableState { delta ->
                                    val drag = activeDrag ?: return@rememberDraggableState
                                    drag.position += delta
                                    val from = currentKeys.indexOf(drag.key)
                                    val to = geometry.indexAt(drag.position)
                                    if (from >= 0 && to >= 0 && to != from) {
                                        currentOnReorder?.invoke(from, to)
                                    }
                                },
                                onDragStarted = {
                                    val start = currentKeys.indexOf(itemKey)
                                    activeDrag = ReorderDrag(itemKey, geometry.centerOf(start))
                                },
                                onDragStopped = { activeDrag = null },
                            ),
                        )
                    }

                    key(itemKey) { content(items[index], grip) }
                }
            }
        },
    ) { measurables, constraints ->
        val childCount = keys.size
        if (childCount == 0) return@Layout layout(constraints.minWidth, constraints.minHeight) {}

        val horizontal = orientation == SplitOrientation.Horizontal
        val totalMain = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val totalCross = if (horizontal) constraints.maxHeight else constraints.maxWidth

        val dividerCount = childCount - 1
        val available = (totalMain - dividerPx * dividerCount).coerceAtLeast(0)
        val totalWeight = keys.sumOf { (weights[it] ?: DEFAULT_WEIGHT).toDouble() }.takeIf { it > 0.0 } ?: 1.0

        val sizes = IntArray(childCount)
        var assigned = 0
        for (index in 0 until childCount) {
            sizes[index] = if (index == childCount - 1) {
                available - assigned
            } else {
                ((available * (weights[keys[index]] ?: DEFAULT_WEIGHT)) / totalWeight).toInt()
            }
            assigned += sizes[index]
        }

        geometry.record(sizes, dividerPx, totalMain)

        val childPlaceables = (0 until childCount).map { index ->
            val size = sizes[index].coerceAtLeast(0)
            measurables[index * 2].measure(
                if (horizontal) {
                    Constraints.fixed(size, totalCross)
                } else {
                    Constraints.fixed(totalCross, size)
                },
            )
        }

        val dividerPlaceables = (0 until dividerCount).map { index ->
            measurables[index * 2 + 1].measure(
                if (horizontal) {
                    Constraints.fixed(dividerPx, totalCross)
                } else {
                    Constraints.fixed(totalCross, dividerPx)
                },
            )
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            var cursor = 0
            childPlaceables.forEachIndexed { index, placeable ->
                if (horizontal) placeable.placeRelative(cursor, 0) else placeable.placeRelative(0, cursor)
                cursor += sizes[index]

                if (index < dividerCount) {
                    val divider = dividerPlaceables[index]
                    if (horizontal) divider.placeRelative(cursor, 0) else divider.placeRelative(0, cursor)
                    cursor += dividerPx
                }
            }
        }
    }
}

private class ReorderDrag(val key: Any, var position: Float)

private fun SnapshotStateMap<Any, Float>.tradeSpace(
    firstKey: Any,
    secondKey: Any,
    deltaPx: Float,
    geometry: SplitGeometry,
    minChildPx: Float,
    allKeys: List<Any>,
) {
    val totalMain = geometry.totalMainAxis
    if (totalMain <= 0) return

    val totalWeight = allKeys.sumOf { (this[it] ?: DEFAULT_WEIGHT).toDouble() }.toFloat()
    if (totalWeight <= 0f) return

    val weightPerPx = totalWeight / totalMain
    val minWeight = minChildPx * weightPerPx
    val first = this[firstKey] ?: DEFAULT_WEIGHT
    val second = this[secondKey] ?: DEFAULT_WEIGHT

    val clamped = (deltaPx * weightPerPx).coerceIn(minWeight - first, second - minWeight)
    if (clamped == 0f) return

    this[firstKey] = first + clamped
    this[secondKey] = second - clamped
}

private const val DEFAULT_WEIGHT = 1f
