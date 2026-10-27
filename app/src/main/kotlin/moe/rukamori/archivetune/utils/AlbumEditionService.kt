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
            val hiddenIds: Set<String>,
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
        private data class StoredFamily(
            val savedAt: Long,
            val displayId: String,
            val baseId: String,
            val members: List<StoredAlbum>,
        )

        @Serializable
        private data class Store(
            val families: Map<String, StoredFamily> = emptyMap(),
            val tracks: Map<String, List<String>> = emptyMap(),
        )

        private val json = Json { ignoreUnknownKeys = true }
        private val file = File(context.filesDir, STORE_FILE)
        private val lock = Any()
        private var families = LinkedHashMap<String, StoredFamily>()
        private var tracks = LinkedHashMap<String, List<String>>()
        private var familyByMember = HashMap<String, String>()
        private var loaded = false

        private fun ensureLoaded() {
            synchronized(lock) {
                if (loaded) return
                loaded = true
                val store =
                    runCatching { if (file.exists()) json.decodeFromString<Store>(file.readText()) else null }.getOrNull()
                        ?: Store()
                families = LinkedHashMap(store.families)
                tracks = LinkedHashMap(store.tracks)
                reindex()
            }
        }

        private fun reindex() {
            familyByMember = HashMap()
            families.forEach { (key, family) -> family.members.forEach { familyByMember[it.id] = key } }
        }

        private fun persist() {
            runCatching {
                val trimmedFamilies =
                    if (families.size > MAX_FAMILIES) {
                        families.entries.sortedByDescending { it.value.savedAt }.take(MAX_FAMILIES).associate { it.key to it.value }
                    } else {
                        families
                    }
                val trimmedTracks =
                    if (tracks.size > MAX_TRACK_LISTS) tracks.entries.toList().takeLast(MAX_TRACK_LISTS).associate { it.key to it.value } else tracks
                val temp = File(file.parentFile, "$STORE_FILE.tmp")
                temp.writeText(json.encodeToString(Store(trimmedFamilies, trimmedTracks)))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not save the album edition cache") }
        }

        private fun familyKeyOf(members: List<AlbumItem>): String = members.map { it.id }.sorted().joinToString("|")

        private fun buildResult(
            albums: List<AlbumItem>,
            decisions: Map<String, AlbumEditions.Decision>,
        ): Result {
            val byId = albums.associateBy { it.id }
            val displayIds = HashSet<String>()
            val hidden = HashSet<String>()
            val baseOf = HashMap<String, AlbumItem>()
            for (family in AlbumEditions.families(albums)) {
                val key = familyKeyOf(family)
                val decision = decisions[key]
                if (family.size == 1 || decision == null) {
                    displayIds += family.map { it.id }
                    continue
                }
                displayIds += decision.displayId
                family.filter { it.id != decision.displayId }.forEach { hidden += it.id }
                if (decision.baseId != decision.displayId) byId[decision.baseId]?.let { baseOf[decision.displayId] = it }
            }
            return Result(albums.filter { it.id in displayIds }, hidden, baseOf)
        }

        fun quick(albums: List<AlbumItem>): Result {
            ensureLoaded()
            val now = System.currentTimeMillis()
            val decisions = HashMap<String, AlbumEditions.Decision>()
            for (family in AlbumEditions.families(albums)) {
                if (family.size < 2) continue
                val key = familyKeyOf(family)
                val stored = synchronized(lock) { families[key] }
                decisions[key] =
                    if (stored != null && now - stored.savedAt < FAMILY_TTL_MS) {
                        AlbumEditions.Decision(stored.displayId, stored.baseId)
                    } else {
                        AlbumEditions.heuristicDecision(family)
                    }
            }
            return buildResult(albums, decisions)
        }

        suspend fun refine(albums: List<AlbumItem>): Result =
            withContext(Dispatchers.IO) {
                ensureLoaded()
                val now = System.currentTimeMillis()
                val decisions = HashMap<String, AlbumEditions.Decision>()
                val pending = mutableListOf<Pair<String, List<AlbumItem>>>()
                for (family in AlbumEditions.families(albums)) {
                    if (family.size < 2) continue
                    val key = familyKeyOf(family)
                    val stored = synchronized(lock) { families[key] }
                    if (stored != null && now - stored.savedAt < FAMILY_TTL_MS) {
                        decisions[key] = AlbumEditions.Decision(stored.displayId, stored.baseId)
                    } else {
                        decisions[key] = AlbumEditions.heuristicDecision(family)
                        pending += key to family
                    }
                }
                var fetchBudget = MAX_FETCHES_PER_REFINE
                val semaphore = Semaphore(3)
                var changed = false
                for ((key, family) in pending) {
                    val missing = family.filter { synchronized(lock) { tracks[it.id] } == null }
                    if (missing.size > fetchBudget) continue
                    fetchBudget -= missing.size
                    val fetched =
                        coroutineScope {
                            missing
                                .map { album ->
                                    async {
                                        semaphore.withPermit {
                                            album.id to
                                                runCatching { YouTube.album(album.id).getOrNull()?.songs?.map { it.title } }.getOrNull()
                                        }
                                    }
                                }.awaitAll()
                        }
                    if (fetched.any { it.second.isNullOrEmpty() }) continue
                    synchronized(lock) { fetched.forEach { (id, list) -> tracks[id] = list.orEmpty() } }
                    val editions = family.map { AlbumEditions.Edition(it, synchronized(lock) { tracks[it.id] }) }
                    val decision = AlbumEditions.decide(editions)
                    decisions[key] = decision
                    synchronized(lock) {
                        families[key] = StoredFamily(now, decision.displayId, decision.baseId, family.map(::toStored))
                        changed = true
                    }
                }
                if (changed) {
                    synchronized(lock) {
                        reindex()
                        persist()
                    }
                }
                buildResult(albums, decisions)
            }

        fun siblingsOf(albumId: String): List<AlbumItem> {
            ensureLoaded()
            return synchronized(lock) {
                val key = familyByMember[albumId] ?: return emptyList()
                families[key]?.members?.filter { it.id != albumId }?.map(::toAlbumItem).orEmpty()
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
            const val STORE_FILE = "album_editions_v1.json"
            const val FAMILY_TTL_MS = 45L * 24 * 60 * 60 * 1000
            const val MAX_FAMILIES = 600
            const val MAX_TRACK_LISTS = 1500
            const val MAX_FETCHES_PER_REFINE = 40
        }
    }
