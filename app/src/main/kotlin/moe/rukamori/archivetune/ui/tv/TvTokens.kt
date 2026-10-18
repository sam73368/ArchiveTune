/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TvTokens {
    val Background = Color(0xFF0B0C10)
    val RailBackground = Color(0xFF101218)
    val Surface = Color(0xFF16181F)
    val SurfaceHigh = Color(0xFF1F222B)
    val OnSurface = Color(0xFFF4F5F8)
    val OnSurfaceMuted = OnSurface.copy(alpha = 0.66f)
    val OnSurfaceFaint = OnSurface.copy(alpha = 0.5f)
    val Chip = OnSurface.copy(alpha = 0.1f)
    val Scrim = Color(0xB8050608)
    val FocusRing = OnSurface

    val RailWidth = 56.dp
    val ScreenPaddingHorizontal = 32.dp
    val ScreenPaddingVertical = 24.dp
    val CardCorner = 10.dp
    val FocusScale = 1.06f

    val Display = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
    val Body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    val BodyBold = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)
    val Overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
}

enum class TvPillStyle { Primary, Tonal, Text }

@Composable
fun TvPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvPillStyle = TvPillStyle.Tonal,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 22.dp,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val restingBackground =
        when (style) {
            TvPillStyle.Primary -> TvTokens.OnSurface
            TvPillStyle.Tonal -> TvTokens.Chip
            TvPillStyle.Text -> Color.Transparent
        }
    val restingContent =
        when (style) {
            TvPillStyle.Primary -> TvTokens.Background
            TvPillStyle.Tonal -> TvTokens.OnSurface
            TvPillStyle.Text -> TvTokens.OnSurface.copy(alpha = 0.8f)
        }
    val background by animateColorAsState(
        if (focused) TvTokens.OnSurface else restingBackground,
        tween(140),
        label = "tvPillBg",
    )
    val contentColor by animateColorAsState(
        if (focused) TvTokens.Background else restingContent,
        tween(140),
        label = "tvPillFg",
    )
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(140), label = "tvPillScale")
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.drawBehind {
                    if (focused) {
                        val ring = 2.5.dp.toPx()
                        drawRoundRect(
                            color = TvTokens.OnSurface.copy(alpha = 0.35f),
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(size.height / 2 + ring),
                            style = Stroke(width = ring * 2),
                        )
                    }
                }.clip(shape)
                .background(background)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).height(height)
                .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        content(contentColor)
    }
}
