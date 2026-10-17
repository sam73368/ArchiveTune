/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TruncatedLyricsTest {
    private val partial = "[00:10.00]one\n[00:30.00]two\n[01:00.00]three"
    private val full = partial + "\n[02:20.00]four\n[03:05.00]five"

    @Test
    fun `last timed line is read from line synced lyrics`() {
        assertEquals(60_000L, LyricsUtils.lastTimedLineMs(partial))
    }

    @Test
    fun `unsynced lyrics have no timing and are never truncated`() {
        assertNull(LyricsUtils.lastTimedLineMs("just\nplain\ntext"))
        assertFalse(LyricsUtils.isLikelyTruncated("just\nplain\ntext", 200))
    }

    @Test
    fun `lyrics ending long before the song are truncated`() {
        assertTrue(LyricsUtils.isLikelyTruncated(partial, 200))
    }

    @Test
    fun `lyrics reaching the end are complete`() {
        assertFalse(LyricsUtils.isLikelyTruncated(full, 200))
    }

    @Test
    fun `unknown duration is never flagged`() {
        assertFalse(LyricsUtils.isLikelyTruncated(partial, 0))
    }
}
