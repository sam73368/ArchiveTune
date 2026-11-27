/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.Player

internal enum class RecoverySource { PLEX, LOCAL, STREAM }

internal data class PlaybackRecoverySnapshot(
    val now: Long,
    val mediaId: String?,
    val source: RecoverySource,
    val casting: Boolean,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val isPlaying: Boolean,
    val positionMs: Long,
    val lastDataAt: Long,
    val crossfading: Boolean,
    val hasNext: Boolean,
)

internal sealed interface PlaybackRecoveryAction {
    data object None : PlaybackRecoveryAction

    data class FlushOutput(
        val mediaId: String,
        val positionMs: Long,
    ) : PlaybackRecoveryAction

    data class RebuildOutput(
        val mediaId: String,
        val positionMs: Long,
    ) : PlaybackRecoveryAction

    data class RestartLoad(
        val mediaId: String,
        val attempt: Int,
    ) : PlaybackRecoveryAction

    data class ReconnectPlex(
        val mediaId: String,
        val positionMs: Long,
        val attempt: Int,
    ) : PlaybackRecoveryAction

    data class RefreshStream(
        val mediaId: String,
        val positionMs: Long,
        val attempt: Int,
    ) : PlaybackRecoveryAction

    data class SkipStalledSong(
        val mediaId: String,
        val attempts: Int,
    ) : PlaybackRecoveryAction
}

