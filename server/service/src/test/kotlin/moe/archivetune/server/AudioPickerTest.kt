package moe.archivetune.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPickerTest {
    private fun candidate(
        itag: Int,
        mimeType: String,
        bitrate: Int,
        url: String = "https://example.test/$itag",
    ) = AudioCandidate(itag, mimeType, bitrate, url, null, null)

    @Test
    fun prefersAacOverOpusForPlayerCompatibility() {
        val best =
            AudioPicker.best(
                listOf(
                    candidate(251, "audio/webm; codecs=\"opus\"", 160_000),
                    candidate(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000),
                ),
            )
        assertEquals(140, best?.itag)
    }

    @Test
    fun picksTheHighestBitrateWithinACodec() {
        val best =
            AudioPicker.best(
                listOf(
                    candidate(139, "audio/mp4; codecs=\"mp4a.40.5\"", 50_000),
                    candidate(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000),
                ),
            )
        assertEquals(140, best?.itag)
    }

    @Test
    fun ignoresVideoAndBlankUrls() {
        val best =
            AudioPicker.best(
                listOf(
                    candidate(137, "video/mp4; codecs=\"avc1\"", 4_000_000),
                    candidate(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000, url = ""),
                ),
            )
        assertNull(best)
    }
}
