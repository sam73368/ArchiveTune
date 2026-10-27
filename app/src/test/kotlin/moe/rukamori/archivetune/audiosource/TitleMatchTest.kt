/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

import moe.rukamori.archivetune.constants.AudioSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    @Test
    fun rejectsIdenticalTitleByDifferentArtist() {
        val result = evaluate(stream(title = "Stay", artist = "The Kid LAROI", durationMs = 141_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("artist"))
    }

    @Test
    fun acceptsMatchingCatalogMetadata() {
        val result =
            evaluate(
                stream(
                    title = "Stay",
                    artist = "Rihanna feat. Mikky Ekko",
                    album = "Unapologetic",
                    durationMs = 241_500,
                ),
            )

        assertTrue(result.accepted)
    }

    @Test
    fun rejectsWrongVersionEvenWhenArtistAndBaseTitleMatch() {
        val result = evaluate(stream(title = "Stay (Live)", artist = "Rihanna", durationMs = 241_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("version"))
    }

    @Test
    fun rejectsSoundCloudUploadWhoseLengthDiffersFromStudio() {
        val result =
            evaluate(
                stream(title = "Stay", artist = "Rihanna", durationMs = 253_000).copy(source = AudioSourceType.SOUNDCLOUD),
            )

        assertFalse(result.accepted)
    }

    @Test
    fun rejectsCleanEditWhenExplicitIsWanted() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "Stay",
                wantedArtists = listOf("Rihanna"),
                wantedAlbum = null,
                wantedDurationMs = 241_000,
                stream = stream(title = "Stay", artist = "Rihanna", durationMs = 241_000).copy(matchedExplicit = false),
                wantedExplicit = true,
            )

        assertFalse(result.accepted)
    }

    @Test
    fun rejectsLiveAlbumForStudioTrack() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "Stay",
                wantedArtists = listOf("Rihanna"),
                wantedAlbum = "Unapologetic",
                wantedDurationMs = 241_000,
                stream = stream(title = "Stay", artist = "Rihanna", album = "Live at the Garden", durationMs = 241_000),
            )

        assertFalse(result.accepted)
    }

    @Test
    fun rejectsImplausibleDuration() {
        val result = evaluate(stream(title = "Stay", artist = "Rihanna", durationMs = 310_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("duration"))
    }

    @Test
    fun preservesNonLatinTitles() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "カワキヲアメク",
                wantedArtists = listOf("美波"),
                wantedAlbum = null,
                wantedDurationMs = 251_000,
                stream = stream("カワキヲアメク", "美波", durationMs = 250_000),
            )

        assertTrue(result.accepted)
    }

    @Test
    fun rejectsMissingMatchMetadataInsteadOfAssumingExact() {
        val result = evaluate(stream(title = null, artist = null, durationMs = null))

        assertFalse(result.accepted)
    }

    private fun evaluate(stream: DirectStream): TitleMatch.Result =
        TitleMatch.evaluate(
            wantedTitle = "Stay",
            wantedArtists = listOf("Rihanna", "Mikky Ekko"),
            wantedAlbum = "Unapologetic",
            wantedDurationMs = 242_000,
            stream = stream,
        )

    @Test
    fun localizedFeaturingCreditsAreIgnored() {
        assertEquals("fuck you", TitleMatch.normalize("Fuck You (avec Devin The Dude et Snoop Dogg)"))
        assertEquals("fuck you", TitleMatch.normalize("Fuck You (feat. Devin The Dude & Snoop Dogg)"))
        assertEquals("song part 2", TitleMatch.normalize("Song (Part 2)"))
        val result =
            TitleMatch.evaluate(
                wantedTitle = "Fuck You (avec Devin The Dude et Snoop Dogg)",
                wantedArtists = listOf("Dr. Dre"),
                wantedAlbum = null,
                wantedDurationMs = 206_000,
                stream = stream(title = "Fuck You", artist = "Dr. Dre", durationMs = 206_000),
            )
        assertTrue(result.accepted)
    }

    private fun stream(
        title: String?,
        artist: String?,
        album: String? = null,
        durationMs: Long?,
    ) = DirectStream(
        uri = "https://example.invalid/audio.flac",
        mimeType = "audio/flac",
        codecs = "flac",
        contentLength = null,
        label = "test",
        source = AudioSourceType.TIDAL,
        matchedTitle = title,
        matchedArtist = artist,
        matchedAlbum = album,
        matchedDurationMs = durationMs,
    )
}
