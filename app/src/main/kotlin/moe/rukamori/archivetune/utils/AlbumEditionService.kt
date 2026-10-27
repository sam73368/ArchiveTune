/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.Artist
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumEditionService
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        data class Result(
            val display: List<AlbumItem>,
            val replacements: Map<String, AlbumItem>,
            val baseOf: Map<String, AlbumItem>,
        )

        @Serializable
        private data class StoredAlbum(
            val id: String,
            val playlistId: String,
            val title: String,
            val year: Int?,
            val thumbnail: String,
            val explicit: Boolean,
            val thumbnailWidth: Int?,
            val thumbnailHeight: Int?,
            val artistNames: List<String> = emptyList(),
            val artistIds: List<String> = emptyList(),
        )

        @Serializable
        private data class StoredInfo(
            val fetchedAt: Long,
            val searchedAt: Long = 0L,
            val tracks: List<String>,
            val self: StoredAlbum,
            val siblings: List<StoredAlbum> = emptyList(),
        )

        @Serializable
        private data class Store(
            val infos: Map<String, StoredInfo> = emptyMap(),
        )

        private val json = Json { ignoreUnknownKeys = true }
        private val file = File(context.filesDir, STORE_FILE)
        private val lock = Any()
        private var infos = LinkedHashMap<String, StoredInfo>()
        private var loaded = false

        private fun ensureLoaded() {
            synchronized(lock) {
                if (loaded) return
                loaded = true
                val store =
                    runCatching { if (file.exists()) json.decodeFromString<Store>(file.readText()) else null }.getOrNull()
                        ?: Store()
                infos = LinkedHashMap(store.infos)
            }
        }

        private fun persist() {
            runCatching {
                val snapshot =
                    synchronized(lock) {
                        if (infos.size > MAX_INFOS) {
                            infos.entries.sortedByDescending { it.value.fetchedAt }.take(MAX_INFOS).associate { it.key to it.value }
                        } else {
                            LinkedHashMap(infos)
                        }
                    }
                val temp = File(file.parentFile, "$STORE_FILE.tmp")
                temp.writeText(json.encodeToString(Store(snapshot)))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not save the album edition cache") }
        }

        private fun infoOf(id: String): StoredInfo? = synchronized(lock) { infos[id] }

        private fun freshInfo(
            id: String,
            now: Long,
        ): StoredInfo? = infoOf(id)?.takeIf { now - it.fetchedAt < INFO_TTL_MS }

        private fun tracksOf(id: String): List<String>? = infoOf(id)?.tracks?.takeIf { it.isNotEmpty() }

        private fun compute(albums: List<AlbumItem>): Result {
            val universe = LinkedHashMap<String, AlbumItem>()
            albums.forEach { universe[it.id] = it }
            var frontier = albums.map { it.id }
            repeat(2) {
                val next = mutableListOf<String>()
                for (id in frontier) {
                    val info = infoOf(id) ?: continue
                    for (sibling in info.siblings) {
                        if (universe.putIfAbsent(sibling.id, toAlbumItem(sibling)) == null) next += sibling.id
                    }
                }
                frontier = next
            }

            val replacements = HashMap<String, AlbumItem>()
            val baseOf = HashMap<String, AlbumItem>()
            for (family in AlbumEditions.families(universe.values.toList())) {
                if (family.size < 2) continue
                val editions = family.map { AlbumEditions.Edition(it, tracksOf(it.id)) }
                for (cluster in AlbumEditions.partition(editions)) {
                    if (cluster.size < 2) continue
                    val decision = AlbumEditions.decide(cluster)
                    val byId = cluster.associateBy { it.album.id }
                    val base = byId[decision.baseId]?.album ?: continue
                    var display = byId[decision.displayId]?.album ?: continue
                    if (display.id != base.id && base.year != null) display = display.copy(year = base.year)
                    for (edition in cluster) {
                        if (edition.album.id != decision.displayId) replacements[edition.album.id] = display
                    }
                    if (display.id != base.id) baseOf[display.id] = base
                }
            }

            val shown = LinkedHashMap<String, AlbumItem>()
            for (album in albums) {
                val chosen = replacements[album.id] ?: album
                shown.putIfAbsent(chosen.id, chosen)
            }
            val listIds = albums.mapTo(HashSet()) { it.id }
            return Result(shown.values.toList(), replacements.filterKeys { it in listIds }, baseOf)
        }

        fun quick(albums: List<AlbumItem>): Result {
            ensureLoaded()
            return compute(albums)
        }

        private suspend fun fetchInfo(
            album: AlbumItem,
            now: Long,
        ): StoredInfo? {
            val page = runCatching { YouTube.album(album.id).getOrNull() }.getOrNull() ?: return null
            val tracks = page.songs.map { it.title }
            if (tracks.isEmpty()) return null
            val siblings =
                page.otherVersions
                    .filter { version ->
                        version.id != album.id &&
                            version.releaseType == AlbumReleaseType.ALBUM &&
                            AlbumEditions.sameFamilyTitles(version.title, album.title) &&
                            AlbumEditions.sharesArtist(album, version)
                    }.distinctBy { it.id }
            val previous = infoOf(album.id)
            return StoredInfo(
                fetchedAt = now,
                searchedAt = previous?.searchedAt ?: 0L,
                tracks = tracks,
                self = toStored(album),
                siblings = (siblings + previous?.siblings.orEmpty().map(::toAlbumItem)).distinctBy { it.id }.map(::toStored),
            )
        }

        private suspend fun fetchInfos(
            targets: List<AlbumItem>,
            limit: Int,
            now: Long,
        ) {
            val todo = targets.distinctBy { it.id }.filter { freshInfo(it.id, now) == null }.take(limit)
            if (todo.isEmpty()) return
            val semaphore = Semaphore(PARALLEL_FETCHES)
            val results =
                coroutineScope {
                    todo.map { album -> async { semaphore.withPermit { album to fetchInfo(album, now) } } }.awaitAll()
                }
            synchronized(lock) {
                results.forEach { (album, info) -> if (info != null) infos[album.id] = info }
            }
        }

        private suspend fun searchOriginals(
            albums: List<AlbumItem>,
            now: Long,
        ) {
            val candidates =
                albums
                    .filter { album ->
                        val info = freshInfo(album.id, now)
                        val albumYear = album.year
                        info != null &&
                            info.searchedAt == 0L &&
                            AlbumEditions.hasEditionMarker(album.title) &&
                            info.siblings.none { sibling ->
                                val siblingYear = sibling.year
                                siblingYear != null &&
                                    albumYear != null &&
                                    siblingYear <= albumYear &&
                                    !AlbumEditions.hasEditionMarker(sibling.title)
                            }
                    }.take(MAX_SEARCHES)
            for (album in candidates) {
                val query = listOf(AlbumEditions.familyKey(album.title), album.artists?.firstOrNull()?.name.orEmpty()).joinToString(" ").trim()
                if (query.isBlank()) continue
                val found =
                    runCatching {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM, useAccountContext = false).getOrNull()?.items
                    }.getOrNull() ?: continue
                val matches =
                    found
                        .filterIsInstance<AlbumItem>()
                        .filter { version ->
                            version.id != album.id &&
                                version.releaseType == AlbumReleaseType.ALBUM &&
                                AlbumEditions.sameFamilyTitles(version.title, album.title) &&
                                AlbumEditions.sharesArtist(album, version)
                        }.distinctBy { it.id }
                synchronized(lock) {
                    val info = infos[album.id] ?: return@synchronized
                    infos[album.id] =
                        info.copy(
                            searchedAt = now,
                            siblings = (info.siblings + matches.map(::toStored)).distinctBy { it.id },
                        )
                }
            }
        }

        suspend fun refine(albums: List<AlbumItem>): Result =
            withContext(Dispatchers.IO) {
                ensureLoaded()
                val now = System.currentTimeMillis()
                val fullAlbums =
                    albums
                        .filter { it.releaseType == AlbumReleaseType.ALBUM }
                        .sortedBy { if (AlbumEditions.hasEditionMarker(it.title)) 0 else 1 }
                fetchInfos(fullAlbums, MAX_LIST_FETCHES, now)
                searchOriginals(fullAlbums, now)
                val discovered =
                    fullAlbums
                        .flatMap { album -> infoOf(album.id)?.siblings.orEmpty().map(::toAlbumItem) }
                        .filter { sibling -> fullAlbums.none { it.id == sibling.id } }
                fetchInfos(discovered, MAX_SIBLING_FETCHES, now)
                persist()
                compute(albums)
            }

        fun siblingsOf(albumId: String): List<AlbumItem> {
            ensureLoaded()
            return synchronized(lock) {
                val own = infos[albumId]?.siblings.orEmpty()
                val reverse =
                    infos.values
                        .filter { info -> info.self.id != albumId && info.siblings.any { it.id == albumId } }
                        .flatMap { info -> listOf(info.self) + info.siblings }
                (own + reverse).filter { it.id != albumId }.distinctBy { it.id }.map(::toAlbumItem)
            }
        }

        private fun toStored(album: AlbumItem) =
            StoredAlbum(
                id = album.id,
                playlistId = album.playlistId,
                title = album.title,
                year = album.year,
                thumbnail = album.thumbnail,
                explicit = album.explicit,
                thumbnailWidth = album.thumbnailWidth,
                thumbnailHeight = album.thumbnailHeight,
                artistNames = album.artists.orEmpty().map { it.name },
                artistIds = album.artists.orEmpty().map { it.id.orEmpty() },
            )

        private fun toAlbumItem(stored: StoredAlbum) =
            AlbumItem(
                browseId = stored.id,
                playlistId = stored.playlistId,
                title = stored.title,
                artists =
                    stored.artistNames
                        .mapIndexed { index, name -> Artist(name, stored.artistIds.getOrNull(index)?.takeIf { it.isNotBlank() }) }
                        .takeIf { it.isNotEmpty() },
                year = stored.year,
                thumbnail = stored.thumbnail,
                explicit = stored.explicit,
                thumbnailWidth = stored.thumbnailWidth,
                thumbnailHeight = stored.thumbnailHeight,
            )

        private companion object {
            const val TAG = "AlbumEditions"
            const val STORE_FILE = "album_editions_v3.json"
            const val INFO_TTL_MS = 45L * 24 * 60 * 60 * 1000
            const val MAX_INFOS = 2500
            const val MAX_LIST_FETCHES = 45
            const val MAX_SIBLING_FETCHES = 25
            const val MAX_SEARCHES = 8
            const val PARALLEL_FETCHES = 3
        }
    }
