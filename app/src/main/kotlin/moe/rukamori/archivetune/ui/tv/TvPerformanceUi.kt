/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.Preferences
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.constants.TvPerformanceModeKey
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.TvPerformance
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun rememberTvPerformanceMode(): Boolean {
    val tv = LocalTvLayout.current
    val enabled by rememberPreference(TvPerformanceModeKey, defaultValue = true)
    return tv && enabled
}

@Composable
fun rememberTvForcedLock(key: Preferences.Key<*>): Boolean {
    val performance = rememberTvPerformanceMode()
    return performance && TvPerformance.isForcedKey(key)
}

@Composable
fun TvArtworkBackdrop(
    artworkUrl: String?,
    alpha: Float,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val performance = rememberTvPerformanceMode()
    AsyncImage(
        model =
            if (performance) {
                artworkUrl?.resize(160, 160)
            } else {
                artworkUrl?.resize(640, 640, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal)
            },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .then(if (performance) Modifier else Modifier.blur(blurRadius)),
    )
}
