/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import moe.rukamori.archivetune.extensions.metadata

object CastQueueItems {
    private val byUid = ConcurrentHashMap<Any, MediaItem>()

    @Volatile
    private var pending: List<MediaItem>? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun expect(items: List<MediaItem>) {
        pending = items.toList()
    }

    fun has(uid: Any): Boolean = byUid.containsKey(uid)

    fun put(
        uid: Any,
        item: MediaItem,
    ) {
        if (item.metadata == null) return
        if (byUid.put(uid, item) == null) _version.update { it + 1 }
    }

    fun clear() {
        pending = null
        if (byUid.isEmpty()) return
        byUid.clear()
        _version.update { it + 1 }
    }

    fun fill(
        timeline: Timeline,
        windows: List<Timeline.Window>,
    ) {
        if (windows.none { it.mediaItem.metadata == null }) return
        val expected = pending
        if (expected != null && timeline.windowCount == expected.size) {
            val window = Timeline.Window()
            for (index in 0 until timeline.windowCount) {
                val uid = timeline.getWindow(index, window).uid
                expected[index].takeIf { it.metadata != null }?.let { byUid.putIfAbsent(uid, it) }
            }
            pending = null
        }
        windows.forEach { window ->
            if (window.mediaItem.metadata == null) byUid[window.uid]?.let { window.mediaItem = it }
        }
    }
}
