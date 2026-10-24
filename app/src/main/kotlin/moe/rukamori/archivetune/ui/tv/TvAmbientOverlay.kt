/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.makeTimeString
import java.text.DateFormat
import java.util.Date

object TvAmbientController {
    private val shown = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = shown.asStateFlow()

    fun show() {
        shown.value = true
    }

    fun hide() {
        shown.value = false
    }
}

@Composable
fun TvAmbientOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(900)),
        exit = fadeOut(tween(250)),
    ) {
        TvAmbientContent()
    }
}

@Composable
private fun TvAmbientContent() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val current = metadata ?: return
    val accent = MaterialTheme.colorScheme.primary
    var progress by remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val format = DateFormat.getTimeInstance(DateFormat.SHORT)
        while (true) {
            val player = playerConnection.player
            val duration = player.duration
            val position = player.currentPosition.coerceAtLeast(0L)
            durationMs = if (duration > 0) duration else 0L
            positionMs = position
            progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            clock = format.format(Date())
            delay(500)
        }
    }
    val artwork = current.thumbnailUrl?.resize(720, 720, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TvPlayerBackdrop(artworkUrl = current.thumbnailUrl)
        Text(
            text = clock,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 44.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(40.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(56.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)),
            )
            Column(modifier = Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = current.title,
                    style = TvTokens.Display,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                current.album?.title?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                    Text(
                        text = albumTitle,
                        style = TvTokens.Title,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = current.artists.joinToString { it.name },
                    style = TvTokens.Body,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier =
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(accent))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(makeTimeString(positionMs), style = TvTokens.Caption, color = Color.White.copy(alpha = 0.7f))
                    Text(
                        if (durationMs > 0L) makeTimeString(durationMs) else "",
                        style = TvTokens.Caption,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
