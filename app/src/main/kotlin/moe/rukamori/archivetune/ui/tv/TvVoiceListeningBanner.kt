/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
fun TvVoiceListeningBanner(listening: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "tvVoicePulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "tvVoicePulseScale",
    )
    Row(
        modifier =
            Modifier
                .padding(top = 32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(TvTokens.Surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        val s = if (listening) pulse else 1f
                        scaleX = s
                        scaleY = s
                    }.clip(CircleShape)
                    .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.mic), null, tint = TvTokens.Background, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(
                text = stringResource(if (listening) R.string.tv_voice_listening else R.string.tv_voice_starting),
                style = TvTokens.BodyBold,
                color = TvTokens.OnSurface,
            )
            Text(text = stringResource(R.string.tv_voice_hint), style = TvTokens.Caption, color = TvTokens.OnSurfaceMuted)
        }
    }
}
