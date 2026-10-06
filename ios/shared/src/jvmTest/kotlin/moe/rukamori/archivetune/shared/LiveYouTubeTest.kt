/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.utils.sha1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the shared YouTube Music client against the live API from CI. Results are echoed as
 * GitHub `::notice::` workflow commands so they show up as check-run annotations.
 */
class LiveYouTubeTest {
    private fun notice(message: String) = println("::notice title=live-youtube::$message")

    @Test
    fun sha1MatchesKnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc"))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1(""))
    }

    @Test
    fun searchAlbumAndQueue() =
        runBlocking {
            val summary = YouTube.searchSummary("Bigflo et Oli La vie de rêve").getOrThrow()
            notice("searchSummary sections=${summary.summaries.map { "${it.title}:${it.items.size}" }}")
            assertTrue(summary.summaries.isNotEmpty(), "search returned no sections")

            val albums = YouTube.search("Bigflo et Oli La vie de rêve", SearchFilter.FILTER_ALBUM).getOrThrow()
            val album = albums.items.filterIsInstance<AlbumItem>().first()
            notice("album search first=${album.title} (${album.browseId})")

            val page = YouTube.album(album.browseId).getOrThrow()
            notice("album '${page.album.title}' songs=${page.songs.size}: ${page.songs.take(5).joinToString { it.title }}")
            assertTrue(page.songs.size >= 10, "album has only ${page.songs.size} songs")

            val firstSong = page.songs.first()
            val next =
                YouTube
                    .next(WatchEndpoint(videoId = firstSong.id, playlistId = page.album.playlistId))
                    .getOrThrow()
            notice("next queue size=${next.items.size} currentIndex=${next.currentIndex}")
            assertTrue(next.items.isNotEmpty())
        }

    @Test
    fun homeArtistPlaylist() =
        runBlocking {
            val home = YouTube.home().getOrThrow()
            notice("home sections=${home.sections.size}: ${home.sections.take(4).joinToString { it.title }}")

            val artists = YouTube.search("Bigflo et Oli", SearchFilter.FILTER_ARTIST).getOrThrow()
            val artistId = artists.items.first().id
            val artist = YouTube.artist(artistId).getOrThrow()
            notice("artist '${artist.artist.title}' sections=${artist.sections.map { it.title }}")
            assertTrue(artist.sections.isNotEmpty())

            val playlists = YouTube.search("Bigflo et Oli", SearchFilter.FILTER_FEATURED_PLAYLIST).getOrThrow()
            val playlistId = playlists.items.first().id
            val (playlist, songs) = YouTube.playlistAllSongs(playlistId).getOrThrow()
            notice("playlist '${playlist.playlist.title}' songs=${songs.size}")
            assertTrue(songs.filterIsInstance<SongItem>().isNotEmpty())
        }
}
