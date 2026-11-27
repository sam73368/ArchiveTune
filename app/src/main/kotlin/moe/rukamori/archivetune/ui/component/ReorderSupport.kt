/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

class ReorderDragTracker {
    @Volatile
    var lastPressedY = Float.NaN
        private set

    @Volatile
    var draggedKey: Any? = null

    fun onPointer(pressedY: Float?) {
        if (pressedY != null) lastPressedY = pressedY
    }

    fun reset() {
        draggedKey = null
        lastPressedY = Float.NaN
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
