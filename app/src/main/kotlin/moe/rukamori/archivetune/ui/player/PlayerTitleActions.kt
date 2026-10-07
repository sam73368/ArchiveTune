/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetState

@Immutable
class PlayerTitleActions(

    val onTitleClick: () -> Unit,

    val onAlbumClick: () -> Unit,

    val onArtistClick: (artistId: String) -> Unit,

    val onCopyTitle: () -> Unit,

    val onCopyArtists: () -> Unit,
)

@Composable
fun rememberPlayerTitleActions(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
): PlayerTitleActions {
    val context = LocalContext.current
    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val artistLine =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(", ") { it.name }
        }

    return remember(mediaMetadata, navController, state, artistLine) {
        val openAlbum: () -> Unit = {
            mediaMetadata.album
                ?.takeIf { it.id.isNotBlank() }
                ?.let { album ->
                    state.collapseSoft()
                    val currentEntry = navController.currentBackStackEntry
                    val isSameAlbum =
                        currentEntry?.destination?.route == "album/{albumId}" &&
                            currentEntry?.arguments?.getString("albumId") == album.id
                    if (!isSameAlbum) {
                        navController.navigate("album/${album.id}")
                    }
                }
        }
        PlayerTitleActions(
            onTitleClick = openAlbum,
            onAlbumClick = openAlbum,
            onArtistClick = { artistId ->
                if (artistId.isNotBlank()) {
                    state.collapseSoft()
                    navController.navigate("artist/$artistId")
                }
            },
            onCopyTitle = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Copied Title", mediaMetadata.title),
                )
                Toast.makeText(context, "Copied Title", Toast.LENGTH_SHORT).show()
            },
            onCopyArtists = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Copied Artist", artistLine),
                )
                Toast.makeText(context, "Copied Artist", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 * Small tappable album line for the player header: shows the current album and opens its page
 * (with every track) on tap. Renders nothing when the item has no known album.
 */
@Composable
fun PlayerAlbumLink(
    mediaMetadata: MediaMetadata,
    onAlbumClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val album = mediaMetadata.album ?: return
    if (album.id.isBlank() || album.title.isBlank()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onAlbumClick)
                .padding(vertical = 2.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.album),
            contentDescription = stringResource(R.string.album_name),
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
