package moe.archivetune.server

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer

@Serializable
data class ArtistResult(
    val id: String,
    val name: String,
    val thumbnail: String?,
    val subtitle: String? = null,
)

@Serializable
data class CardItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val year: Int? = null,
    val explicit: Boolean = false,
    val track: TrackResult? = null,
)

@Serializable
data class ArtistShelf(
    val title: String,
    val kind: String,
    val items: List<CardItem>,
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
)

@Serializable
data class ArtistItemsResult(
    val title: String,
    val items: List<CardItem>,
)

@Serializable
data class AllSearchResult(
    val query: String,
    val top: CardItem?,
    val tracks: List<TrackResult>,
    val albums: List<AlbumResult>,
    val artists: List<ArtistResult>,
)

@Serializable
data class AlbumDetails(
    val album: AlbumResult,
    val tracks: List<TrackResult>,
)

@Serializable
data class ArtistDetails(
    val artist: ArtistResult,
    val description: String?,
    val topTracks: List<TrackResult>,
    val albums: List<AlbumResult>,
    val singles: List<AlbumResult>,
    val shelves: List<ArtistShelf>,
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
            .map { it.toArtist() }

    suspend fun searchAll(query: String): AllSearchResult =
        coroutineScope {
            val tracks = async { runCatching { searchTracks(query) }.getOrDefault(emptyList()) }
            val albums = async { runCatching { searchAlbums(query) }.getOrDefault(emptyList()) }
            val artists = async { runCatching { searchArtists(query) }.getOrDefault(emptyList()) }
            val t = tracks.await()
            val a = albums.await()
            val ar = artists.await()
            val wanted = normalize(query)
            val top =
                ar.firstOrNull { normalize(it.name) == wanted }?.let {
                    CardItem("artist", it.id, it.name, it.subtitle ?: "Artiste", it.thumbnail)
                } ?: a.firstOrNull { normalize(it.title) == wanted }?.let {
                    CardItem("album", it.id, it.title, it.artists.joinToString(", "), it.thumbnail, it.year, it.explicit)
                } ?: t.firstOrNull()?.let {
                    CardItem("song", it.id, it.title, it.artists.joinToString(", "), it.thumbnail, explicit = it.explicit, track = it)
                }
            if (t.isEmpty() && a.isEmpty() && ar.isEmpty()) error("search failed or returned nothing")
            AllSearchResult(query, top, t.take(8), a.take(10), ar.take(8))
        }

    suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty().take(8)

    suspend fun artistItems(
        browseId: String,
        params: String?,
    ): ArtistItemsResult {
        val page = YouTube.artistItems(BrowseEndpoint(browseId = browseId, params = params)).getOrThrow()
        val items = page.items.toMutableList()
        var continuation = page.continuation
        var pages = 0
        while (continuation != null && pages < 4) {
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            items += next.items
            continuation = next.continuation
            pages++
        }
        val cards = items.mapNotNull { it.toCard() }.distinctBy { it.kind + it.id }
        val explicitAlbumKeys =
            cards.filter { it.kind == "album" && it.explicit }.map { normalize(it.title) }.toSet()
        val cleaned = cards.filterNot { it.kind == "album" && !it.explicit && normalize(it.title) in explicitAlbumKeys }
        val sorted = if (cleaned.all { it.kind == "album" }) cleaned.sortedByDescending { it.year ?: 0 } else cleaned
        return ArtistItemsResult(page.title, sorted)
    }

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
        val shelves = mutableListOf<ArtistShelf>()
        var topSongsPlaylist: String? = null
        page.sections.forEach { section ->
            val isSingles = Regex("""\b(singles?|eps?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(section.title)
            val sectionSongs = section.items.filterIsInstance<SongItem>()
            val isTopSongs = shelves.isEmpty() && songs.isEmpty() && sectionSongs.isNotEmpty() && sectionSongs.size == section.items.size
            if (isTopSongs) {
                sectionSongs.forEach { item -> if (songs.none { it.id == item.id }) songs += item.toTrack() }
                topSongsPlaylist = section.moreEndpoint?.browseId
                return@forEach
            }
            section.items.forEach { item ->
                if (item is AlbumItem) {
                    val list = if (isSingles) singles else albums
                    if (list.none { it.id == item.browseId }) list += item.toAlbum()
                }
            }
            val cards = section.items.mapNotNull { it.toCard() }
            if (cards.isEmpty()) return@forEach
            val kind =
                when {
                    cards.all { it.kind == "artist" } -> "artists"
                    cards.all { it.kind == "song" } -> "songs"
                    isSingles -> "singles"
                    cards.all { it.kind == "album" } -> "albums"
                    else -> "mixed"
                }
            shelves +=
                ArtistShelf(
                    title = section.title,
                    kind = kind,
                    items = if (kind == "albums" || kind == "singles") cards.sortedByDescending { it.year ?: 0 } else cards,
                    moreBrowseId = section.moreEndpoint?.browseId,
                    moreParams = section.moreEndpoint?.params,
                )
        }
        val fullTop =
            topSongsPlaylist
                ?.let { playlistId ->
                    runCatching {
                        YouTube.playlist(playlistId.removePrefix("VL")).getOrThrow().songs.map { it.toTrack() }
                    }.getOrNull()
                }?.let(::dropCleanTwins)
                ?.takeIf { it.size > songs.size }
        return ArtistDetails(
            artist = page.artist.toArtist(),
            description = page.description,
            topTracks = (fullTop ?: songs).take(50),
            albums = albums.sortedByDescending { it.year ?: 0 },
            singles = singles.sortedByDescending { it.year ?: 0 },
            shelves = shelves,
        )
    }

    private fun ArtistItem.toArtist() =
        ArtistResult(
            id = id,
            name = title,
            thumbnail = thumbnail,
            subtitle = monthlyListenerCountText ?: subscriberCountText,
        )

    private fun YTItem.toCard(): CardItem? =
        when (this) {
            is SongItem -> CardItem("song", id, title, artists.joinToString(", ") { it.name }, thumbnail, explicit = explicit, track = toTrack())
            is AlbumItem -> CardItem("album", browseId, title, artists.orEmpty().joinToString(", ") { it.name }.ifBlank { null }, thumbnail, year, explicit)
            is ArtistItem -> CardItem("artist", id, title, monthlyListenerCountText ?: subscriberCountText, thumbnail)
            else -> null
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
