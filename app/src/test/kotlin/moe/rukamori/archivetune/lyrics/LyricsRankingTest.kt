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

class LyricsRankingTest {
    private val plain = "First line of the song\nSecond line of the song\nThird line here"

    private val censoredSynced =
        (0 until 40).joinToString("\n") { index ->
            val seconds = index * 5
            "[%02d:%02d.00] Line %d f*** this".format(seconds / 60, seconds % 60, index)
        }

    private val synced =
        (0 until 40).joinToString("\n") { index ->
            val seconds = index * 5
            "[%02d:%02d.00] Line %d of the song".format(seconds / 60, seconds % 60, index)
        }

    private val partialSynced = "[00:05.00] Only the start\n[00:10.00] Then nothing"

    @Test
    fun syncedLyricsBeatPlainOnesEvenWhenListedLater() {
        val best =
            LyricsRanking.pickBest(
                listOf(LyricsRanking.Candidate("Genius", plain), LyricsRanking.Candidate("LrcLib", synced)),
                durationSeconds = 205,
            )
        assertEquals("LrcLib", best?.providerName)
    }

    @Test
    fun restorableCensorshipDoesNotDemoteSyncedLyrics() {
        val best =
            LyricsRanking.pickBest(
                listOf(LyricsRanking.Candidate("Genius", plain), LyricsRanking.Candidate("Musixmatch", censoredSynced)),
                durationSeconds = 205,
            )
        assertEquals("Musixmatch", best?.providerName)
    }

    @Test
    fun partialSyncedLyricsLoseToCompletePlainLyrics() {
        val best =
            LyricsRanking.pickBest(
                listOf(LyricsRanking.Candidate("KuGou", partialSynced), LyricsRanking.Candidate("Genius", plain)),
                durationSeconds = 240,
            )
        assertEquals("Genius", best?.providerName)
    }

    @Test
    fun tiersAndEmptyInput() {
        assertTrue(LyricsRanking.isSynced(synced))
        assertFalse(LyricsRanking.isSynced(plain))
        assertEquals(LyricsRanking.TIER_LINE_SYNCED, LyricsRanking.syncTier(synced))
        assertNull(LyricsRanking.pickBest(emptyList(), 200))
    }
}
