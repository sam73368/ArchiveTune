/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlProbeTest {
    private val base = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&itag=251&c=WEB_REMIX"

    @Test
    fun classifiesStatusCodes() {
        assertEquals(StreamUrlProbe.Result.PLAYABLE, StreamUrlProbe.classify(206))
        assertEquals(StreamUrlProbe.Result.PLAYABLE, StreamUrlProbe.classify(200))
        assertEquals(StreamUrlProbe.Result.REJECTED, StreamUrlProbe.classify(403))
        assertEquals(StreamUrlProbe.Result.REJECTED, StreamUrlProbe.classify(410))
        assertEquals(StreamUrlProbe.Result.UNKNOWN, StreamUrlProbe.classify(503))
        assertEquals(StreamUrlProbe.Result.UNKNOWN, StreamUrlProbe.classify(429))
    }

    @Test
    fun onlyGooglevideoMediaUrlsAreProbed() {
        assertTrue(StreamUrlProbe.isProbeable(base))
        assertFalse(StreamUrlProbe.isProbeable("https://manifest.googlevideo.com/api/manifest/hls_playlist/id/1"))
        assertFalse(StreamUrlProbe.isProbeable("https://example.com/audio.mp3"))
        assertFalse(StreamUrlProbe.isProbeable("tidal-dash://abc"))
    }

    @Test
    fun replacesAddsAndRemovesThePoToken() {
        val withPot = "$base&pot=OLD&sig=x"
        assertEquals("OLD", StreamUrlProbe.potOf(withPot))
        assertEquals("$base&pot=NEW&sig=x", StreamUrlProbe.withPoToken(withPot, "NEW"))
        assertEquals("$base&pot=A%3D", StreamUrlProbe.withPoToken(base, "A="))
        assertEquals("$base&sig=x", StreamUrlProbe.withoutPoToken(withPot))
        assertEquals(
            "https://r.googlevideo.com/videoplayback?itag=1",
            StreamUrlProbe.withoutPoToken("https://r.googlevideo.com/videoplayback?pot=X&itag=1"),
        )
        assertNull(StreamUrlProbe.potOf(base))
    }

    @Test
    fun probeNeverDownloadsAWholeRangeQuery() {
        assertEquals("$base&range=0-1", StreamUrlProbe.probeUrlFor("$base&range=0-9999999"))
        assertEquals(base, StreamUrlProbe.probeUrlFor(base))
    }
}
