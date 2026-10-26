/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.soundcloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudOfficialGateTest {
    private val wanted =
        SoundCloudOfficialGate.Wanted(
            title = "Master of Puppets",
            artists = listOf("Metallica"),
            album = "Master of Puppets",
            isrc = null,
            explicit = false,
            durationMs = 515_000,
        )

    private fun candidate(
        title: String = "Master of Puppets",
        album: String? = "Master of Puppets",
        durationMs: Long? = 515_000,
        verified: Boolean = false,
        publisherArtist: String? = "Metallica",
        uploader: String? = "Metallica",
        description: String? = null,
        explicit: Boolean? = null,
        isrc: String? = null,
    ) =
        SoundCloudOfficialGate.Candidate(
            title = title,
            artistNames = listOfNotNull(publisherArtist, uploader),
            album = album,
            isrc = isrc,
            explicit = explicit,
            verifiedUploader = verified,
            uploaderName = uploader,
            publisherArtist = publisherArtist,
            labelName = null,
            description = description,
            durationMs = durationMs,
        )

    @Test
    fun acceptsOfficialStudioUpload() {
        assertTrue(SoundCloudOfficialGate.evaluate(wanted, candidate()).accepted)
    }

    @Test
    fun rejectsLiveTitle() {
        assertFalse(SoundCloudOfficialGate.evaluate(wanted, candidate(title = "Master of Puppets (Live in Moscow)")).accepted)
    }

    @Test
    fun rejectsLiveAlbum() {
        assertFalse(SoundCloudOfficialGate.evaluate(wanted, candidate(album = "Live at Wembley")).accepted)
    }

    @Test
    fun rejectsLiveRecordingWithPlainTitleBySlightlyDifferentLength() {
        assertFalse(SoundCloudOfficialGate.evaluate(wanted, candidate(durationMs = 548_000)).accepted)
    }

    @Test
    fun rejectsDescriptionAdmittingLiveRecording() {
        assertFalse(
            SoundCloudOfficialGate.evaluate(wanted, candidate(description = "Recorded live in 1991, audience recording")).accepted,
        )
    }

    @Test
    fun rejectsUnknownUploaderWithoutReleaseMetadata() {
        val result =
            SoundCloudOfficialGate.evaluate(
                wanted,
                candidate(album = null, publisherArtist = null, uploader = "xX_metal_fan_Xx"),
            )
        assertFalse(result.accepted)
    }

    @Test
    fun acceptsVerifiedArtistUploader() {
        assertTrue(
            SoundCloudOfficialGate.evaluate(
                wanted,
                candidate(album = null, publisherArtist = null, uploader = "Metallica", verified = true),
            ).accepted,
        )
    }

    @Test
    fun rejectsCleanEditWhenExplicitWanted() {
        val explicitWanted = wanted.copy(explicit = true)
        assertFalse(SoundCloudOfficialGate.evaluate(explicitWanted, candidate(explicit = false)).accepted)
        assertFalse(SoundCloudOfficialGate.evaluate(explicitWanted, candidate(explicit = null)).accepted)
        assertTrue(SoundCloudOfficialGate.evaluate(explicitWanted, candidate(explicit = true)).accepted)
    }

    @Test
    fun acceptsMatchingIsrcEvenWithoutOtherSignals() {
        val withIsrc = wanted.copy(isrc = "USEL11200001")
        val result =
            SoundCloudOfficialGate.evaluate(
                withIsrc,
                candidate(album = null, publisherArtist = null, uploader = "random", isrc = "USEL11200001"),
            )
        assertTrue(result.accepted)
    }

    @Test
    fun rejectsDifferentIsrc() {
        val withIsrc = wanted.copy(isrc = "USEL11200001")
        assertFalse(SoundCloudOfficialGate.evaluate(withIsrc, candidate(isrc = "USEL19999999")).accepted)
    }

    @Test
    fun wantedLiveTitleAllowsLiveCandidate() {
        val liveWanted = wanted.copy(title = "Master of Puppets (Live)")
        assertTrue(SoundCloudOfficialGate.evaluate(liveWanted, candidate(title = "Master of Puppets (Live)")).accepted)
    }

    @Test
    fun doesNotConfuseWordsContainingLive() {
        val wantedOlive = wanted.copy(title = "Olive Tree", album = null)
        assertTrue(SoundCloudOfficialGate.evaluate(wantedOlive, candidate(title = "Olive Tree")).accepted)
    }
}
