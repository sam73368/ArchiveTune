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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R

private val TvKeyboardRows =
    listOf(
        "ABCDEF",
        "GHIJKL",
        "MNOPQR",
        "STUVWX",
        "YZ'-&.",
        "123456",
        "7890ÉÈ",
    )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchKeyboard(
    focusRequester: FocusRequester,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoiceSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val firstKey = focusRequester
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        repeat(6) {
            if (runCatching { firstKey.requestFocus() }.isSuccess) {
                keyboardController?.hide()
                return@LaunchedEffect
            }
            delay(60)
        }
    }

    Column(
        modifier =
            modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(TvTokens.RailBackground)
                .padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
                .focusRestorer(firstKey),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.search).uppercase(),
            style = TvTokens.Overline,
            color = TvTokens.OnSurfaceMuted,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvTokens.Surface)
                    .border(1.dp, TvTokens.OnSurface.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (query.isEmpty()) "…" else query + "▏",
                style = TvTokens.Title,
                color = if (query.isEmpty()) TvTokens.OnSurfaceFaint else TvTokens.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TvKeyboardRows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { index, char ->
                    TvKey(
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (rowIndex == 0 && index == 0) Modifier.focusRequester(firstKey) else Modifier),
                        onClick = { onQueryChange(query + char.lowercaseChar()) },
                    ) { color ->
                        Text(char.toString(), style = TvTokens.BodyBold, color = color)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvKey(modifier = Modifier.weight(2f), onClick = { onQueryChange("$query ") }) { color ->
                Text(stringResource(R.string.tv_key_space), style = TvTokens.Caption, color = color)
            }
            TvKey(modifier = Modifier.weight(1f), onClick = { onQueryChange(query.dropLast(1)) }) { color ->
                Icon(painterResource(R.drawable.backspace), stringResource(R.string.tv_key_delete), tint = color, modifier = Modifier.size(16.dp))
            }
            TvKey(modifier = Modifier.weight(1f), onClick = { onQueryChange("") }) { color ->
                Icon(painterResource(R.drawable.close), stringResource(R.string.tv_key_clear), tint = color, modifier = Modifier.size(16.dp))
            }
            if (onVoiceSearch != null) {
                TvKey(modifier = Modifier.weight(1f), onClick = onVoiceSearch) { color ->
                    Icon(painterResource(R.drawable.mic), stringResource(R.string.voice_search), tint = color, modifier = Modifier.size(16.dp))
                }
            }
        }
        TvKey(
            modifier = Modifier.fillMaxWidth(),
            accent = true,
            onClick = { onSearch(query) },
        ) { color ->
            Icon(painterResource(R.drawable.search), null, tint = color, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.search), style = TvTokens.BodyBold, color = color, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun TvKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> TvTokens.OnSurface
            accent -> TvTokens.OnSurface.copy(alpha = 0.18f)
            else -> TvTokens.SurfaceHigh
        },
        tween(110),
        label = "tvKeyBg",
    )
    val contentColor = if (focused) TvTokens.Background else TvTokens.OnSurface
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(110), label = "tvKeyScale")
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(contentColor)
    }
}
