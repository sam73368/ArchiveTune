/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

object LyricsRanking {
    const val TIER_PLAIN = 0
    const val TIER_LINE_SYNCED = 1
    const val TIER_WORD_SYNCED = 2

    data class Candidate(
        val providerName: String,
        val lyrics: String,
    )

    fun syncTier(lyrics: String): Int =
        when {
            LyricsUtils.hasWordSyncedLyrics(lyrics) -> TIER_WORD_SYNCED
            LyricsUtils.lastTimedLineMs(lyrics) != null -> TIER_LINE_SYNCED
            else -> TIER_PLAIN
        }

    fun isSynced(lyrics: String): Boolean = syncTier(lyrics) > TIER_PLAIN

    fun residualMaskedWords(lyrics: String): Int = LyricsUncensor.maskedWordCount(LyricsUncensor.restore(lyrics))

    fun pickBest(
        candidates: List<Candidate>,
        durationSeconds: Int,
    ): Candidate? {
        if (candidates.isEmpty()) return null
        val complete =
            candidates.filter {
                !LyricsUtils.isLikelyTruncated(it.lyrics, durationSeconds) &&
                    !LyricsUtils.isLikelyTooLong(it.lyrics, durationSeconds)
            }
        val pool =
            if (complete.isNotEmpty()) {
                complete
            } else {
                listOf(candidates.maxBy { LyricsUtils.lastTimedLineMs(it.lyrics) ?: -1L })
            }
        val tiers = pool.associateWith { syncTier(it.lyrics) }
        val bestTier = tiers.values.max()
        val tierPool = pool.filter { tiers[it] == bestTier }
        val leastMasked = tierPool.minOf { residualMaskedWords(it.lyrics) }
        return tierPool.first { residualMaskedWords(it.lyrics) == leastMasked }
    }
}
