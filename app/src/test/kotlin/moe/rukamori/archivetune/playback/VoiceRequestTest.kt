/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceRequestTest {
    private val albumFocus = "vnd.android.cursor.item/album"
    private val audioFocus = "vnd.android.cursor.item/audio"

    @Test
    fun albumFocusIsAnAlbumRequest() {
        val request =
            VoiceRequest.albumRequest(
                focus = albumFocus,
                title = null,
                album = "Nightmare",
                artist = "Avenged Sevenfold",
                query = "Nightmare Avenged Sevenfold",
            )
        assertEquals(VoiceAlbumRequest("Nightmare", "Avenged Sevenfold"), request)
    }

    @Test
    fun albumNameStoredInTitleStillCounts() {
        val request =
            VoiceRequest.albumRequest(
                focus = albumFocus,
                title = "Nightmare",
                album = null,
                artist = "Avenged Sevenfold",
                query = null,
            )
        assertEquals(VoiceAlbumRequest("Nightmare", "Avenged Sevenfold"), request)
    }

    @Test
    fun sameTitleAndAlbumWithoutSongFocusIsAnAlbumRequest() {
        val request =
            VoiceRequest.albumRequest(
                focus = null,
                title = "Nightmare",
                album = "Nightmare",
                artist = "Avenged Sevenfold",
                query = "Nightmare Avenged Sevenfold",
            )
        assertEquals(VoiceAlbumRequest("Nightmare", "Avenged Sevenfold"), request)
    }

    @Test
    fun sameTitleAndAlbumWithSongFocusStaysASongRequest() {
        val request =
            VoiceRequest.albumRequest(
                focus = audioFocus,
                title = "Nightmare",
                album = "Nightmare",
                artist = "Avenged Sevenfold",
                query = "Nightmare",
            )
        assertNull(request)
    }

    @Test
    fun songWithADifferentAlbumHintIsNotAnAlbumRequest() {
        val request =
            VoiceRequest.albumRequest(
                focus = null,
                title = "Bat Country",
                album = "City of Evil",
                artist = "Avenged Sevenfold",
                query = "Bat Country",
            )
        assertNull(request)
    }

    @Test
    fun spokenAlbumWordWithoutExtrasIsAnAlbumRequest() {
        val english = VoiceRequest.albumRequest(null, null, null, null, "album Nightmare Avenged Sevenfold")
        assertEquals("Nightmare Avenged Sevenfold", english?.albumName)
        val french = VoiceRequest.albumRequest(null, null, null, null, "l'album Nightmare")
        assertEquals("Nightmare", french?.albumName)
    }

    @Test
    fun plainQueryIsNotAnAlbumRequest() {
        assertNull(VoiceRequest.albumRequest(null, null, null, null, "Nightmare Avenged Sevenfold"))
    }

    private data class Song(
        val title: String,
        val artist: String,
    )

    private fun shape(
        items: List<Song>,
        query: String,
    ) = VoiceRequest.coherentSongQueue(items, query, artistOf = { it.artist }, titleOf = { it.title })

    @Test
    fun songRequestKeepsTheMatchThenOnlyTheSameArtistsStudioSongs() {
        val items =
            listOf(
                Song("Nightmare", "Avenged Sevenfold"),
                Song("Warmness On The Soul", "Avenged Sevenfold"),
                Song("Seize the Day (Live)", "Avenged Sevenfold"),
                Song("Nightmare", "Another Band"),
                Song("Scream", "Avenged Sevenfold"),
                Song("NIGHTMARE", "Avenged Sevenfold"),
            )
        val shaped = shape(items, "Nightmare Avenged Sevenfold")
        assertEquals(
            listOf("Nightmare", "Warmness On The Soul", "Scream"),
            shaped.map { it.title },
        )
    }

    @Test
    fun liveSongRequestKeepsLiveVersions() {
        val items =
            listOf(
                Song("Seize the Day (Live)", "Avenged Sevenfold"),
                Song("Unholy Confessions (Live)", "Avenged Sevenfold"),
            )
        assertEquals(items, shape(items, "Seize the Day Live Avenged Sevenfold"))
    }

    @Test
    fun genericQueryIsLeftAlone() {
        val items =
            listOf(
                Song("Nightmare", "Avenged Sevenfold"),
                Song("Master of Puppets", "Metallica"),
            )
        assertEquals(items, shape(items, "metal"))
    }
}
