/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.SpotifyOnly
import moe.rukamori.archivetune.spotify.bestCoverUrl
import moe.rukamori.archivetune.spotify.toSpotifyOnlyMetadata
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.SpotifyOnlyBadge
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.tv.tvBackButtonFocus
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.viewmodels.SpotifyAlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }

    val album = state.album
    val cover = album?.images?.bestCoverUrl()
    val trackMetadata =
        remember(album) {
            album?.tracks?.items.orEmpty()
                .filter { it.id.isNotBlank() }
                .map { it.toSpotifyOnlyMetadata(coverFallback = cover) }
        }

    val playFrom: (Int) -> Unit = { index ->
        if (playerConnection != null && trackMetadata.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = album?.name,
                    items = trackMetadata.map { it.toMediaItem() },
                    startIndex = index.coerceIn(trackMetadata.indices),
                ),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            album == null || trackMetadata.isEmpty() -> {
                EmptyPlaceholder(
                    icon = R.drawable.spotify_icon,
                    text = stringResource(R.string.spotify_only_album_load_failed),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    contentPadding =
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
                        ) {
                            AsyncImage(
                                model = cover,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                            )
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            Text(
                                text =
                                    joinByBullet(
                                        album.artists.joinToString { it.name },
                                        album.releaseDate?.take(4),
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                SpotifyOnlyBadge()
                                Button(onClick = { playFrom(0) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.play),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(
                        items = album.tracks?.items.orEmpty().filter { it.id.isNotBlank() },
                        key = { index, track -> "${track.id}_$index" },
                    ) { index, track ->
                        val mediaId = SpotifyOnly.trackMediaId(track.id)
                        val active = mediaMetadata?.id == mediaId
                        SpotifyTrackListItem(
                            track = track,
                            albumIndex = index + 1,
                            isActive = active,
                            isPlaying = isPlaying,
                            modifier =
                                Modifier.clickable {
                                    if (active) {
                                        playerConnection?.player?.togglePlayPause()
                                    } else {
                                        playFrom(index)
                                    }
                                },
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(
                    modifier = Modifier.tvBackButtonFocus(),
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}
