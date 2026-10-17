/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout

/**
 * Orders an artist's songs from the most viewed to the least viewed.
 *
 * The artist page only carries a handful of songs ("top songs", in YouTube's own order), so ranking
 * just those would show the five most viewed *of the five YouTube picked*. To get the real top, the
 * full list behind the section's arrow is loaded first and the whole list is ranked.
 */
object SongViewRanking {
    /** Only this many songs are ranked (one request each); the rest keep YouTube's order after them. */
    const val MAX_RANKED = 100
    private const val MAX_CONTINUATIONS = 10
    private const val COUNT_TIMEOUT_MS = 40_000L
    private const val RETRY_TIMEOUT_MS = 10_000L
    private const val IN_FLIGHT_WAIT_MS = 3_000

    class Collected(
        val songs: List<SongItem>,
        /** Left over when [MAX_RANKED] songs were reached before the list ended. */
        val continuation: String?,
    )

    /** The songs of [first] plus its continuation pages, in YouTube's order. */
    suspend fun collectSongs(first: ArtistItemsPage): Collected {
        val all = first.items.filterIsInstance<SongItem>().toMutableList()
        var continuation = first.continuation
        var pages = 0
        while (continuation != null && pages < MAX_CONTINUATIONS && all.size < MAX_RANKED) {
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            all += next.items.filterIsInstance<SongItem>()
            continuation = next.continuation
            pages++
        }
        return Collected(all.distinctBy { it.id }, continuation)
    }

    /** Every song behind [endpoint], or null if the page could not be loaded or is not a song list. */
    suspend fun fetchAllSongs(endpoint: BrowseEndpoint): Collected? {
        val first = YouTube.artistItems(endpoint).getOrNull() ?: return null
        if (first.layout != ArtistItemsPageLayout.LIST) return null
        val collected = collectSongs(first)
        return collected.takeIf { it.songs.isNotEmpty() }
    }

    /**
     * Sorts [songs] by view count, highest first. Songs whose count could not be fetched go last
     * in their original order. Every song gets a second attempt before being given up on.
     */
    suspend fun rank(
        context: Context,
        songs: List<SongItem>,
    ): List<SongItem> {
        val head = songs.take(MAX_RANKED)
        val tail = songs.drop(MAX_RANKED)
        if (head.size < 2) return songs

        withTimeoutOrNull(COUNT_TIMEOUT_MS) {
            coroutineScope {
                head.map { song -> launch { ViewCountCache.load(context, song.id) } }.joinAll()
            }
        }
        val missing = head.filter { ViewCountCache.counts[it.id] == null }
        if (missing.isNotEmpty()) {
            withTimeoutOrNull(RETRY_TIMEOUT_MS) {
                coroutineScope {
                    missing.map { song -> launch { ViewCountCache.retry(context, song.id) } }.joinAll()
                }
            }
        }
        // A row on screen may be fetching the same song: `load` returns at once in that case, so
        // wait briefly for those requests rather than ranking the song as unknown.
        var waitedMs = 0
        while (waitedMs < IN_FLIGHT_WAIT_MS && head.any { ViewCountCache.counts[it.id] == null && ViewCountCache.isInFlight(it.id) }) {
            delay(200)
            waitedMs += 200
        }
        return head.sortedByDescending { ViewCountCache.counts[it.id] ?: -1 } + tail
    }
}
