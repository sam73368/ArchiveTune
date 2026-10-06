/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.shared.player.PlayerController
import moe.rukamori.archivetune.shared.ui.AppIcons
import moe.rukamori.archivetune.shared.ui.ErrorBox
import moe.rukamori.archivetune.shared.ui.ItemCard
import moe.rukamori.archivetune.shared.ui.LoadingBox
import moe.rukamori.archivetune.shared.ui.Navigator
import moe.rukamori.archivetune.shared.ui.Screen
import moe.rukamori.archivetune.shared.ui.ScreenHeader
import moe.rukamori.archivetune.shared.ui.SectionTitle
import moe.rukamori.archivetune.shared.ui.SongRow
import moe.rukamori.archivetune.shared.ui.Thumbnail
import moe.rukamori.archivetune.shared.ui.currentSongIdState
import moe.rukamori.archivetune.shared.ui.formatDuration

/** Loads something once per key, with retry. */
@Composable
private fun <T> rememberLoad(
    key: Any,
    loader: suspend () -> Result<T>,
): Triple<T?, String?, () -> Unit> {
    var reload by remember(key) { mutableIntStateOf(0) }
    var data by remember(key) { mutableStateOf<T?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key, reload) {
        error = null
        loader().onSuccess { data = it }.onFailure { error = it.message ?: it.toString() }
    }
    return Triple(data, error, { reload++ })
}

