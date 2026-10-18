/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalTvLayout = staticCompositionLocalOf { false }

class TvFocusState {
    var focused by mutableStateOf(false)
}

@Composable
fun rememberTvFocusState(): TvFocusState = remember { TvFocusState() }

fun Modifier.tvFocusTracker(state: TvFocusState): Modifier = onFocusChanged { state.focused = it.hasFocus }

@Composable
fun Modifier.tvFocusScale(
    state: TvFocusState,
    focusedScale: Float = TvTokens.FocusScale,
): Modifier {
    val scale by animateFloatAsState(if (state.focused) focusedScale else 1f, tween(150), label = "tvFocusScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.tvFocusRing(
    state: TvFocusState,
    corner: Dp,
    width: Dp = 2.5.dp,
): Modifier =
    drawWithContent {
        drawContent()
        if (state.focused) {
            val stroke = width.toPx()
            val gap = 1.5.dp.toPx()
            val inset = stroke / 2 + gap
            drawRoundRect(
                color = TvTokens.FocusRing,
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + inset * 2, size.height + inset * 2),
                cornerRadius = CornerRadius(corner.toPx() + inset),
                style = Stroke(width = stroke),
            )
        }
    }
