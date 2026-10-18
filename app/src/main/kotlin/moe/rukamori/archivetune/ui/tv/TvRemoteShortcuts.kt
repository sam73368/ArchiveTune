/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.PlayerConnection

private const val TvMediaSeekStepMs = 10_000L

private val TvDigitKeys =
    listOf(
        Key.One,
        Key.Two,
        Key.Three,
        Key.Four,
        Key.Five,
        Key.Six,
        Key.Seven,
        Key.Eight,
        Key.Nine,
    )

object TvRemoteShortcuts {
    fun handleMediaKey(
        event: KeyEvent,
        connection: PlayerConnection?,
    ): Boolean {
        val player = connection?.player ?: return false
        val action: (() -> Unit) =
            when (event.key) {
                Key.MediaPlayPause -> ({ player.togglePlayPause() })
                Key.MediaPlay -> ({ player.play() })
                Key.MediaPause -> ({ player.pause() })
                Key.MediaFastForward -> ({ seekBy(connection, TvMediaSeekStepMs) })
                Key.MediaRewind -> ({ seekBy(connection, -TvMediaSeekStepMs) })
                Key.MediaNext -> ({ connection.seekToNext() })
                Key.MediaPrevious -> ({ connection.seekToPrevious() })
                else -> return false
            }
        if (event.type == KeyEventType.KeyDown) action()
        return true
    }

    fun digitSlot(event: KeyEvent): Int? {
        val index = TvDigitKeys.indexOf(event.key)
        return if (index >= 0) index else null
    }

    private fun seekBy(
        connection: PlayerConnection,
        delta: Long,
    ) {
        val player = connection.player
        val duration = player.duration
        val target = player.currentPosition + delta
        val bounded = if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
        player.seekTo(bounded)
    }
}
