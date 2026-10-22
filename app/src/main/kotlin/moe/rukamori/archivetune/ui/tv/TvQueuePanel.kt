/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference

private enum class TvQueueMode { Browse, Actions, Moving }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvQueuePanel(
    onClose: () -> Unit,
    onMoreOptions: (MediaMetadata) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val windows by playerConnection.queueWindows.collectAsState()
    val currentIndex by playerConnection.currentWindowIndex.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    var autoLoadMore by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    var confirmClear by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    var mode by remember { mutableStateOf(TvQueueMode.Browse) }
    var activeUid by remember { mutableStateOf<Any?>(null) }
    var focusedUid by remember { mutableStateOf<Any?>(null) }
    var confirmHeld by remember { mutableStateOf(false) }
    var swallowConfirm by remember { mutableStateOf(false) }
    val restoreFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun openActions(uid: Any?) {
        if (uid == null) return
        activeUid = uid
        mode = TvQueueMode.Actions
    }

    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            kotlinx.coroutines.delay(4000)
            confirmClear = false
        }
    }
    LaunchedEffect(mode) {
        if (mode != TvQueueMode.Actions && activeUid != null) {
            delay(60)
            runCatching { restoreFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex.coerceAtLeast(0))
        repeat(8) {
            delay(80)
            if (runCatching { currentFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    fun timelineIndexOf(uid: Any?): Int = windows.firstOrNull { it.uid == uid }?.firstPeriodIndex ?: -1

    fun moveActive(delta: Int) {
        val from = windows.indexOfFirst { it.uid == activeUid }
        val to = from + delta
        if (from < 0 || to !in windows.indices || shuffleEnabled) return
        player.moveMediaItem(windows[from].firstPeriodIndex, windows[to].firstPeriodIndex)
        scope.launch {
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index ?: 0
            val lastVisible = visible.lastOrNull()?.index ?: 0
            if (to <= firstVisible + 1 || to >= lastVisible - 1) listState.animateScrollToItem((to - 3).coerceAtLeast(0))
            delay(80)
            runCatching { restoreFocus.requestFocus() }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(360.dp)
                .background(TvTokens.Surface)
                .tvMenuFocusTrap()
                .onPreviewKeyEvent { event ->
                    val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isConfirm) {
                        if (event.type == KeyEventType.KeyDown) confirmHeld = true
                        if (event.type == KeyEventType.KeyUp) confirmHeld = false
                    }
                    when {
                        isConfirm && swallowConfirm -> {
                            if (event.type == KeyEventType.KeyUp) swallowConfirm = false
                            true
                        }
                        event.key == Key.Menu && mode == TvQueueMode.Browse -> {
                            if (event.type == KeyEventType.KeyDown) openActions(focusedUid)
                            true
                        }
                        event.key == Key.Back -> {
                            if (event.type == KeyEventType.KeyUp) {
                                if (mode == TvQueueMode.Browse) onClose() else mode = TvQueueMode.Browse
                            }
                            true
                        }
                        mode == TvQueueMode.Moving && event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                            moveActive(if (event.key == Key.DirectionUp) -1 else 1)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.tv_queue),
                    style = TvTokens.Title,
                    color = TvTokens.OnSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.tv_queue_count, windows.size),
                    style = TvTokens.Caption,
                    color = TvTokens.OnSurfaceMuted,
                )
            }
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TvPillButton(
                    onClick = { player.shuffleModeEnabled = !shuffleEnabled },
                    style = if (shuffleEnabled) TvPillStyle.Primary else TvPillStyle.Tonal,
                    height = 28.dp,
                    horizontalPadding = 10.dp,
                ) { color -> Icon(painterResource(R.drawable.shuffle), stringResource(R.string.tv_shuffle), tint = color, modifier = Modifier.size(14.dp)) }
                TvPillButton(
                    onClick = { player.toggleRepeatMode() },
                    style = if (repeatMode != Player.REPEAT_MODE_OFF) TvPillStyle.Primary else TvPillStyle.Tonal,
                    height = 28.dp,
                    horizontalPadding = 10.dp,
                ) { color ->
                    Icon(
                        painterResource(if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat),
                        null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
                TvPillButton(
                    onClick = { autoLoadMore = !autoLoadMore },
                    style = if (autoLoadMore) TvPillStyle.Primary else TvPillStyle.Tonal,
                    height = 28.dp,
                    horizontalPadding = 10.dp,
                ) { color -> Text(stringResource(R.string.tv_queue_autoplay), style = TvTokens.Caption, color = color, maxLines = 1) }
                if (windows.size > 1) {
                    TvPillButton(
                        onClick = {
                            if (confirmClear) {
                                confirmClear = false
                                playerConnection.clearQueueKeepingCurrent()
                            } else {
                                confirmClear = true
                            }
                        },
                        style = if (confirmClear) TvPillStyle.Primary else TvPillStyle.Tonal,
                        height = 28.dp,
                        horizontalPadding = 10.dp,
                    ) { color ->
                        Text(
                            stringResource(if (confirmClear) R.string.tv_queue_clear_confirm else R.string.tv_queue_clear),
                            style = TvTokens.Caption,
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text =
                    stringResource(
                        when (mode) {
                            TvQueueMode.Moving -> if (shuffleEnabled) R.string.tv_queue_move_shuffle else R.string.tv_queue_move_hint
                            else -> R.string.tv_queue_hint
                        },
                    ),
                style = TvTokens.Caption,
                color = if (mode == TvQueueMode.Moving) accent else TvTokens.OnSurfaceFaint,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(windows, key = { _, window -> window.uid.hashCode() }) { index, window ->
                    val item = window.mediaItem.metadata
                    val isCurrent = index == currentIndex
                    val isActive = window.uid == activeUid && mode != TvQueueMode.Browse
                    var focused by remember { mutableStateOf(false) }
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        when {
                                            window.uid == activeUid -> Modifier.focusRequester(restoreFocus)
                                            isCurrent -> Modifier.focusRequester(currentFocus)
                                            else -> Modifier
                                        },
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isActive && mode == TvQueueMode.Moving -> accent.copy(alpha = 0.35f)
                                            focused -> TvTokens.OnSurface
                                            else -> Color.Transparent
                                        },
                                    ).onFocusChanged {
                                        focused = it.isFocused
                                        if (it.isFocused) focusedUid = window.uid
                                    }
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (mode == TvQueueMode.Moving) {
                                                mode = TvQueueMode.Browse
                                            } else {
                                                player.seekToDefaultPosition(window.firstPeriodIndex)
                                                player.play()
                                            }
                                        },
                                        onLongClick = {
                                            if (confirmHeld) swallowConfirm = true
                                            openActions(window.uid)
                                        },
                                    ).padding(8.dp),
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
                                    color =
                                        when {
                                            focused -> TvTokens.Background
                                            isCurrent -> accent
                                            else -> TvTokens.OnSurface
                                        },
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
                        if (isActive && mode == TvQueueMode.Actions) {
                            val actionsFocus = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                delay(60)
                                runCatching { actionsFocus.requestFocus() }
                            }
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TvPillButton(
                                    onClick = {
                                        val from = timelineIndexOf(window.uid)
                                        val currentTimeline = player.currentMediaItemIndex
                                        if (!shuffleEnabled && from >= 0 && from != currentTimeline) {
                                            val target = if (from > currentTimeline) currentTimeline + 1 else currentTimeline
                                            player.moveMediaItem(from, target)
                                        }
                                        mode = TvQueueMode.Browse
                                    },
                                    height = 28.dp,
                                    horizontalPadding = 10.dp,
                                    modifier = Modifier.focusRequester(actionsFocus),
                                ) { color -> Text(stringResource(R.string.tv_queue_play_next), style = TvTokens.Caption, color = color, maxLines = 1) }
                                TvPillButton(
                                    onClick = { mode = TvQueueMode.Moving },
                                    height = 28.dp,
                                    horizontalPadding = 10.dp,
                                ) { color -> Text(stringResource(R.string.tv_queue_move), style = TvTokens.Caption, color = color, maxLines = 1) }
                                TvPillButton(
                                    onClick = {
                                        val from = timelineIndexOf(window.uid)
                                        if (from >= 0 && from != player.currentMediaItemIndex) player.removeMediaItem(from)
                                        mode = TvQueueMode.Browse
                                    },
                                    height = 28.dp,
                                    horizontalPadding = 10.dp,
                                ) { color -> Text(stringResource(R.string.tv_queue_remove), style = TvTokens.Caption, color = color, maxLines = 1) }
                                TvPillButton(
                                    onClick = {
                                        mode = TvQueueMode.Browse
                                        item?.let(onMoreOptions)
                                    },
                                    height = 28.dp,
                                    horizontalPadding = 10.dp,
                                ) { color -> Icon(painterResource(R.drawable.more_vert), stringResource(R.string.tv_more), tint = color, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
