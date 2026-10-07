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
import androidx.compose.runtime.Composable
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
