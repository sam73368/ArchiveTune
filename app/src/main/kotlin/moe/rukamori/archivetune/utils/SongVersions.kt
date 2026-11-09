/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

object SongVersions {
    data class ViewGroup(
        val key: String,
        val query: String,
    )

    private const val DURATION_TOLERANCE_SEC = 6
    private const val MAX_LOOKUPS = 30

    private val albumNoiseWords =
        setOf(
            "deluxe", "expanded", "remastered", "remaster", "anniversary", "edition", "collector", "collectors",
            "bonus", "complete", "platinum", "greatest", "hits", "best", "collection", "anthology", "essential",
            "essentials", "compilation", "soundtrack", "ost", "live", "unplugged", "reloaded", "revisited", "redux",
            "mixtape", "sessions",
        )

    private val albumVersionCache = ConcurrentHashMap<String, SongItem>()
    private val lookupPermits = Semaphore(4)

    private fun fold(raw: String): String = AlbumKindCatalog.normalizeArtist(raw)

    private fun variantKey(title: String): String {
        val bracketText = Regex("""\(([^)]*)\)|\[([^]]*)]""").findAll(title).joinToString(" ") { it.value }
        val dashText = title.substringAfter(" - ", "")
        return fold("$bracketText $dashText")
    }

    fun groupKeyOf(
        title: String,
        artist: String?,
    ): String? {
        val track = AlbumEditions.trackKey(title)
        val artistKey = fold(artist.orEmpty())
        if (track.isBlank() || artistKey.isBlank()) return null
        val variant = if (AlbumEditions.isStudioLike(title)) "" else "|${variantKey(title)}"
        return "$track|$artistKey$variant"
    }

    fun groupKeyOf(song: SongItem): String? = groupKeyOf(song.title, song.artists.firstOrNull()?.name)

    fun viewGroupOf(
        title: String,
        artist: String?,
    ): ViewGroup? {
        val key = groupKeyOf(title, artist) ?: return null
        return ViewGroup(key = key, query = listOf(title, artist.orEmpty()).filter { it.isNotBlank() }.joinToString(" "))
    }

    fun isSingleRelease(
        title: String,
        albumName: String?,
    ): Boolean {
        val album = albumName?.trim().orEmpty()
        if (album.isEmpty()) return false
        if (fold(album).endsWith(" single")) return true
        val albumKey = AlbumEditions.trackKey(album)
        return albumKey.isNotBlank() && albumKey == AlbumEditions.trackKey(title)
    }

    fun isSingleRelease(song: SongItem): Boolean = isSingleRelease(song.title, song.album?.name)

    private fun hasAlbumNoise(albumName: String): Boolean = fold(albumName).split(' ').any { it in albumNoiseWords }

    private fun durationsClose(
        first: SongItem,
        second: SongItem,
    ): Boolean {
        val a = first.duration ?: return true
        val b = second.duration ?: return true
        return kotlin.math.abs(a - b) <= DURATION_TOLERANCE_SEC
    }

    private fun pickAlbumVersion(
        single: SongItem,
        key: String,
        candidates: List<SongItem>,
    ): SongItem? {
        val best =
            candidates
                .filter { candidate ->
                    candidate.id != single.id &&
                        candidate.album != null &&
                        !isSingleRelease(candidate) &&
                        groupKeyOf(candidate) == key &&
                        durationsClose(single, candidate)
                }.withIndex()
                .sortedWith(
                    compareBy<IndexedValue<SongItem>> { it.value.explicit != single.explicit }
                        .thenBy { hasAlbumNoise(it.value.album?.name.orEmpty()) }
                        .thenByDescending { it.value.viewCount ?: 0L }
                        .thenBy { it.index },
                ).firstOrNull()
                ?.value ?: return null
        val singleCount = single.viewCount ?: 0L
        val albumCount = best.viewCount ?: 0L
        return if (singleCount > albumCount) {
            best.copy(viewCount = single.viewCount, viewCountText = single.viewCountText)
        } else {
            best
        }
    }

