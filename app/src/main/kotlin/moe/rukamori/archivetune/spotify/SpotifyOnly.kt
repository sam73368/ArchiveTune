/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyOnly {
    private const val TRACK_PREFIX = "spotify-only-track-"
    private const val ALBUM_PREFIX = "spotify-only-album-"
    private const val REGISTRY_MAX_SIZE = 400

    private val registry =
        object : LinkedHashMap<String, MediaMetadata>(REGISTRY_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > REGISTRY_MAX_SIZE
        }

    fun trackMediaId(spotifyId: String): String = TRACK_PREFIX + spotifyId

    fun albumItemId(spotifyId: String): String = ALBUM_PREFIX + spotifyId

    fun isTrackId(id: String?): Boolean = id != null && id.startsWith(TRACK_PREFIX)

    fun isAlbumId(id: String?): Boolean = id != null && id.startsWith(ALBUM_PREFIX)

    fun isSpotifyOnly(id: String?): Boolean = isTrackId(id) || isAlbumId(id)

    fun spotifyIdOf(id: String): String = id.removePrefix(TRACK_PREFIX).removePrefix(ALBUM_PREFIX)

    fun albumRoute(id: String): String =
        if (isAlbumId(id)) "spotify_album/${spotifyIdOf(id)}" else "album/$id"

    fun remember(metadata: MediaMetadata) {
        synchronized(registry) { registry[metadata.id] = metadata }
    }

    fun metadataFor(item: SongItem): MediaMetadata =
        synchronized(registry) { registry[item.id] }
            ?: MediaMetadata(
                id = item.id,
                title = item.title,
                artists = item.artists.map { MediaMetadata.Artist(id = null, name = it.name) },
                duration = item.duration ?: -1,
                thumbnailUrl = item.thumbnail.takeIf { it.isNotBlank() },
                explicit = item.explicit,
                spotifyTrackId = spotifyIdOf(item.id),
            )
}

fun String?.isSpotifyOnlyId(): Boolean = SpotifyOnly.isSpotifyOnly(this)

fun List<SpotifyImage>.bestCoverUrl(): String? =
    filter { it.url.isNotBlank() }
        .sortedBy { it.width ?: it.height ?: Int.MAX_VALUE }
        .let { sorted -> sorted.firstOrNull { (it.width ?: it.height ?: 0) >= 300 } ?: sorted.lastOrNull() }
        ?.url

fun SpotifyTrack.toSpotifyOnlyMetadata(coverFallback: String? = null): MediaMetadata =
    MediaMetadata(
        id = SpotifyOnly.trackMediaId(id),
        title = name,
        artists = artists.map { MediaMetadata.Artist(id = null, name = it.name) },
        duration = (durationMs / 1000).takeIf { it > 0 } ?: -1,
        thumbnailUrl = album?.images?.bestCoverUrl() ?: coverFallback,
        explicit = explicit,
        spotifyTrackId = id,
        isrc = isrc,
    ).also(SpotifyOnly::remember)

fun SpotifyTrack.toSpotifyOnlySongItem(coverFallback: String? = null): SongItem {
    val metadata = toSpotifyOnlyMetadata(coverFallback)
    return SongItem(
        id = metadata.id,
        title = metadata.title,
        artists = metadata.artists.map { Artist(name = it.name, id = null) },
        album = null,
        duration = metadata.duration.takeIf { it > 0 },
        thumbnail = metadata.thumbnailUrl.orEmpty(),
        explicit = metadata.explicit,
    )
}

fun SpotifyAlbum.toSpotifyOnlyAlbumItem(): AlbumItem =
    AlbumItem(
        browseId = SpotifyOnly.albumItemId(id),
        playlistId = "",
        title = name,
        artists = artists.map { Artist(name = it.name, id = null) },
        year = releaseDate?.take(4)?.toIntOrNull(),
        thumbnail = images.bestCoverUrl().orEmpty(),
        explicit = false,
        releaseType =
            when (albumType?.lowercase()) {
                "single" -> AlbumReleaseType.SINGLE
                else -> AlbumReleaseType.ALBUM
            },
    )

fun PlayerConnection.playSpotifyOnlyTrack(metadata: MediaMetadata) {
    playSongKeepingQueue(
        metadata,
        ListQueue(title = metadata.title, items = listOf(metadata.toMediaItem())),
    )
}
