/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SpotifyOnlyBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(SpotifyGreen.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.spotify_icon),
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(R.string.spotify_only_badge),
            style = MaterialTheme.typography.labelSmall,
            color = SpotifyGreen,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
