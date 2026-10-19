/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object TvPlayerFocus {
    val entry = FocusRequester()
}

@Composable
fun TvPauseGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * (8f / 24f)
        val gap = size.width * (4f / 24f)
        val barHeight = size.height * (20f / 24f)
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barWidth * 0.32f)
        drawRoundRect(tint, Offset(0f, top), Size(barWidth, barHeight), radius)
        drawRoundRect(tint, Offset(barWidth + gap, top), Size(barWidth, barHeight), radius)
    }
}

@Composable
fun TvLinkText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val base =
        if (onClick == null) {
            modifier
        } else {
            modifier
                .clip(shape)
                .background(if (focused) TvTokens.OnSurface else Color.Transparent)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        }
    Text(
        text = text,
        style = style,
        color = if (focused) TvTokens.Background else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = base.padding(horizontal = if (onClick == null) 0.dp else 6.dp, vertical = 1.dp),
    )
}