internal class PlaybackRecoveryPolicy(
    private val timing: Timing = Timing(),
) {
    data class Timing(
        val initialPhaseMs: Long = 5_000L,
        val initialStallMs: Long = 10_000L,
        val initialSilenceMs: Long = 6_000L,
        val initialHardStallMs: Long = 20_000L,
        val maxRestarts: Int = 2,
        val skipAfterRestartMs: Long = 15_000L,
        val plexKickMs: Long = 8_000L,
        val streamKickMs: Long = 10_000L,
        val outputStallMs: Long = 2_500L,
        val outputMaxKicks: Int = 2,
    )

    private var mediaId: String? = null
    private var bufferingSince = NOT_SET
    private var restarts = 0
    private var lastRestartAt = NOT_SET
    private var skipIssued = false
    private var plexKicks = 0
    private var lastPlexKickAt = NOT_SET
    private var streamKicks = 0
    private var lastStreamKickAt = NOT_SET
    private var outputKicks = 0
    private var lastPositionMs = NOT_SET
    private var lastAdvanceAt = NOT_SET

    val restartAttempts: Int
        get() = restarts

    fun reset() {
        mediaId = null
        bufferingSince = NOT_SET
        restarts = 0
        lastRestartAt = NOT_SET
        skipIssued = false
        resetBufferingKicks()
        outputKicks = 0
        lastPositionMs = NOT_SET
        lastAdvanceAt = NOT_SET
    }

    fun evaluate(snapshot: PlaybackRecoverySnapshot): PlaybackRecoveryAction {
        val id = snapshot.mediaId
        if (id != mediaId) {
            reset()
            mediaId = id
        }
        if (id == null || snapshot.casting) {
            bufferingSince = NOT_SET
            markOutputBaseline(snapshot)
            return PlaybackRecoveryAction.None
        }
        val buffering = snapshot.playbackState == Player.STATE_BUFFERING && snapshot.playWhenReady
        if (!buffering) bufferingSince = NOT_SET
        if (snapshot.playbackState == Player.STATE_READY) {
            resetBufferingKicks()
            return evaluateOutput(snapshot, id)
        }
        markOutputBaseline(snapshot)
        if (!buffering) return PlaybackRecoveryAction.None
        if (bufferingSince == NOT_SET) bufferingSince = snapshot.now
        return if (snapshot.positionMs < timing.initialPhaseMs) {
            evaluateInitialBuffer(snapshot, id)
        } else {
            evaluateMidSong(snapshot, id)
        }
    }

    private fun evaluateOutput(
        snapshot: PlaybackRecoverySnapshot,
        id: String,
    ): PlaybackRecoveryAction {
        if (!snapshot.isPlaying || snapshot.crossfading) {
            markOutputBaseline(snapshot)
            return PlaybackRecoveryAction.None
        }
        if (snapshot.positionMs != lastPositionMs) {
            if (lastPositionMs != NOT_SET && snapshot.positionMs > lastPositionMs) outputKicks = 0
            markOutputBaseline(snapshot)
            return PlaybackRecoveryAction.None
        }
        if (snapshot.now - lastAdvanceAt < timing.outputStallMs) return PlaybackRecoveryAction.None
        if (outputKicks >= timing.outputMaxKicks) return PlaybackRecoveryAction.None
        outputKicks++
        lastAdvanceAt = snapshot.now
        return if (outputKicks == 1) {
            PlaybackRecoveryAction.FlushOutput(id, snapshot.positionMs)
        } else {
            PlaybackRecoveryAction.RebuildOutput(id, snapshot.positionMs)
        }
    }

    private fun evaluateInitialBuffer(
        snapshot: PlaybackRecoverySnapshot,
        id: String,
    ): PlaybackRecoveryAction {
        val now = snapshot.now
        if (restarts >= timing.maxRestarts) {
            if (!skipIssued && lastRestartAt != NOT_SET && now - lastRestartAt >= timing.skipAfterRestartMs) {
                skipIssued = true
                if (snapshot.hasNext) return PlaybackRecoveryAction.SkipStalledSong(id, restarts)
            }
        } else {
            val bufferingFor = now - bufferingSince
            val silentFor = now - snapshot.lastDataAt
            if (bufferingFor >= timing.initialStallMs &&
                (silentFor >= timing.initialSilenceMs || bufferingFor >= timing.initialHardStallMs)
            ) {
                restarts++
                lastRestartAt = now
                bufferingSince = now
                return PlaybackRecoveryAction.RestartLoad(id, restarts)
            }
        }
        return if (snapshot.source == RecoverySource.PLEX) plexKick(snapshot, id) else PlaybackRecoveryAction.None
    }

    private fun evaluateMidSong(
        snapshot: PlaybackRecoverySnapshot,
        id: String,
    ): PlaybackRecoveryAction =
        when (snapshot.source) {
            RecoverySource.PLEX -> plexKick(snapshot, id)
            RecoverySource.STREAM -> streamKick(snapshot, id)
            RecoverySource.LOCAL -> PlaybackRecoveryAction.None
        }

    private fun plexKick(
        snapshot: PlaybackRecoverySnapshot,
        id: String,
    ): PlaybackRecoveryAction {
        val now = snapshot.now
        if (now - snapshot.lastDataAt <= timing.plexKickMs) return PlaybackRecoveryAction.None
        if (lastPlexKickAt != NOT_SET && now - lastPlexKickAt < timing.plexKickMs) return PlaybackRecoveryAction.None
        plexKicks++
        lastPlexKickAt = now
        return PlaybackRecoveryAction.ReconnectPlex(id, snapshot.positionMs, plexKicks)
    }

    private fun streamKick(
        snapshot: PlaybackRecoverySnapshot,
        id: String,
    ): PlaybackRecoveryAction {
        val now = snapshot.now
        if (now - snapshot.lastDataAt <= timing.streamKickMs) return PlaybackRecoveryAction.None
        if (lastStreamKickAt != NOT_SET && now - lastStreamKickAt < timing.streamKickMs) return PlaybackRecoveryAction.None
        streamKicks++
        lastStreamKickAt = now
        return PlaybackRecoveryAction.RefreshStream(id, snapshot.positionMs, streamKicks)
    }

    private fun resetBufferingKicks() {
        plexKicks = 0
        lastPlexKickAt = NOT_SET
        streamKicks = 0
        lastStreamKickAt = NOT_SET
    }

    private fun markOutputBaseline(snapshot: PlaybackRecoverySnapshot) {
        lastPositionMs = snapshot.positionMs
        lastAdvanceAt = snapshot.now
    }

    private companion object {
        const val NOT_SET = Long.MIN_VALUE
    }
}
