/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusPageNoiseTest {
    private val junk =
        "PortuguêsTürkçeDeutschSong Name Lyrics“Song Name” is the first single of an album.\n" +
            "[Intro]\nfirst line\nsecond line"

    @Test
    fun `header and description before the first section are removed`() {
        assertEquals("[Intro]\nfirst line\nsecond line", GeniusPageNoise.stripHeader(junk))
    }

    @Test
    fun `lyrics that start with a section marker are untouched`() {
        val clean = "[Verse 1]\nhello\nworld"
        assertEquals(clean, GeniusPageNoise.stripHeader(clean))
    }

    @Test
    fun `plain lyrics without a header word are untouched`() {
        val plain = "intro line\n[Chorus]\nsing along"
        assertEquals(plain, GeniusPageNoise.stripHeader(plain))
    }

    @Test
    fun `glued header without section markers is detected`() {
        assertTrue(GeniusPageNoise.hasGluedHeader("DeutschSong Name Lyrics“Song Name” is a single"))
        assertFalse(GeniusPageNoise.hasGluedHeader("I write my lyrics, you sing them"))
    }
}
