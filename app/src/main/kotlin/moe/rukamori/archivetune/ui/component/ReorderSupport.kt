/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import timber.log.Timber

private const val REORDER_LOG_TAG = "QueueDrag"

class ReorderDragTracker {
    @Volatile
    var lastPressedY = Float.NaN
        private set

    @Volatile
    var pressed = false
        private set

    @Volatile
    var draggedKey: Any? = null

    private var moves = 0

    fun onPointer(pressedY: Float?) {
        pressed = pressedY != null
        if (pressedY != null) lastPressedY = pressedY
    }

    fun onDragStarted(key: Any) {
        draggedKey = key
        moves = 0
        Timber.tag(REORDER_LOG_TAG).i("drag started key=%s y=%.0f", key, lastPressedY)
    }

    fun onMoved(
        from: Any,
        to: Any,
        fromIndex: Int,
        toIndex: Int,
    ) {
        draggedKey = from
        moves++
        Timber.tag(REORDER_LOG_TAG).d("move %d: %s (%d) -> %s (%d)", moves, from, fromIndex, to, toIndex)
    }

    fun onDragStopped(lazyListState: LazyListState) {
        val key = draggedKey ?: return
        val info = lazyListState.layoutInfo
        val visible = info.visibleItemsInfo.any { it.key == key }
        if (pressed) {
            Timber.tag(REORDER_LOG_TAG).w(
                "drag stopped while the finger is still down: key=%s moves=%d y=%.0f visible=%b first=%d scrolling=%b",
                key,
                moves,
                lastPressedY,
                visible,
                lazyListState.firstVisibleItemIndex,
                lazyListState.isScrollInProgress,
            )
        } else {
            Timber.tag(REORDER_LOG_TAG).i("drag released key=%s moves=%d y=%.0f visible=%b", key, moves, lastPressedY, visible)
        }
    }

    fun reset() {
        draggedKey = null
        lastPressedY = Float.NaN
        moves = 0
    }

    fun dropEdgeTarget(
        lazyListState: LazyListState,
        firstKey: Any?,
        lastKey: Any?,
    ): Any? {
        val dragged = draggedKey ?: return null
        val y = lastPressedY
        if (y.isNaN()) return null
        val info = lazyListState.layoutInfo
        val topPadding = -info.viewportStartOffset
        if (firstKey != null && firstKey != dragged) {
            val firstItem = info.visibleItemsInfo.firstOrNull { it.key == firstKey }
            if (firstItem != null && y < firstItem.offset + topPadding) return firstKey
        }
        if (lastKey != null && lastKey != dragged) {
            val lastItem = info.visibleItemsInfo.lastOrNull { it.key == lastKey }
            if (lastItem != null && y > lastItem.offset + lastItem.size + topPadding) return lastKey
        }
        return null
    }
}

fun Modifier.trackReorderPointer(tracker: ReorderDragTracker): Modifier =
    pointerInput(tracker) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                tracker.onPointer(event.changes.firstOrNull { it.pressed }?.position?.y)
            }
        }
    }

@Composable
fun KeepDraggedRowComposed(
    isDragging: Boolean,
    key: Any,
) {
    val pinnableContainer = LocalPinnableContainer.current
    DisposableEffect(isDragging, pinnableContainer) {
        val handle = if (isDragging) pinnableContainer?.pin() else null
        onDispose {
            if (isDragging) Timber.tag(REORDER_LOG_TAG).w("dragged row left composition or stopped dragging key=%s", key)
            handle?.release()
        }
    }
}
