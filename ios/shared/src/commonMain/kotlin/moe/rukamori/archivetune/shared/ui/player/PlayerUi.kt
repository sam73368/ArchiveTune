/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.shared.player.PlayerController
import moe.rukamori.archivetune.shared.ui.AppIcons
import moe.rukamori.archivetune.shared.ui.SongRow
import moe.rukamori.archivetune.shared.ui.Thumbnail
import moe.rukamori.archivetune.shared.ui.formatDuration

@Composable
fun MiniPlayer(
    player: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by player.queue.collectAsState()
    val index by player.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val song = queue.getOrNull(index) ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(song.thumbnail, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = player::togglePlayPause) {
                        Icon(if (isPlaying) AppIcons.Pause else AppIcons.Play, contentDescription = "Lecture/Pause")
                    }
                }
                IconButton(onClick = player::next) { Icon(AppIcons.Next, contentDescription = "Suivant") }
            }
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

@Composable
fun FullPlayer(
    player: PlayerController,
    onCollapse: () -> Unit,
) {
    val queue by player.queue.collectAsState()
    val index by player.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val error by player.error.collectAsState()
    val queueTitle by player.queueTitle.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf<Float?>(null) }
    val song = queue.getOrNull(index)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(AppIcons.ExpandMore, contentDescription = "Réduire") }
                Text(
                    text = queueTitle ?: "Lecture en cours",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(AppIcons.Queue, contentDescription = "File d'attente", tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }

            if (showQueue) {
                val listState = rememberLazyListState()
                LaunchedEffect(Unit) { if (index > 0) listState.scrollToItem(index) }
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(queue, key = { i, item -> "$i-${item.id}" }) { i, item ->
                        SongRow(song = item, isCurrent = i == index, onClick = { player.playIndex(i) })
                    }
                }
            } else {
                Spacer(Modifier.weight(0.1f))
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Thumbnail(song?.thumbnail, 320.dp, shape = RoundedCornerShape(20.dp))
                }
                Spacer(Modifier.weight(0.1f))
                Text(
                    song?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (error != null) {
                    Text(
                        "Lecture impossible : $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            val sliderMax = duration.coerceAtLeast(1L).toFloat()
            Slider(
                value = (seekPosition ?: position.toFloat()).coerceIn(0f, sliderMax),
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    seekPosition?.let { player.seekTo(it.toLong()) }
                    seekPosition = null
                },
                valueRange = 0f..sliderMax,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((seekPosition ?: position.toFloat()).toLong()), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.Previous, contentDescription = "Précédent", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = player::togglePlayPause, modifier = Modifier.size(76.dp), shape = CircleShape) {
                    if (isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(if (isPlaying) AppIcons.Pause else AppIcons.Play, contentDescription = "Lecture/Pause", modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.Next, contentDescription = "Suivant", modifier = Modifier.size(36.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        }
    }
}
