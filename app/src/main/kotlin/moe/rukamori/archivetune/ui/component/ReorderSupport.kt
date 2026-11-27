/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.Scroller
import sh.calvin.reorderable.rememberScroller

private const val REORDER_SCROLL_VIEWPORT_FRACTION = 0.1f

private class ResilientScrollableState(
    private val delegate: ScrollableState,
) : ScrollableState by delegate {
    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        try {
            delegate.scroll(scrollPriority, block)
        } catch (interrupted: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }
}

@Composable
fun rememberReorderScroller(lazyListState: LazyListState): Scroller {
    val scrollable = remember(lazyListState) { ResilientScrollableState(lazyListState) }
    return rememberScroller(
        scrollableState = scrollable,
        pixelAmountProvider = { lazyListState.layoutInfo.viewportSize.height * REORDER_SCROLL_VIEWPORT_FRACTION },
    )
}

@Stable
class ReorderDragTracker {
    var pointerY by mutableFloatStateOf(Float.NaN)
    var draggedKey by mutableStateOf<Any?>(null)
}

fun Modifier.trackReorderPointer(tracker: ReorderDragTracker): Modifier =
    pointerInput(tracker) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                tracker.pointerY = event.changes.firstOrNull { it.pressed }?.position?.y ?: Float.NaN
            }
        }
    }

private data class ReorderEdgeMove(
    val dragged: Any,
    val target: Any,
    val draggedIndex: Int,
    val anchorFirstVisible: Boolean,
)

@Composable
fun ReorderEdgeCatchUp(
    reorderableState: ReorderableLazyListState,
    lazyListState: LazyListState,
    tracker: ReorderDragTracker,
    firstKey: () -> Any?,
    lastKey: () -> Any?,
    moveTo: (dragged: Any, target: Any) -> Unit,
) {
    val dragging = reorderableState.isAnyItemDragging
    val currentFirstKey by rememberUpdatedState(firstKey)
    val currentLastKey by rememberUpdatedState(lastKey)
    val currentMoveTo by rememberUpdatedState(moveTo)
    LaunchedEffect(dragging, tracker, lazyListState) {
        if (!dragging) return@LaunchedEffect
        snapshotFlow { edgeMoveFor(lazyListState, tracker, currentFirstKey(), currentLastKey()) }
            .collect { move ->
                if (move == null) return@collect
                if (move.anchorFirstVisible) {
                    lazyListState.requestScrollToItem(
                        lazyListState.firstVisibleItemIndex,
                        lazyListState.firstVisibleItemScrollOffset,
                    )
                }
                currentMoveTo(move.dragged, move.target)
            }
    }
}

private fun edgeMoveFor(
    lazyListState: LazyListState,
    tracker: ReorderDragTracker,
    first: Any?,
    last: Any?,
): ReorderEdgeMove? {
    val dragged = tracker.draggedKey ?: return null
    val y = tracker.pointerY
    if (y.isNaN()) return null
    val info = lazyListState.layoutInfo
    val draggedItem = info.visibleItemsInfo.firstOrNull { it.key == dragged } ?: return null
    val topPadding = -info.viewportStartOffset
    if (first != null && first != dragged) {
        val firstItem = info.visibleItemsInfo.firstOrNull { it.key == first }
        if (firstItem != null && y < firstItem.offset + topPadding) {
            return ReorderEdgeMove(
                dragged = dragged,
                target = first,
                draggedIndex = draggedItem.index,
                anchorFirstVisible = firstItem.index == lazyListState.firstVisibleItemIndex,
            )
        }
    }
    if (last != null && last != dragged) {
        val lastItem = info.visibleItemsInfo.lastOrNull { it.key == last }
        if (lastItem != null && y > lastItem.offset + lastItem.size + topPadding) {
            return ReorderEdgeMove(
                dragged = dragged,
                target = last,
                draggedIndex = draggedItem.index,
                anchorFirstVisible = false,
            )
        }
    }
    return null
}
