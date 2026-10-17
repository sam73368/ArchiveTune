/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Car mode: a stripped-down player with very large controls that can be hit at a glance.
 * It keeps the screen on while open and only drives the existing player, nothing else changes.
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.utils.makeTimeString

const val CAR_MODE_ROUTE = "car_mode"

@Composable
fun CarModeScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val liked = currentSong?.song?.liked == true

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playerConnection) {
        while (true) {
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = playerConnection.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
                    // Keep clear of the compact mini player that floats over every screen.
                    .padding(bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.car_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                BigRoundButton(
                    size = 64.dp,
                    iconRes = R.drawable.close,
                    contentDescription = stringResource(R.string.car_mode_exit),
                    onClick = { navController.navigateUp() },
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { playerConnection.player.togglePlayPause() },
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = mediaMetadata?.title.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(makeTimeString(position), style = MaterialTheme.typography.bodyLarge)
                Text(makeTimeString(duration), style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigRoundButton(
                    size = 96.dp,
                    iconRes = R.drawable.skip_previous,
                    contentDescription = null,
                    onClick = { playerConnection.seekToPrevious() },
                )
                BigRoundButton(
                    size = 128.dp,
                    iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription = null,
                    filled = true,
                    onClick = { playerConnection.player.togglePlayPause() },
                )
                BigRoundButton(
                    size = 96.dp,
                    iconRes = R.drawable.skip_next,
                    contentDescription = null,
                    onClick = { playerConnection.seekToNext() },
                )
            }

            Spacer(Modifier.height(12.dp))

            BigRoundButton(
                size = 72.dp,
                iconRes = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                contentDescription = null,
                onClick = { playerConnection.toggleLike() },
            )
        }
    }
}

@Composable
private fun BigRoundButton(
    size: Dp,
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