    suspend fun preferAlbumVersion(song: SongItem): SongItem {
        if (!isSingleRelease(song)) return song
        albumVersionCache[song.id]?.let { return it }
        val key = groupKeyOf(song) ?: return song
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val results =
            lookupPermits.withPermit {
                YouTube
                    .search("${song.title} $artist".trim(), YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
            } ?: return song
        val resolved = pickAlbumVersion(song, key, results) ?: song
        albumVersionCache[song.id] = resolved
        return resolved
    }

    suspend fun preferAlbumVersions(
        songs: List<SongItem>,
        maxLookups: Int = MAX_LOOKUPS,
    ): List<SongItem> {
        if (songs.none { isSingleRelease(it) }) return mergeSameSongs(songs)
        val resolved =
            coroutineScope {
                var budget = maxLookups
                songs
                    .map { song ->
                        if (isSingleRelease(song) && (albumVersionCache.containsKey(song.id) || budget-- > 0)) {
                            async { preferAlbumVersion(song) }
                        } else {
                            CompletableDeferred(song)
                        }
                    }.awaitAll()
            }
        return mergeSameSongs(resolved)
    }

    suspend fun resolveSongItems(
        items: List<YTItem>,
        budget: AtomicInteger,
    ): List<YTItem> =
        coroutineScope {
            items
                .map { item ->
                    if (item is SongItem &&
                        isSingleRelease(item) &&
                        (albumVersionCache.containsKey(item.id) || budget.getAndDecrement() > 0)
                    ) {
                        async<YTItem> { preferAlbumVersion(item) }
                    } else {
                        CompletableDeferred<YTItem>(item)
                    }
                }.awaitAll()
                .distinctBy { it.id }
        }

    fun mergeSameSongs(songs: List<SongItem>): List<SongItem> {
        val result = ArrayList<SongItem>(songs.size)
        val indexByKey = HashMap<String, Int>()
        val seenIds = HashSet<String>()
        for (song in songs) {
            if (!seenIds.add(song.id)) continue
            val key = groupKeyOf(song)
            if (key == null) {
                result += song
                continue
            }
            val existingIndex = indexByKey[key]
            if (existingIndex == null) {
                indexByKey[key] = result.size
                result += song
                continue
            }
            val existing = result[existingIndex]
            val better =
                if (isSingleRelease(existing) && !isSingleRelease(song)) song else existing
            val bestCount = maxOf(existing.viewCount ?: 0L, song.viewCount ?: 0L).takeIf { it > 0L }
            val countSource = if ((song.viewCount ?: 0L) > (existing.viewCount ?: 0L)) song else existing
            result[existingIndex] =
                better.copy(
                    viewCount = bestCount ?: better.viewCount,
                    viewCountText = if (bestCount != null) countSource.viewCountText else better.viewCountText,
                )
        }
        return result
    }

    fun <T : YTItem> List<T>.dropSingleTwins(): List<T> {
        val songs = filterIsInstance<SongItem>()
        if (songs.size < 2 || songs.none { isSingleRelease(it) }) return this
        val albumKeys = songs.filter { !isSingleRelease(it) }.mapNotNull { groupKeyOf(it) }.toHashSet()
        if (albumKeys.isEmpty()) return this
        return filterNot { item ->
            item is SongItem && isSingleRelease(item) && groupKeyOf(item) in albumKeys
        }
    }

    fun popularityOf(song: SongItem): Long {
        val grouped = groupKeyOf(song)?.let { ViewCountCache.groupCounts[it]?.toLong() }
        return maxOf(song.viewCount ?: -1L, grouped ?: -1L)
    }

    fun sortByPopularity(
        songs: List<SongItem>,
        query: String,
    ): List<SongItem> {
        val normalizedQuery = fold(query)
        fun tier(song: SongItem): Int {
            if (normalizedQuery.isEmpty()) return 3
            val title = fold(song.title)
            val base = AlbumEditions.trackKey(song.title)
            val haystack = title + " " + song.artists.joinToString(" ") { fold(it.name) }
            return when {
                title == normalizedQuery || base == normalizedQuery -> 0
                title.startsWith(normalizedQuery) || base.startsWith(normalizedQuery) -> 1
                normalizedQuery.split(' ').all { it.isEmpty() || haystack.contains(it) } -> 2
                else -> 3
            }
        }
        return songs
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<SongItem>> { tier(it.value) }
                    .thenByDescending { popularityOf(it.value) }
                    .thenBy { it.index },
            ).map { it.value }
    }
}
