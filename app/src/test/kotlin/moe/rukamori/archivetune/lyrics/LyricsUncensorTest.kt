/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUncensorTest {
    @Test
    fun `masked words with a unique match are restored`() {
        assertEquals("what the fuck", LyricsUncensor.restore("what the f***"))
        assertEquals("Shit happens", LyricsUncensor.restore("S*** happens"))
        assertEquals("a bitch", LyricsUncensor.restore("a b****"))
        assertEquals("fucking", LyricsUncensor.restore("f**king"))
    }

    @Test
    fun `fully masked or ambiguous tokens are left alone`() {
        assertEquals("****", LyricsUncensor.restore("****"))
        assertEquals("d***", LyricsUncensor.restore("d***"))
    }

    @Test
    fun `markdown emphasis and plain text are untouched`() {
        assertEquals("*chorus* again", LyricsUncensor.restore("*chorus* again"))
        assertEquals("nothing to do", LyricsUncensor.restore("nothing to do"))
    }

    @Test
    fun `remaining masked words are counted`() {
        assertEquals(0, LyricsUncensor.maskedWordCount("clean line"))
        assertTrue(LyricsUncensor.maskedWordCount("d*** and x***") >= 1)
    }
}