@Composable
private fun CollectionHeader(
    thumbnail: String?,
    title: String,
    subtitle: String,
    details: String?,
    circle: Boolean = false,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onRadio: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Thumbnail(thumbnail, 220.dp, shape = if (circle) CircleShape else RoundedCornerShape(16.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        if (!details.isNullOrBlank()) {
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onPlay != null) {
                Button(onClick = onPlay) {
                    Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Lecture")
                }
            }
            if (onShuffle != null) {
                OutlinedButton(onClick = onShuffle) {
                    Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Aléatoire")
                }
            }
            if (onRadio != null) {
                OutlinedButton(onClick = onRadio) {
                    Icon(AppIcons.Radio, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Radio")
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(
    screen: Screen.Album,
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    val (page, error, retry) = rememberLoad<AlbumPage>(screen.browseId) { YouTube.album(screen.browseId) }
    val currentId = player.currentSongIdState()
    LazyColumn(contentPadding = contentPadding) {
        item { ScreenHeader(title = page?.album?.title ?: "Album", onBack = navigator::back) }
        when {
            error != null -> item { ErrorBox(error, onRetry = retry) }
            page == null -> item { LoadingBox() }
            else -> {
                val album = page.album
                val songs = page.songs
                val totalMs = songs.sumOf { (it.duration ?: 0).toLong() } * 1000L
                item {
                    CollectionHeader(
                        thumbnail = album.thumbnail,
                        title = album.title,
                        subtitle = album.artists.orEmpty().joinToString { it.name },
                        details = listOfNotNull(album.year?.toString(), "${songs.size} titres", formatDuration(totalMs).takeIf { totalMs > 0 }).joinToString(" • "),
                        onPlay = { player.playQueue(songs, 0, title = album.title) },
                        onShuffle = { player.playQueue(songs.shuffled(), 0, title = album.title) },
                    )
                }
                itemsIndexed(songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = currentId == song.id,
                        index = index + 1,
                        showThumbnail = false,
                        // Album semantics fixed on Android too: the queue is the whole album,
                        // starting from the tapped track.
                        onClick = { player.playQueue(songs, index, title = album.title) },
                    )
                }
                if (page.otherVersions.isNotEmpty()) {
                    item { SectionTitle("Autres versions") }
                    item { ItemRow(page.otherVersions, navigator, player) }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    screen: Screen.Playlist,
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    val (data, error, retry) =
        rememberLoad<Pair<PlaylistPage, List<SongItem>>>(screen.playlistId) { YouTube.playlistAllSongs(screen.playlistId) }
    val currentId = player.currentSongIdState()
    LazyColumn(contentPadding = contentPadding) {
        item { ScreenHeader(title = data?.first?.playlist?.title ?: "Playlist", onBack = navigator::back) }
        when {
            error != null -> item { ErrorBox(error, onRetry = retry) }
            data == null -> item { LoadingBox() }
            else -> {
                val (page, songs) = data
                val playlist = page.playlist
                item {
                    CollectionHeader(
                        thumbnail = playlist.thumbnail,
                        title = playlist.title,
                        subtitle = playlist.author?.name.orEmpty(),
                        details = listOfNotNull(playlist.songCountText ?: "${songs.size} titres").joinToString(),
                        onPlay = { player.playQueue(songs, 0, title = playlist.title) },
                        onShuffle = { player.playQueue(songs.shuffled(), 0, title = playlist.title) },
                    )
                }
                itemsIndexed(songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = currentId == song.id,
                        onClick = { player.playQueue(songs, index, title = playlist.title) },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(
    screen: Screen.Artist,
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    val (page, error, retry) = rememberLoad<ArtistPage>(screen.browseId) { YouTube.artist(screen.browseId) }
    val currentId = player.currentSongIdState()
    LazyColumn(contentPadding = contentPadding) {
        item { ScreenHeader(title = page?.artist?.title ?: "Artiste", onBack = navigator::back) }
        when {
            error != null -> item { ErrorBox(error, onRetry = retry) }
            page == null -> item { LoadingBox() }
            else -> {
                val artist = page.artist
                item {
                    CollectionHeader(
                        thumbnail = artist.thumbnail,
                        title = artist.title,
                        subtitle = "",
                        details = artist.monthlyListenerCountText ?: artist.subscriberCountText,
                        circle = true,
                        onPlay = null,
                        onShuffle = null,
                        onRadio = artist.radioEndpoint?.videoId?.let { videoId -> { playEndpointRadio(player, videoId) } },
                    )
                }
                page.sections.forEachIndexed { sectionIndex, section ->
                    item(key = "t$sectionIndex") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(section.title, modifier = Modifier.weight(1f))
                            section.moreEndpoint?.let { endpoint ->
                                Text(
                                    "Tout voir",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .padding(end = 16.dp, top = 12.dp)
                                            .clickable { navigator.push(Screen.ArtistItems(section.title, endpoint)) },
                                )
                            }
                        }
                    }
                    val songs = section.items.filterIsInstance<SongItem>()
                    if (songs.size == section.items.size && songs.isNotEmpty()) {
                        itemsIndexed(songs.take(10), key = { i, song -> "s$sectionIndex-$i-${song.id}" }) { index, song ->
                            SongRow(
                                song = song,
                                isCurrent = currentId == song.id,
                                onClick = { player.playQueue(songs, index, title = artist.title) },
                            )
                        }
                    } else {
                        item(key = "r$sectionIndex") { ItemRow(section.items, navigator, player) }
                    }
                }
                page.description?.let { description ->
                    item { SectionTitle("À propos") }
                    item {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistItemsScreen(
    screen: Screen.ArtistItems,
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    val (page, error, retry) = rememberLoad<ArtistItemsPage>(screen.endpoint) { YouTube.artistItems(screen.endpoint) }
    val currentId = player.currentSongIdState()
    LazyColumn(contentPadding = contentPadding) {
        item { ScreenHeader(title = page?.title?.takeIf { it.isNotBlank() } ?: screen.title, onBack = navigator::back) }
        when {
            error != null -> item { ErrorBox(error, onRetry = retry) }
            page == null -> item { LoadingBox() }
            else -> {
                val songs = page.items.filterIsInstance<SongItem>()
                if (songs.isNotEmpty() && songs.size == page.items.size) {
                    itemsIndexed(songs, key = { i, song -> "$i-${song.id}" }) { index, song ->
                        SongRow(song = song, isCurrent = currentId == song.id, onClick = { player.playQueue(songs, index, title = screen.title) })
                    }
                } else {
                    items(page.items.chunked(2)) { pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            pair.forEach { item -> ItemCard(item, onClick = { openOrPlay(item, navigator, player) }) }
                            if (pair.size == 1) Spacer(Modifier.size(150.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    items: List<YTItem>,
    navigator: Navigator,
    player: PlayerController,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(items.distinctBy { it.id }, key = { it.id }) { item ->
            ItemCard(item, onClick = { openOrPlay(item, navigator, player) })
        }
    }
}

private fun openOrPlay(
    item: YTItem,
    navigator: Navigator,
    player: PlayerController,
) {
    if (!navigator.open(item) && item is SongItem) player.playRadio(item)
}

private fun playEndpointRadio(
    player: PlayerController,
    videoId: String,
) {
    player.playRadio(
        SongItem(id = videoId, title = "Radio", artists = emptyList(), thumbnail = ""),
    )
}
