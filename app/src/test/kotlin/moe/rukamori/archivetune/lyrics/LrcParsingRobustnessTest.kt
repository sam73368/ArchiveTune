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

class LrcParsingRobustnessTest {
    @Test
    fun `one digit fractions are read as tenths of a second`() {
        val lyrics = "[00:12.5]hello\n[00:15.25]world\n[00:20.125]again"
        assertTrue(LyricsUtils.isLineSyncedLrc(lyrics))
        assertEquals(listOf(12_500L, 15_250L, 20_125L), LyricsUtils.parseLyrics(lyrics).map { it.time })
    }

    @Test
    fun `positive offset tag shows the lyrics earlier`() {
        val lyrics = "[offset:+500]\n[00:10.00]one\n[00:20.00]two"
        assertEquals(listOf(9_500L, 19_500L), LyricsUtils.parseLyrics(lyrics).map { it.time })
    }

    @Test
    fun `negative offset tag shows the lyrics later and never goes below zero`() {
        assertEquals(
            listOf(10_300L),
            LyricsUtils.parseLyrics("[offset:-300]\n[00:10.00]one").map { it.time },
        )
        assertEquals(
            listOf(0L),
            LyricsUtils.parseLyrics("[offset:2000]\n[00:01.00]one").map { it.time },
        )
    }

    @Test
    fun `offset tag also moves word timings`() {
        val entry = LyricsUtils.parseLyrics("[offset:1000]\n[00:10.00]<00:10.00>hi <00:10.50>there").single()
        assertEquals(9_000L, entry.time)
        assertEquals(9.0, entry.words!!.first().startTime, 0.001)
    }

    @Test
    fun `garbage never throws`() {
        assertTrue(LyricsUtils.parseTtml("<tt><body><div><p begin=\"x\"").isEmpty())
        LyricsUtils.parseLyrics("[99:99.999]\n[:]\n[00:00]")
    }
}
