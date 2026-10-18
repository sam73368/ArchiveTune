/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.debounce
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize

@Stable
class TvFocusedPreview {
    var artworkUrl by mutableStateOf<String?>(null)
}

val LocalTvFocusedPreview = staticCompositionLocalOf<TvFocusedPreview?> { null }

@Composable
fun Modifier.tvPreviewOnFocus(artworkUrl: String?): Modifier {
    val preview = LocalTvFocusedPreview.current ?: return this
    return onFocusChanged { if (it.hasFocus && artworkUrl != null) preview.artworkUrl = artworkUrl }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun BoxScope.TvHomeBackdrop(
    preview: TvFocusedPreview,
    fallbackArtworkUrl: String?,
) {
    var shown by androidx.compose.runtime.remember { mutableStateOf(preview.artworkUrl ?: fallbackArtworkUrl) }
    LaunchedEffect(preview, fallbackArtworkUrl) {
        snapshotFlow { preview.artworkUrl ?: fallbackArtworkUrl }
            .debounce(220)
            .collect { shown = it }
    }
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.62f)
                .height(420.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = size.width * 0.45f),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        Crossfade(targetState = shown, animationSpec = tween(600), label = "tvHomeBackdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = url.resize(720, 720, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.42f },
                )
            }
        }
    }
}
