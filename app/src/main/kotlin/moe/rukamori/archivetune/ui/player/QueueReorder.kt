/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import moe.rukamori.archivetune.extensions.move

internal data class QueueReorderMove(
    val from: Int,
    val to: Int,
)

internal fun resolveQueueReorder(
    queueWindows: List<Timeline.Window>,
    visualWindows: List<Timeline.Window>,
    draggedUid: Any,
    leadingAnchorUid: Any? = null,
): QueueReorderMove? {
    val visualIndex = visualWindows.indexOfFirst { it.uid == draggedUid }
    if (visualIndex == -1) return null
    val source = queueWindows.locate(visualWindows[visualIndex])
    if (source == -1) return null

    for (index in visualIndex - 1 downTo 0) {
        val anchor = queueWindows.locate(visualWindows[index])
        if (anchor != -1 && anchor != source) {
            return QueueReorderMove(source, if (source < anchor) anchor else anchor + 1).takeIf { it.from != it.to }
        }
    }
    if (leadingAnchorUid != null) {
        val anchor = queueWindows.indexOfFirst { it.uid == leadingAnchorUid }
        if (anchor != -1 && anchor != source) {
            return QueueReorderMove(source, if (source < anchor) anchor else anchor + 1).takeIf { it.from != it.to }
        }
    }
    for (index in visualIndex + 1..visualWindows.lastIndex) {
        val next = queueWindows.locate(visualWindows[index])
        if (next != -1 && next != source) {
            return QueueReorderMove(source, if (source < next) next - 1 else next).takeIf { it.from != it.to }
        }
    }
    return null
}

internal fun Player.applyQueueReorder(
    shuffleTarget: ExoPlayer,
    queueWindows: List<Timeline.Window>,
    move: QueueReorderMove,
) {
    if (move.from !in queueWindows.indices || move.to !in queueWindows.indices) return
    if (!shuffleModeEnabled) {
        moveMediaItem(move.from, move.to)
    } else {
        shuffleTarget.setShuffleOrder(
            DefaultShuffleOrder(
                queueWindows
                    .map { it.firstPeriodIndex }
                    .toMutableList()
                    .move(move.from, move.to)
                    .toIntArray(),
                System.currentTimeMillis(),
            ),
        )
    }
}

private fun List<Timeline.Window>.locate(window: Timeline.Window): Int {
    val byUid = indexOfFirst { it.uid == window.uid }
    if (byUid != -1) return byUid
    val mediaId = window.mediaItem.mediaId
    val first = indexOfFirst { it.mediaItem.mediaId == mediaId }
    if (first == -1) return -1
    return if (indexOfLast { it.mediaItem.mediaId == mediaId } == first) first else -1
}
