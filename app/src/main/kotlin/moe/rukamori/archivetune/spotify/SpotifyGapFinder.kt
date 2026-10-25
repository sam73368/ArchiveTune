/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.TrackMatching
import moe.rukamori.archivetune.constants.SpotifyFillGapsKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyGaps(
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty()

    companion object {
        val EMPTY = SpotifyGaps()
    }
}

@Singleton
class SpotifyGapFinder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SpotifyLibraryRepository,
    ) {
        @Volatile
        private var pausedUntilMs = 0L

        suspend fun isEnabled(): Boolean {
            if (System.currentTimeMillis() < pausedUntilMs) return false
            val prefs = context.dataStore.data.first()
            return (prefs[SpotifyFillGapsKey] ?: true) && prefs[SpotifySpDcKey].orEmpty().isNotBlank()
        }

        suspend fun missingForSearch(
            query: String,
            known: List<YTItem>,
            hideExplicit: Boolean,
        ): SpotifyGaps {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !isEnabled()) return SpotifyGaps.EMPTY
            val result = spotifySearch(trimmed, listOf("track", "album")) ?: return SpotifyGaps.EMPTY
            val queryTokens = tokens(trimmed)

            val albumCandidates =
                result.albums?.items.orEmpty()
                    .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                    .filter { album -> covers(queryTokens, album.name, album.artists) }
                    .take(MAX_ALBUM_CHECKS)
            val trackCandidates =
                result.tracks?.items.orEmpty()
                    .filter { it.id.isNotBlank() && it.name.isNotBlank() && !it.isLocal }
                    .filter { track -> !hideExplicit || !track.explicit }
                    .filter { track -> covers(queryTokens, track.name, track.artists, track.album?.name) }
                    .take(MAX_TRACK_CHECKS)

            val knownAlbums = known.filterIsInstance<AlbumItem>()
            val knownSongs = known.filterIsInstance<SongItem>()

            return withContext(Dispatchers.IO) {
                coroutineScope {
                    val albumJobs =
                        albumCandidates.map { album ->
                            async { album.takeIf { isMissingAlbum(it, knownAlbums) } }
                        }
                    val trackJobs =
                        trackCandidates.map { track ->
                            async { track.takeIf { isMissingTrack(it, knownSongs) } }
                        }
                    val albums = albumJobs.awaitAll().filterNotNull()
                    val tracks = trackJobs.awaitAll().filterNotNull()
                    SpotifyGaps(
                        songs = tracks.map { it.toSpotifyOnlySongItem() },
                        albums = albums.map { it.toSpotifyOnlyAlbumItem() },
                    )
                }
            }
        }

        suspend fun missingAlbumsForArtist(
            artistName: String,
            knownAlbums: List<AlbumItem>,
        ): List<AlbumItem> {
            val name = artistName.trim()
            if (name.isEmpty() || !isEnabled()) return emptyList()
            val result = spotifySearch(name, listOf("album"), limit = 30) ?: return emptyList()
            val wanted = TrackMatching.normalize(name)
            val own =
                result.albums?.items.orEmpty()
                    .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                    .filter { album -> album.artists.any { TrackMatching.normalize(it.name) == wanted } }
                    .take(MAX_ARTIST_ALBUM_CHECKS)
            if (own.isEmpty()) return emptyList()
            return withContext(Dispatchers.IO) {
                coroutineScope {
                    own
                        .map { album -> async { album.takeIf { isMissingAlbum(it, knownAlbums) } } }
                        .awaitAll()
                        .filterNotNull()
                        .map { it.toSpotifyOnlyAlbumItem() }
                }
            }
        }

        private suspend fun spotifySearch(
            query: String,
            types: List<String>,
            limit: Int = 12,
        ) =
            try {
                repository.search(query = query, types = types, limit = limit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                pausedUntilMs = System.currentTimeMillis() + PAUSE_AFTER_FAILURE_MS
                null
            }

        private suspend fun isMissingAlbum(
            album: SpotifyAlbum,
            knownAlbums: List<AlbumItem>,
        ): Boolean {
            if (knownAlbums.any { albumMatches(album, it) }) return false
            val query = listOfNotNull(album.name, album.artists.firstOrNull()?.name).joinToString(" ")
            val found = youTubeSearchItems(query, YouTube.SearchFilter.FILTER_ALBUM) ?: return false
            return found.filterIsInstance<AlbumItem>().none { albumMatches(album, it) }
        }

        private suspend fun isMissingTrack(
            track: SpotifyTrack,
            knownSongs: List<SongItem>,
        ): Boolean {
            if (knownSongs.any { trackMatches(track, it) }) return false
            val query = listOfNotNull(track.name, track.artists.firstOrNull()?.name).joinToString(" ")
            val found = youTubeSearchItems(query, YouTube.SearchFilter.FILTER_SONG) ?: return false
            return found.filterIsInstance<SongItem>().none { trackMatches(track, it) }
        }

        private suspend fun youTubeSearchItems(
            query: String,
            filter: YouTube.SearchFilter,
        ): List<YTItem>? {
            val anonymous =
                try {
                    YouTube.search(query, filter, useAccountContext = false).getOrNull()?.items
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    null
                }
            if (!anonymous.isNullOrEmpty()) return anonymous
            return try {
                YouTube.search(query, filter).getOrNull()?.items
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }
        }

        private fun albumMatches(
            album: SpotifyAlbum,
            candidate: AlbumItem,
        ): Boolean =
            titlesMatch(album.name, candidate.title) &&
                artistsMatch(album.artists, candidate.artists?.map { it.name }.orEmpty())

        private fun trackMatches(
            track: SpotifyTrack,
            candidate: SongItem,
        ): Boolean =
            titlesMatch(track.name, candidate.title) &&
                artistsMatch(track.artists, candidate.artists.map { it.name })

        private fun titlesMatch(
            first: String,
            second: String,
        ): Boolean {
            val a = TrackMatching.normalizeTitle(first)
            val b = TrackMatching.normalizeTitle(second)
            if (a.isEmpty() || b.isEmpty()) return false
            return a == b || a.startsWith("$b ") || b.startsWith("$a ")
        }

        private fun artistsMatch(
            spotifyArtists: List<SpotifySimpleArtist>,
            candidateNames: List<String>,
        ): Boolean {
            if (candidateNames.isEmpty() || spotifyArtists.isEmpty()) return true
            val candidates = candidateNames.map(TrackMatching::normalize).filter { it.isNotEmpty() }
            return spotifyArtists.any { artist ->
                val normalized = TrackMatching.normalize(artist.name)
                normalized.isNotEmpty() &&
                    candidates.any { it == normalized || it.contains(normalized) || normalized.contains(it) }
            }
        }

        private fun tokens(value: String): List<String> =
            TrackMatching.normalize(value).split(' ').filter { it.isNotEmpty() }

        private fun covers(
            queryTokens: List<String>,
            title: String,
            artists: List<SpotifySimpleArtist>,
            albumName: String? = null,
        ): Boolean {
            if (queryTokens.isEmpty()) return false
            val haystack =
                tokens(
                    listOfNotNull(title, albumName).plus(artists.map { it.name }).joinToString(" "),
                )
            return queryTokens.all { token -> haystack.any { it.startsWith(token) } }
        }

        private companion object {
            const val MAX_ALBUM_CHECKS = 4
            const val MAX_TRACK_CHECKS = 4
            const val MAX_ARTIST_ALBUM_CHECKS = 12
            const val PAUSE_AFTER_FAILURE_MS = 60_000L
        }
    }

fun <T : YTItem> List<T>.withSpotifyGaps(
    extra: List<T>,
    startIndex: Int = 2,
): List<T> {
    if (extra.isEmpty()) return this
    val present = mapTo(HashSet()) { it.id }
    val additions = extra.filter { it.id !in present }
    if (additions.isEmpty()) return this
    val at = startIndex.coerceIn(0, size)
    return take(at) + additions + drop(at)
}
