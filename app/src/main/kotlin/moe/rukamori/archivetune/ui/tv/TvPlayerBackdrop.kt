/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.TvPlayerBackground
import moe.rukamori.archivetune.constants.TvPlayerBackgroundKey
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberEnumPreference

@Immutable
class TvBackdropColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private val TvBackdropFallback =
    TvBackdropColors(
        primary = Color(0xFF3B4366),
        secondary = Color(0xFF262B45),
        tertiary = Color(0xFF171A2B),
    )

private val TvBackdropCache =
    object : LinkedHashMap<String, TvBackdropColors>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TvBackdropColors>): Boolean = size > 24
    }

private fun Color.withHsv(
    saturation: ClosedFloatingPointRange<Float>,
    value: Float,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(saturation.start, saturation.endInclusive)
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun buildBackdropColors(palette: Palette): TvBackdropColors {
    val lead =
        palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
    val second =
        palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.darkMutedSwatch ?: lead
    val third =
        palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: second
    if (lead == null) return TvBackdropFallback
    return TvBackdropColors(
        primary = Color(lead.rgb).withHsv(0.38f..0.85f, 0.46f),
        secondary = Color((second ?: lead).rgb).withHsv(0.3f..0.8f, 0.26f),
        tertiary = Color((third ?: lead).rgb).withHsv(0.2f..0.7f, 0.11f),
    )
}

@Composable
fun rememberTvBackdropColors(artworkUrl: String?): TvBackdropColors {
    val context = LocalContext.current
    var colors by
        remember(artworkUrl) {
            mutableStateOf(artworkUrl?.let { synchronized(TvBackdropCache) { TvBackdropCache[it] } })
        }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank() || colors != null) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(artworkUrl.resize(640, 640, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal))
                .size(64, 64)
                .allowHardware(false)
                .build()
        val extracted =
            withContext(Dispatchers.IO) {
                val result = runCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult
                val bitmap = result?.image?.toBitmap() ?: return@withContext null
                runCatching { buildBackdropColors(Palette.from(bitmap).maximumColorCount(16).generate()) }.getOrNull()
            } ?: return@LaunchedEffect
        synchronized(TvBackdropCache) { TvBackdropCache[artworkUrl] = extracted }
        colors = extracted
    }
    return colors ?: TvBackdropFallback
}

@Composable
fun TvPlayerBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val style by rememberEnumPreference(TvPlayerBackgroundKey, TvPlayerBackground.GRADIENT)
    if (style == TvPlayerBackground.COVER) {
        Box(modifier = modifier.fillMaxSize()) {
            TvArtworkBackdrop(artworkUrl = artworkUrl, alpha = 0.55f, blurRadius = 80.dp)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        return
    }
    if (style == TvPlayerBackground.BLACK) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF05060A)))
        return
    }
    val target = rememberTvBackdropColors(artworkUrl)
    val primary by animateColorAsState(target.primary, tween(900), label = "tvBackdropPrimary")
    val secondary by animateColorAsState(target.secondary, tween(900), label = "tvBackdropSecondary")
    val tertiary by animateColorAsState(target.tertiary, tween(900), label = "tvBackdropTertiary")
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind {
                    when (style) {
                        TvPlayerBackground.AURORA -> {
                            drawRect(tertiary)
                            drawRect(
                                Brush.radialGradient(
                                    listOf(primary.copy(alpha = 0.85f), Color.Transparent),
                                    center = Offset(size.width * 0.12f, size.height * 0.18f),
                                    radius = size.width * 0.62f,
                                ),
                            )
                            drawRect(
                                Brush.radialGradient(
                                    listOf(secondary.copy(alpha = 0.95f), Color.Transparent),
                                    center = Offset(size.width * 0.88f, size.height * 0.82f),
                                    radius = size.width * 0.58f,
                                ),
                            )
                            drawRect(
                                Brush.radialGradient(
                                    listOf(primary.copy(alpha = 0.4f), Color.Transparent),
                                    center = Offset(size.width * 0.78f, size.height * 0.08f),
                                    radius = size.width * 0.4f,
                                ),
                            )
                        }

                        TvPlayerBackground.SOLID -> {
                            drawRect(secondary)
                            drawRect(
                                Brush.radialGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                    center = Offset(size.width * 0.35f, size.height * 0.4f),
                                    radius = size.width * 0.85f,
                                ),
                            )
                        }

                        else -> {
                            drawRect(
                                Brush.linearGradient(
                                    colorStops = arrayOf(0f to primary, 0.55f to secondary, 1f to tertiary),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height),
                                ),
                            )
                        }
                    }
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.38f)),
                        ),
                    )
                },
    )
}
