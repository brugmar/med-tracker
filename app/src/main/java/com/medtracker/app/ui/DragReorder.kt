package com.medtracker.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press drag reordering for a [LazyColumn]/[LazyRow][androidx.compose.foundation.lazy.LazyRow].
 *
 * Items are identified by their lazy-list key: only items whose key is a [Long]
 * (the medicine id) take part, so header rows with string keys are skipped both
 * as drag sources and as drop targets. Tracking by key instead of index keeps
 * the gesture stable while the backing list reorders asynchronously underneath.
 *
 * Attach [dragReorderContainer] to the list and wrap each reorderable item in
 * [DraggableItem]. [onMove] is called with the dragged id and the id whose
 * position it should take; it must reorder the backing list synchronously.
 */
@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (fromId: Long, toId: Long) -> Unit
): DragReorderState {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    return remember(listState) { DragReorderState(listState, scope, haptics, onMove) }
}

class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val onMove: (fromId: Long, toId: Long) -> Unit
) {
    var draggingKey by mutableStateOf<Long?>(null)
        private set
    internal var settlingKey by mutableStateOf<Long?>(null)
        private set
    internal val settlingOffset = Animatable(0f)

    private var draggedDistance by mutableFloatStateOf(0f)
    private var draggingInitialOffset = 0

    internal val vertical: Boolean
        get() = listState.layoutInfo.orientation == Orientation.Vertical

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** Displacement of the dragged item from its current slot, in the list axis. */
    internal val draggingItemOffset: Float
        get() = draggingItem?.let { draggingInitialOffset + draggedDistance - it.offset } ?: 0f

    internal fun onDragStart(position: Offset) {
        val axisPosition = (if (vertical) position.y else position.x).toInt()
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                item.key is Long && axisPosition in item.offset..(item.offset + item.size)
            }
            ?.let { item ->
                draggingKey = item.key as Long
                draggingInitialOffset = item.offset
                draggedDistance = 0f
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
    }

    internal fun onDrag(delta: Offset) {
        draggedDistance += if (vertical) delta.y else delta.x
        val dragging = draggingItem ?: return
        val startOffset = draggingInitialOffset + draggedDistance
        val endOffset = startOffset + dragging.size
        val middle = ((startOffset + endOffset) / 2f).toInt()

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key is Long && item.key != draggingKey &&
                middle in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            // Lazy layouts anchor scroll to the first visible item; pin it so the
            // list does not jump when that item is part of the swap.
            if (dragging.index == listState.firstVisibleItemIndex ||
                target.index == listState.firstVisibleItemIndex
            ) {
                scope.launch {
                    listState.scrollToItem(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset
                    )
                }
            }
            onMove(draggingKey ?: return, target.key as Long)
        } else {
            // Near the viewport edges, scroll the list under the stationary finger.
            val overscroll = when {
                draggedDistance > 0 ->
                    (endOffset - listState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggedDistance < 0 ->
                    (startOffset - listState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) {
                scope.launch { listState.scrollBy(overscroll) }
            }
        }
    }

    internal fun onDragInterrupted() {
        val key = draggingKey
        if (key != null) {
            // Let the released item glide from under the finger into its slot.
            settlingKey = key
            val releaseOffset = draggingItemOffset
            scope.launch {
                settlingOffset.snapTo(releaseOffset)
                settlingOffset.animateTo(
                    0f,
                    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
                )
                settlingKey = null
            }
        }
        draggingKey = null
        draggedDistance = 0f
        draggingInitialOffset = 0
    }
}

/** Put on the LazyColumn/LazyRow itself; items opt in via [DraggableItem]. */
fun Modifier.dragReorderContainer(state: DragReorderState): Modifier =
    pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> state.onDragStart(offset) },
            onDrag = { change, offset ->
                change.consume()
                state.onDrag(offset)
            },
            onDragEnd = { state.onDragInterrupted() },
            onDragCancel = { state.onDragInterrupted() }
        )
    }

@Composable
fun LazyItemScope.DraggableItem(
    state: DragReorderState,
    key: Long,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val dragging = key == state.draggingKey
    val positionModifier = when (key) {
        state.draggingKey -> Modifier
            .zIndex(1f)
            .graphicsLayer {
                if (state.vertical) translationY = state.draggingItemOffset
                else translationX = state.draggingItemOffset
            }
        state.settlingKey -> Modifier
            .zIndex(1f)
            .graphicsLayer {
                if (state.vertical) translationY = state.settlingOffset.value
                else translationX = state.settlingOffset.value
            }
        else -> Modifier.animateItem()
    }
    Box(modifier = modifier.then(positionModifier)) { content(dragging) }
}
