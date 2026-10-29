package moe.archivetune.server

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import java.text.Normalizer

@Serializable
data class ArtistResult(
    val id: String,
    val name: String,
    val thumbnail: String?,
)

@Serializable
data class AlbumDetails(
    val album: AlbumResult,
    val tracks: List<TrackResult>,
)

@Serializable
data class ArtistDetails(
    val artist: ArtistResult,
    val topTracks: List<TrackResult>,
    val albums: List<AlbumResult>,
    val singles: List<AlbumResult>,
)

class Library {
    suspend fun searchTracks(query: String): List<TrackResult> =
        YouTube
            .search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow()
            .items
            .filterIsInstance<SongItem>()
            .map { it.toTrack() }
            .let(::dropCleanTwins)

    suspend fun searchAlbums(query: String): List<AlbumResult> =
        YouTube
            .search(query, YouTube.SearchFilter.FILTER_ALBUM)
            .getOrThrow()
            .items
            .filterIsInstance<AlbumItem>()
            .map { it.toAlbum() }
            .let(::dropCleanAlbumTwins)

    suspend fun searchArtists(query: String): List<ArtistResult> =
        YouTube
            .search(query, YouTube.SearchFilter.FILTER_ARTIST)
            .getOrThrow()
            .items
            .filterIsInstance<ArtistItem>()
            .map { ArtistResult(it.id, it.title, it.thumbnail) }

    suspend fun album(browseId: String): AlbumDetails {
        val page = YouTube.album(browseId).getOrThrow()
        val album = page.album.toAlbum()
        return AlbumDetails(
            album = album,
            tracks =
                page.songs.map { song ->
                    song.toTrack().let { track ->
                        track.copy(
                            album = track.album ?: album.title,
                            albumId = track.albumId ?: album.id,
                            thumbnail = album.thumbnail ?: track.thumbnail,
                        )
                    }
                },
        )
    }

    suspend fun artist(browseId: String): ArtistDetails {
        val page = YouTube.artist(browseId).getOrThrow()
        val songs = mutableListOf<TrackResult>()
        val albums = mutableListOf<AlbumResult>()
        val singles = mutableListOf<AlbumResult>()
        page.sections.forEach { section ->
            val isSingles = Regex("""\b(singles?|eps?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(section.title)
            section.items.forEach { item ->
                when (item) {
                    is SongItem -> if (songs.none { it.id == item.id }) songs += item.toTrack()
                    is AlbumItem -> (if (isSingles) singles else albums).let { list ->
                        if (list.none { it.id == item.browseId }) list += item.toAlbum()
                    }
                    else -> Unit
                }
            }
        }
        return ArtistDetails(
            artist = ArtistResult(page.artist.id, page.artist.title, page.artist.thumbnail),
            topTracks = songs,
            albums = albums.sortedByDescending { it.year ?: 0 },
            singles = singles.sortedByDescending { it.year ?: 0 },
        )
    }

    suspend fun track(videoId: String): TrackResult? =
        YouTube.queue(videoIds = listOf(videoId)).getOrNull()?.firstOrNull()?.toTrack()

    suspend fun radio(
        seed: TrackResult,
        limit: Int = 25,
    ): List<TrackResult> {
        val related =
            YouTube
                .next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM" + seed.id))
                .getOrNull()
                ?.items
                .orEmpty()
                .map { it.toTrack() }
                .filter { it.id != seed.id }
        return (listOf(seed) + dropCleanTwins(related)).distinctBy { it.id }.take(limit)
    }

    suspend fun bestTrack(query: String): TrackResult? = searchTracks(query).firstOrNull()

    suspend fun bestAlbum(query: String): AlbumResult? {
        val albums = searchAlbums(query)
        val wanted = normalize(query)
        return albums.firstOrNull { wanted.contains(normalize(it.title)) } ?: albums.firstOrNull()
    }

    private fun SongItem.toTrack() =
        TrackResult(
            id = id,
            title = title,
            artists = artists.map { it.name },
            album = album?.name,
            albumId = album?.id,
            durationSeconds = duration,
            thumbnail = thumbnail,
            explicit = explicit,
        )

    private fun AlbumItem.toAlbum() =
        AlbumResult(
            id = browseId,
            title = title,
            artists = artists.orEmpty().map { it.name },
            year = year,
            thumbnail = thumbnail,
            explicit = explicit,
        )

    companion object {
        fun normalize(value: String): String =
            Normalizer
                .normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        fun dropCleanTwins(tracks: List<TrackResult>): List<TrackResult> {
            val explicitKeys =
                tracks
                    .filter { it.explicit }
                    .map { normalize(it.title) + "|" + normalize(it.artists.firstOrNull().orEmpty()) }
                    .toSet()
            return tracks.filter { track ->
                track.explicit || (normalize(track.title) + "|" + normalize(track.artists.firstOrNull().orEmpty())) !in explicitKeys
            }
        }

        fun dropCleanAlbumTwins(albums: List<AlbumResult>): List<AlbumResult> {
            val explicitKeys =
                albums
                    .filter { it.explicit }
                    .map { normalize(it.title) + "|" + normalize(it.artists.firstOrNull().orEmpty()) }
                    .toSet()
            return albums.filter { album ->
                album.explicit || (normalize(album.title) + "|" + normalize(album.artists.firstOrNull().orEmpty())) !in explicitKeys
            }
        }
    }
}
