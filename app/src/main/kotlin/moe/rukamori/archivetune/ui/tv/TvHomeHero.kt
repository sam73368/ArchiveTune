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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R

@Composable
fun TvHomeHero(
    artworkUrl: String?,
    overline: String,
    title: String,
    subtitle: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = TvTokens.ScreenPaddingHorizontal, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .shadow(24.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvTokens.SurfaceHigh),
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(180.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = overline.uppercase(), style = TvTokens.Overline, color = accent)
            Text(
                text = title,
                style = TvTokens.Display,
                color = TvTokens.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TvTokens.Body,
                color = TvTokens.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvPillButton(onClick = onPrimary, style = TvPillStyle.Primary, height = 36.dp) { color ->
                    Icon(painterResource(R.drawable.play), null, tint = color, modifier = Modifier.size(16.dp))
                    Text(primaryLabel, style = TvTokens.BodyBold, color = color)
                }
                TvPillButton(onClick = onSecondary, style = TvPillStyle.Tonal, height = 36.dp) { color ->
                    Icon(painterResource(R.drawable.shuffle), null, tint = color, modifier = Modifier.size(16.dp))
                    Text(secondaryLabel, style = TvTokens.BodyBold, color = color)
                }
            }
        }
    }
}
