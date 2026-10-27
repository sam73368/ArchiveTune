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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumKindRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Serializable
        private data class CachedArtist(
            val fetchedAt: Long,
            val found: Boolean,
            val entries: List<AlbumKindCatalog.Entry> = emptyList(),
        )

        @Serializable
        private data class Store(
            val artists: Map<String, CachedArtist> = emptyMap(),
        )

        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()

        private val json = Json { ignoreUnknownKeys = true }
        private val file = File(context.filesDir, STORE_FILE)
        private val mutex = Mutex()
        private var store: MutableMap<String, CachedArtist>? = null
        private var lastRequestAt = 0L

        suspend fun entriesFor(
            artistName: String,
            allowNetwork: Boolean = true,
        ): List<AlbumKindCatalog.Entry>? =
            withContext(Dispatchers.IO) {
                val key = AlbumKindCatalog.normalizeArtist(artistName)
                if (key.isBlank()) return@withContext null
                mutex.withLock {
                    val cache = loadStore()
                    val cached = cache[key]
                    val now = System.currentTimeMillis()
                    val ttl = if (cached?.found == true) FOUND_TTL_MS else MISSING_TTL_MS
                    if (cached != null && now - cached.fetchedAt < ttl) {
                        return@withLock cached.entries
                    }
                    if (!allowNetwork) return@withLock cached?.entries
                    val fetched =
                        try {
                            fetchArtist(artistName)
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            Timber.tag(TAG).w(error, "Album kind lookup failed for %s", artistName)
                            null
                        }
                    if (fetched == null) {
                        return@withLock cached?.entries
                    }
                    cache[key] = fetched
                    persist(cache)
                    fetched.entries
                }
            }

        private suspend fun fetchArtist(artistName: String): CachedArtist? {
            val escaped = artistName.replace("\\", "\\\\").replace("\"", "\\\"")
            val artistUrl =
                "$BASE_URL/artist"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", "artist:\"$escaped\"")
                    .addQueryParameter("limit", "5")
                    .addQueryParameter("fmt", "json")
                    .build()
            val artistBody = request(artistUrl.toString()) ?: return null
            val mbid =
                AlbumKindCatalog.parseBestArtistId(artistBody, artistName)
                    ?: return CachedArtist(System.currentTimeMillis(), found = false)

            val entries = mutableListOf<AlbumKindCatalog.Entry>()
            var offset = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                val url =
                    "$BASE_URL/release-group"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("artist", mbid)
                        .addQueryParameter("type", "album|ep|single")
                        .addQueryParameter("limit", PAGE_SIZE.toString())
                        .addQueryParameter("offset", offset.toString())
                        .addQueryParameter("fmt", "json")
                        .build()
                val body = request(url.toString()) ?: return null
                val page = AlbumKindCatalog.parseReleaseGroups(body) ?: return null
                entries += page.entries
                offset += PAGE_SIZE
                pages++
                if (offset >= page.total) break
            }
            return CachedArtist(System.currentTimeMillis(), found = entries.isNotEmpty(), entries = entries)
        }

        private suspend fun request(url: String): String? {
            repeat(2) { attempt ->
                val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastRequestAt = System.currentTimeMillis()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> return response.body?.string()
                        response.code == 503 || response.code == 429 -> if (attempt == 0) delay(RETRY_DELAY_MS)
                        else -> return null
                    }
                }
            }
            return null
        }

        private fun loadStore(): MutableMap<String, CachedArtist> {
            store?.let { return it }
            val loaded =
                runCatching {
                    if (file.exists()) json.decodeFromString<Store>(file.readText()).artists.toMutableMap() else null
                }.getOrNull() ?: mutableMapOf()
            store = loaded
            return loaded
        }

        private fun persist(cache: Map<String, CachedArtist>) {
            runCatching {
                val trimmed =
                    if (cache.size > MAX_ARTISTS) {
                        cache.entries.sortedByDescending { it.value.fetchedAt }.take(MAX_ARTISTS).associate { it.key to it.value }
                    } else {
                        cache
                    }
                val temp = File(file.parentFile, "$STORE_FILE.tmp")
                temp.writeText(json.encodeToString(Store(trimmed)))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not save the album kind cache") }
        }

        private companion object {
            const val TAG = "AlbumKinds"
            const val STORE_FILE = "album_kinds_v1.json"
            const val BASE_URL = "https://musicbrainz.org/ws/2"
            const val USER_AGENT = "ArchiveTune-Fork/1.0 ( https://github.com/sam73368/ArchiveTune )"
            const val PAGE_SIZE = 100
            const val MAX_PAGES = 5
            const val MAX_ARTISTS = 400
            const val MIN_INTERVAL_MS = 1_100L
            const val RETRY_DELAY_MS = 2_500L
            const val FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
            const val MISSING_TTL_MS = 3L * 24 * 60 * 60 * 1000
        }
    }
