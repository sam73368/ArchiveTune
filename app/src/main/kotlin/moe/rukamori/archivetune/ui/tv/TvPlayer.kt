/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.utils.makeTimeString

private const val TvSeekStepMs = 10_000L

private fun tvSeekStepFor(repeatCount: Int): Long =
    when {
        repeatCount < 4 -> TvSeekStepMs
        repeatCount < 12 -> TvSeekStepMs * 2
        repeatCount < 24 -> TvSeekStepMs * 4
        else -> TvSeekStepMs * 6
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    positionProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    duration: Long,
    liked: Boolean,
    lyricsSyncOffset: Int,
    expanded: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val accent = MaterialTheme.colorScheme.primary
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    var lyricsVisible by rememberSaveable { mutableStateOf(true) }
    val playFocus = remember { FocusRequester() }
    var lyricsBrowsing by remember { mutableStateOf(false) }
    val tvPerformance = rememberTvPerformanceMode()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val nextMetadata = remember(queueWindows, currentWindowIndex) { queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata }
    var liveDuration by remember(mediaMetadata.id) { mutableLongStateOf(duration) }
    LaunchedEffect(mediaMetadata.id, duration) {
        while (true) {
            val playerDuration = playerConnection.player.duration
            liveDuration =
                when {
                    playerDuration > 0L && playerDuration != C.TIME_UNSET -> playerDuration
                    duration > 0L && duration != C.TIME_UNSET -> duration
                    else -> mediaMetadata.duration.toLong() * 1000L
                }
            delay(500)
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            repeat(6) {
                if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(80)
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvTokens.Background),
    ) {
        TvPlayerBackdrop(artworkUrl = mediaMetadata.thumbnailUrl)

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .then(if (lyricsVisible) Modifier.width(440.dp) else Modifier.weight(1f))
                        .fillMaxHeight()
                        .padding(start = 64.dp, end = 32.dp, top = 40.dp, bottom = 28.dp)
                        .focusRestorer(playFocus),
                horizontalAlignment = if (lyricsVisible) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (lyricsVisible) 240.dp else 260.dp)
                            .shadow(40.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvTokens.SurfaceHigh),
                ) {
                    AsyncImage(
                        model = mediaMetadata.thumbnailUrl?.resize(640, 640, ytimgResizePolicy = moe.rukamori.archivetune.ui.utils.YtimgResizePolicy.PreserveOriginal),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(
                    modifier = Modifier.width(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = if (lyricsVisible) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = mediaMetadata.title,
                            style = TvTokens.Title,
                            color = TvTokens.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (mediaMetadata.explicit) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(TvTokens.OnSurface.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("E", style = TvTokens.Caption, color = TvTokens.OnSurface)
                            }
                        }
                    }
                    Text(
                        text =
                            listOfNotNull(
                                mediaMetadata.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                                mediaMetadata.album?.title,
                            ).joinToString(" · "),
                        style = TvTokens.Body,
                        color = TvTokens.OnSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TvSeekBar(
                    positionProvider = { sliderPositionProvider() ?: playerConnection.player.currentPosition.coerceAtLeast(0L) },
                    duration = liveDuration,
                    onSeek = onSeek,
                    onSeekFinished = onSeekFinished,
                    modifier = Modifier.width(320.dp),
                )

                Row(
                    modifier = Modifier.width(320.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvRoundButton(
                        iconRes = R.drawable.shuffle,
                        contentDescription = stringResource(R.string.tv_shuffle),
                        size = 32.dp,
                        tint = if (shuffleEnabled) accent else TvTokens.OnSurfaceMuted,
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                    )
                    TvRoundButton(
                        iconRes = R.drawable.skip_previous,
                        contentDescription = null,
                        size = 40.dp,
                        enabled = canSkipPrevious,
                        onClick = playerConnection::seekToPrevious,
                    )
                    TvRoundButton(
                        iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                        contentDescription = stringResource(if (isPlaying) R.string.tv_pause else R.string.tv_resume),
                        size = 56.dp,
                        filled = true,
                        loading = isLoading,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = { playerConnection.player.togglePlayPause() },
                    )
                    TvRoundButton(
                        iconRes = R.drawable.skip_next,
                        contentDescription = null,
                        size = 40.dp,
                        enabled = canSkipNext,
                        onClick = playerConnection::seekToNext,
                    )
                    TvRoundButton(
                        iconRes =
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> R.drawable.repeat
                            },
                        contentDescription = null,
                        size = 32.dp,
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) TvTokens.OnSurfaceMuted else accent,
                        onClick = { playerConnection.player.toggleRepeatMode() },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvPillButton(onClick = playerConnection::toggleLike, height = 28.dp, horizontalPadding = 10.dp) { color ->
                        Icon(
                            painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                            null,
                            tint = if (liked && color == TvTokens.OnSurface) accent else color,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(stringResource(R.string.tv_like), style = TvTokens.Caption, color = color, maxLines = 1, softWrap = false)
                    }
                    TvPillButton(onClick = onQueue, height = 28.dp, horizontalPadding = 10.dp) { color ->
                        Icon(painterResource(R.drawable.queue_music), null, tint = color, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.tv_queue), style = TvTokens.Caption, color = color, maxLines = 1, softWrap = false)
                    }
                    TvPillButton(
                        onClick = { lyricsVisible = !lyricsVisible },
                        style = if (lyricsVisible) TvPillStyle.Primary else TvPillStyle.Tonal,
                        height = 28.dp,
                        horizontalPadding = 10.dp,
                    ) { color ->
                        Icon(painterResource(R.drawable.lyrics), null, tint = color, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.tv_lyrics), style = TvTokens.Caption, color = color, maxLines = 1, softWrap = false)
                    }
                    TvPillButton(onClick = onMore, height = 28.dp, horizontalPadding = 10.dp) { color ->
                        Icon(painterResource(R.drawable.more_vert), null, tint = color, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.tv_more), style = TvTokens.Caption, color = color, maxLines = 1, softWrap = false)
                    }
                }

                Spacer(Modifier.weight(1f))
                nextMetadata?.let { next ->
                    Text(
                        text =
                            stringResource(R.string.tv_up_next) + " · " + next.title +
                                next.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(),
                        style = TvTokens.Caption,
                        color = TvTokens.OnSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(320.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.tv_player_hint),
                    style = TvTokens.Caption,
                    color = TvTokens.OnSurfaceFaint,
                )
            }

            if (lyricsVisible) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 24.dp, end = 48.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.key != Key.Back || !lyricsBrowsing) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyUp) {
                                    lyricsBrowsing = false
                                    runCatching { playFocus.requestFocus() }
                                }
                                true
                            }.focusProperties {
                                enter = {
                                    lyricsBrowsing = true
                                    FocusRequester.Default
                                }
                                exit = {
                                    lyricsBrowsing = false
                                    FocusRequester.Default
                                }
                            }.focusGroup(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (lyricsBrowsing) {
                            Row(
                                modifier = Modifier.padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TvPillButton(
                                    onClick = {
                                        lyricsBrowsing = false
                                        runCatching { playFocus.requestFocus() }
                                    },
                                    style = TvPillStyle.Primary,
                                    height = 30.dp,
                                    horizontalPadding = 14.dp,
                                ) { color ->
                                    Text(stringResource(R.string.lyrics_resync), style = TvTokens.Caption, color = color, maxLines = 1)
                                }
                                Text(
                                    text = stringResource(R.string.tv_lyrics_browse_hint),
                                    style = TvTokens.Caption,
                                    color = TvTokens.OnSurfaceMuted,
                                )
                            }
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            moe.rukamori.archivetune.LocalAnimationsDisabled provides
                                (tvPerformance || moe.rukamori.archivetune.LocalAnimationsDisabled.current),
                        ) {
                            LyricsEnhanced(
                                sliderPositionProvider = sliderPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                textColorOverride = Color.White,
                                browseMode = lyricsBrowsing,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeekBar(
    positionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val latestPositionProvider by rememberUpdatedState(positionProvider)
    var position by remember { mutableLongStateOf(positionProvider()) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) position = latestPositionProvider()
            delay(250)
        }
    }
    val seekable = duration > 0L && duration != C.TIME_UNSET
    val fraction = if (seekable) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val barHeight by animateFloatAsState(if (focused) 8f else 4f, tween(140), label = "tvSeekHeight")
    val accent = MaterialTheme.colorScheme.primary
    val activeColor by animateColorAsState(if (focused) accent else TvTokens.OnSurface, label = "tvSeekColor")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (!seekable) return@onPreviewKeyEvent false
                        val direction =
                            when (event.key) {
                                Key.DirectionLeft -> -1L
                                Key.DirectionRight -> 1L
                                else -> return@onPreviewKeyEvent false
                            }
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                scrubbing = true
                                val step = tvSeekStepFor(event.nativeKeyEvent.repeatCount)
                                val target = (position + direction * step).coerceIn(0L, duration)
                                position = target
                                onSeek(target)
                            }
                            KeyEventType.KeyUp -> {
                                scrubbing = false
                                onSeekFinished()
                            }
                        }
                        true
                    }.focusable()
                    .drawBehind {
                        val h = barHeight.dp.toPx()
                        val top = (size.height - h) / 2
                        drawRoundRect(
                            color = TvTokens.OnSurface.copy(alpha = 0.16f),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, h),
                            cornerRadius = CornerRadius(h / 2),
                        )
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(0f, top),
                            size = Size(size.width * fraction, h),
                            cornerRadius = CornerRadius(h / 2),
                        )
                        if (focused) {
                            val r = 8.dp.toPx()
                            drawCircle(TvTokens.OnSurface, r, Offset(size.width * fraction, size.height / 2))
                        }
                    },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(makeTimeString(position), style = TvTokens.Caption, color = TvTokens.OnSurfaceMuted)
            Text(
                if (seekable) "−" + makeTimeString((duration - position).coerceAtLeast(0L)) else "",
                style = TvTokens.Caption,
                color = TvTokens.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun TvRoundButton(
    iconRes: Int,
    contentDescription: String?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = TvTokens.OnSurface,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(140), label = "tvRoundScale")
    val background by animateColorAsState(
        when {
            filled -> TvTokens.OnSurface
            focused -> TvTokens.OnSurface
            else -> Color.Transparent
        },
        tween(140),
        label = "tvRoundBg",
    )
    val iconTint =
        when {
            filled || focused -> TvTokens.Background
            !enabled -> tint.copy(alpha = 0.35f)
            else -> tint
        }
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.drawBehind {
                    if (focused && filled) {
                        val ring = 3.dp.toPx()
                        drawCircle(
                            color = TvTokens.OnSurface.copy(alpha = 0.35f),
                            radius = this.size.minDimension / 2 + ring,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(ring * 2),
                        )
                    }
                }.size(size)
                .clip(CircleShape)
                .background(background)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.45f),
                color = iconTint,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}
