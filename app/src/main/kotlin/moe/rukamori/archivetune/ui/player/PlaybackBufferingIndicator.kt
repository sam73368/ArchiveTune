/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.playback.PlaybackLoadActivity

private const val FILL_TARGET_MS = 2_500f
private const val POLL_MS = 250L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackBufferingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val player = LocalPlayerConnection.current?.player
    var fraction by remember { mutableFloatStateOf(0f) }
    var stalled by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            buffering = player?.playbackState == Player.STATE_BUFFERING
            val buffered = player?.totalBufferedDuration ?: 0L
            fraction = (buffered / FILL_TARGET_MS).coerceIn(0f, 1f)
            val lastSignal = maxOf(startedAt, PlaybackLoadActivity.lastSignalAt(now))
            stalled = buffering && now - lastSignal > PlaybackLoadActivity.STALL_AFTER_MS
            delay(POLL_MS)
        }
    }

    val ringColor =
        when {
            stalled -> MaterialTheme.colorScheme.error
            color.isSpecified -> color
            else -> MaterialTheme.colorScheme.primary
        }
    val shown by animateFloatAsState(targetValue = fraction, label = "bufferFill")

    if (stalled) {
        val pulse by rememberInfiniteTransition(label = "stallPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "stallAlpha",
        )
        CircularWavyProgressIndicator(
            progress = { shown.coerceAtLeast(0.06f) },
            modifier = modifier.alpha(pulse),
            color = ringColor,
            trackColor = ringColor.copy(alpha = 0.25f),
        )
    } else if (buffering && fraction > 0f) {
        CircularWavyProgressIndicator(
            progress = { shown },
            modifier = modifier,
            color = ringColor,
            trackColor = ringColor.copy(alpha = 0.25f),
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = ringColor,
        )
    }
}
