/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.player

/** What the platform player needs to know about a track. */
data class PlayerTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    /** 0 when unknown. */
    val durationMs: Long,
)

/**
 * Platform audio engine. On iOS it is implemented in Swift (AVPlayer + YouTubeKit stream
 * extraction + lock-screen / Control Center integration) and handed to the shared code.
 * All calls happen on the main thread.
 */
interface AudioEngine {
    fun setListener(listener: AudioEngineListener)

    /** Resolves the stream for [track] and starts it (or just prepares it when [autoplay] is false). */
    fun load(
        track: PlayerTrack,
        autoplay: Boolean,
    )

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    /** Tells the lock screen whether next/previous are available. */
    fun setQueueNavigation(
        hasNext: Boolean,
        hasPrevious: Boolean,
    )
}

/** Events reported by the platform engine (on the main thread). */
interface AudioEngineListener {
    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        isBuffering: Boolean,
    )

    fun onProgress(
        positionMs: Long,
        durationMs: Long,
    )

    fun onTrackEnded()

    fun onError(message: String)

    /** Lock screen / headphones "next". */
    fun onRemoteNext()

    /** Lock screen / headphones "previous". */
    fun onRemotePrevious()
}
