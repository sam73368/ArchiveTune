/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object CrossfadePolicy {
    fun outgoingGain(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return cos(clamped.toDouble() * (PI / 2.0)).toFloat()
    }

    fun incomingGain(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return sin(clamped.toDouble() * (PI / 2.0)).toFloat()
    }

    fun gentleIncomingGain(progress: Float): Float {
        val base = incomingGain(progress)
        return base * base
    }

    fun outgoingVolume(
        progress: Float,
        baseVolume: Float,
        maxGain: Float,
    ): Float = (baseVolume * outgoingGain(progress)).coerceIn(0f, maxGain)

    fun incomingVolume(
        progress: Float,
        baseVolume: Float,
        maxGain: Float,
    ): Float = (baseVolume * incomingGain(progress)).coerceIn(0f, maxGain)

    fun gentleIncomingVolume(
        progress: Float,
        baseVolume: Float,
        maxGain: Float,
    ): Float = (baseVolume * gentleIncomingGain(progress)).coerceIn(0f, maxGain)

    fun resolveTargetIndex(
        repeatOne: Boolean,
        currentIndex: Int,
        nextIndex: Int,
        itemCount: Int,
        unsetIndex: Int,
    ): Int? {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
        val target = if (repeatOne) currentIndex else nextIndex
        if (target == unsetIndex || target !in 0 until itemCount) return null
        if (!repeatOne && target == currentIndex) return null
        return target
    }

    fun effectiveDurationMs(
        requestedMs: Long,
        trackDurationMs: Long,
        endGuardMs: Long,
        minDurationMs: Long,
        unsetTime: Long,
    ): Long? {
        if (trackDurationMs == unsetTime || trackDurationMs <= 0L) return null
        val maxDuration = trackDurationMs - endGuardMs
        if (maxDuration < minDurationMs) return null
        return requestedMs.coerceIn(minDurationMs, maxDuration)
    }

    data class SmartWindow(
        val startMs: Long,
        val fadeMs: Long,
    )

    fun lastSustainedIndex(
        curve: List<Float>,
        threshold: Float,
        fromIndex: Int,
        toIndex: Int,
    ): Int {
        val last = toIndex.coerceAtMost(curve.lastIndex)
        var index = last
        while (index >= fromIndex.coerceAtLeast(0)) {
            val windowStart = (index - 3).coerceAtLeast(0)
            var hits = 0
            for (i in windowStart..index) {
                if (curve[i] >= threshold) hits++
            }
            if (curve[index] >= threshold && hits >= 3) return index
            index--
        }
        return -1
    }

    fun hasSustainedActivity(
        curve: List<Float>,
        threshold: Float,
        fromIndex: Int,
        toIndex: Int,
    ): Boolean = lastSustainedIndex(curve, threshold, fromIndex, toIndex) >= 0

    fun smartWindow(
        durationMs: Long,
        requestedFadeMs: Long,
        endGuardMs: Long,
        minFadeMs: Long,
        contentEndMs: Long,
        finalFadeOnsetMs: Long,
        lastVocalEndMs: Long,
        incomingStartsWithVoice: Boolean,
        voiceIntroMaxFadeMs: Long,
    ): SmartWindow? {
        if (durationMs <= 0L || requestedFadeMs <= 0L) return null
        val hardEnd = durationMs - endGuardMs
        if (hardEnd < minFadeMs) return null
        val end =
            if (contentEndMs in (durationMs / 2)..hardEnd) {
                (contentEndMs + 250L).coerceAtMost(hardEnd)
            } else {
                hardEnd
            }
        val hasOwnFadeOut = finalFadeOnsetMs in 1L until (end - 1_000L)
        val vocalEnd = lastVocalEndMs.coerceIn(0L, end)
        val protectedUntil = if (hasOwnFadeOut) minOf(vocalEnd, finalFadeOnsetMs) else vocalEnd
        val maxFade = if (incomingStartsWithVoice) minOf(requestedFadeMs, voiceIntroMaxFadeMs) else requestedFadeMs
        var start = maxOf(end - maxFade, protectedUntil)
        if (end - start < minFadeMs) start = end - minFadeMs
        if (start < 0L) return null
        return SmartWindow(startMs = start, fadeMs = end - start)
    }

    fun incomingCueMs(audibleStartMs: Long): Long =
        if (audibleStartMs in 1_000L..20_000L) audibleStartMs - 500L else 0L

    data class ReadinessSnapshot(
        val isReady: Boolean,
        val isIdle: Boolean,
        val isEnded: Boolean,
        val hasError: Boolean,
        val bufferedEnough: Boolean,
    )

    fun isReadyForFadeStart(snapshot: ReadinessSnapshot): Boolean =
        !snapshot.hasError && !snapshot.isEnded && snapshot.isReady && snapshot.bufferedEnough

    fun mustAbortReadiness(snapshot: ReadinessSnapshot): Boolean = snapshot.hasError || snapshot.isEnded

    private val continuousAlbumWords =
        listOf(
            "live", "concert", "unplugged", "en vivo", "ao vivo", "en direct", "en concierto", "in concert",
            "mixed", "mix", "megamix", "continuous", "nonstop", "non stop", "medley", "dj set", "mixtape",
        )

    fun albumLooksContinuous(albumTitle: String?): Boolean {
        val text =
            " " +
                java.text.Normalizer
                    .normalize(albumTitle.orEmpty(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("""\p{Mn}+"""), "")
                    .lowercase(java.util.Locale.ROOT)
                    .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                    .trim() +
                " "
        return continuousAlbumWords.any { " $it " in text }
    }

    data class AudioAdvancementSnapshot(
        val isReady: Boolean,
        val isPlaying: Boolean,
        val hasError: Boolean,
        val positionMs: Long,
        val previousPositionMs: Long,
    )

    fun hasAudioAdvanced(snapshot: AudioAdvancementSnapshot): Boolean =
        !snapshot.hasError &&
            snapshot.isReady &&
            snapshot.isPlaying &&
            snapshot.positionMs > snapshot.previousPositionMs

    data class PromotionSnapshot(
        val generationMatches: Boolean,
        val targetIndex: Int,
        val hasError: Boolean,
        val isIdle: Boolean,
        val isEnded: Boolean,
        val unsetIndex: Int,
    )

    fun mayPromote(snapshot: PromotionSnapshot): Boolean =
        snapshot.generationMatches &&
            snapshot.targetIndex != snapshot.unsetIndex &&
            !snapshot.hasError &&
            !snapshot.isIdle &&
            !snapshot.isEnded

    /** True when the incoming player already holds exactly the session's queue, playing the same slot. */
    fun queuesMatch(
        sourceIds: List<String>,
        incomingIds: List<String>,
        sourcePlayingIndex: Int,
        incomingPlayingIndex: Int,
    ): Boolean = sourcePlayingIndex == incomingPlayingIndex && sourceIds == incomingIds

    /** A shuffle order is valid for [itemCount] items when it lists every index exactly once. */
    fun isShuffleOrder(
        order: IntArray,
        itemCount: Int,
    ): Boolean = order.size == itemCount && order.sortedArray().withIndex().all { (index, value) -> index == value }
}
