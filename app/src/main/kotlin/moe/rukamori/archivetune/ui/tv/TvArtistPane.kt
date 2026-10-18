/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R

val TvArtistPaneWidth = 300.dp

@Composable
fun TvArtistSidePane(
    thumbnailUrl: String?,
    name: String,
    audience: String,
    catalog: String,
    isSubscribed: Boolean,
    canPlay: Boolean,
    canShuffle: Boolean,
    canRadio: Boolean,
    topPadding: Dp,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier =
            modifier
                .width(TvArtistPaneWidth)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.22f))
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 24.dp, top = topPadding + 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .shadow(24.dp, CircleShape)
                    .clip(CircleShape)
                    .background(TvTokens.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(180.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = TvTokens.OnSurfaceMuted,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Text(
            text = name,
            style = TvTokens.Display,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (audience.isNotEmpty()) {
            Text(text = audience, style = TvTokens.Body, color = Color.White.copy(alpha = 0.75f), maxLines = 2)
        }
        if (catalog.isNotEmpty()) {
            Text(text = catalog, style = TvTokens.Caption, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canPlay) {
                TvPillButton(onClick = onPlay, style = TvPillStyle.Primary, height = 36.dp) { color ->
                    Icon(painterResource(R.drawable.play), null, tint = color, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.play), style = TvTokens.BodyBold, color = color)
                }
            }
            if (canShuffle) {
                TvPillButton(onClick = onShuffle, height = 36.dp, horizontalPadding = 12.dp) { color ->
                    Icon(painterResource(R.drawable.shuffle), stringResource(R.string.tv_shuffle), tint = color, modifier = Modifier.size(16.dp))
                }
            }
            if (canRadio) {
                TvPillButton(onClick = onRadio, height = 36.dp, horizontalPadding = 12.dp) { color ->
                    Icon(painterResource(R.drawable.radio), stringResource(R.string.start_radio), tint = color, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvPillButton(onClick = onToggleSubscribe, height = 32.dp, horizontalPadding = 14.dp) { color ->
                Icon(
                    painterResource(if (isSubscribed) R.drawable.favorite else R.drawable.favorite_border),
                    null,
                    tint = if (isSubscribed && color != TvTokens.Background) accent else color,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe),
                    style = TvTokens.Caption,
                    color = color,
                )
            }
            TvPillButton(onClick = onShare, height = 32.dp, horizontalPadding = 12.dp) { color ->
                Icon(painterResource(R.drawable.share), stringResource(R.string.share), tint = color, modifier = Modifier.size(14.dp))
            }
            TvPillButton(onClick = onMore, height = 32.dp, horizontalPadding = 12.dp) { color ->
                Icon(painterResource(R.drawable.more_vert), stringResource(R.string.tv_more), tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}
