/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
fun TvRailNowPlaying(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    rightTarget: FocusRequester? = null,
) {
    val rightModifier =
        if (rightTarget != null) {
            Modifier.focusProperties { right = rightTarget }
        } else {
            Modifier
        }
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val current = metadata ?: return
    val accent = MaterialTheme.colorScheme.primary
    var progress by remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var queueOpen by remember { mutableStateOf(false) }
    LaunchedEffect(current.id) {
        while (true) {
            val player = playerConnection.player
            val duration = player.duration
            val position = player.currentPosition.coerceAtLeast(0L)
            durationMs = if (duration > 0) duration else 0L
            positionMs = position
            progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    Column(
        modifier =
            modifier
                .width(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvRailNowPlayingButton(
            size = 52.dp,
            corner = 10.dp,
            modifier = rightModifier,
            contentDescription = stringResource(R.string.tv_open_player),
            onClick = onOpenPlayer,
            onLongClick = { queueOpen = true },
        ) {
            AsyncImage(
                model = current.thumbnailUrl?.resize(160, 160, ytimgResizePolicy = moe.rukamori.archivetune.ui.utils.YtimgResizePolicy.PreserveOriginal),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            text = current.title,
            style = TvTokens.Caption,
            color = TvTokens.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        current.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { artistNames ->
            Text(
                text = artistNames,
                style = TvTokens.Caption,
                color = TvTokens.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TvTokens.OnSurface.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .drawBehind { drawRect(accent, size = size.copy(width = size.width * progress)) },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(makeTimeString(positionMs), style = TvTokens.Caption, color = TvTokens.OnSurfaceMuted, maxLines = 1)
            Text(
                if (durationMs > 0L) makeTimeString(durationMs) else "",
                style = TvTokens.Caption,
                color = TvTokens.OnSurfaceMuted,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TvRailNowPlayingButton(
                size = 24.dp,
                corner = 12.dp,
                contentDescription = stringResource(R.string.tv_previous),
                onClick = { playerConnection.seekToPrevious() },
            ) { tint ->
                Icon(painterResource(R.drawable.skip_previous), null, tint = tint, modifier = Modifier.size(14.dp))
            }
            TvRailNowPlayingButton(
                size = 24.dp,
                corner = 12.dp,
                contentDescription = stringResource(if (isPlaying) R.string.tv_pause else R.string.tv_resume),
                onClick = { playerConnection.player.togglePlayPause() },
            ) { tint ->
                if (isPlaying) {
                    TvPauseGlyph(tint = tint, modifier = Modifier.size(14.dp))
                } else {
                    Icon(painterResource(R.drawable.play), null, tint = tint, modifier = Modifier.size(14.dp))
                }
            }
            TvRailNowPlayingButton(
                size = 24.dp,
                corner = 12.dp,
                modifier = rightModifier,
                contentDescription = null,
                onClick = { playerConnection.seekToNext() },
            ) { tint ->
                Icon(painterResource(R.drawable.skip_next), null, tint = tint, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (queueOpen) {
        TvRailUpNextPopup(onDismiss = { queueOpen = false })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TvRailNowPlayingButton(
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused) TvTokens.Background else TvTokens.OnSurface
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val s = if (focused) 1.12f else 1f
                    scaleX = s
                    scaleY = s
                }.size(size)
                .tvFocusRingSimple(focused, corner)
                .clip(RoundedCornerShape(corner))
                .background(if (focused) TvTokens.OnSurface else TvTokens.OnSurface.copy(alpha = 0.1f))
                .onFocusChanged { focused = it.isFocused }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

@Composable
private fun TvRailUpNextPopup(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windows by playerConnection.queueWindows.collectAsState()
    val currentIndex by playerConnection.currentWindowIndex.collectAsState()
    val upNext = remember(windows, currentIndex) { windows.drop(currentIndex + 1).take(5) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(upNext.isNotEmpty()) {
        repeat(6) {
            delay(60)
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Popup(
        alignment = Alignment.BottomStart,
        offset = IntOffset(170, -40),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TvTokens.Surface)
                    .padding(12.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.tv_up_next), style = TvTokens.Overline, color = TvTokens.OnSurfaceMuted)
            if (upNext.isEmpty()) {
                Text(stringResource(R.string.tv_up_next_empty), style = TvTokens.Body, color = TvTokens.OnSurfaceMuted)
            }
            upNext.forEachIndexed { index, window ->
                val item = window.mediaItem.metadata
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) TvTokens.OnSurface else Color.Transparent)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.play()
                                onDismiss()
                            }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AsyncImage(
                        model = item?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item?.title.orEmpty(),
                            style = TvTokens.BodyBold,
                            color = if (focused) TvTokens.Background else TvTokens.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item?.artists?.joinToString { it.name }.orEmpty(),
                            style = TvTokens.Caption,
                            color = if (focused) TvTokens.Background.copy(alpha = 0.7f) else TvTokens.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.tvFocusRingSimple(
    focused: Boolean,
    corner: androidx.compose.ui.unit.Dp,
): Modifier {
    val state = TvFocusState().also { it.focused = focused }
    return tvFocusRing(state, corner, 2.dp)
}
