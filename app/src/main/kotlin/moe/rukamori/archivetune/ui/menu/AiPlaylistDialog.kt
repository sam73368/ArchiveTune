/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiPlaylistGenerator
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.playlist.CrossServicePlaylistImporter
import moe.rukamori.archivetune.ui.component.DefaultDialog
import java.time.LocalDateTime

/**
 * "AI playlist": the user describes a mood, the configured AI provider proposes songs, each one is
 * looked up on YouTube Music, and the matches become a local playlist.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiPlaylistDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val database = LocalDatabase.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var description by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    DefaultDialog(
        onDismiss = { if (!isLoading) onDismiss() },
        title = { Text(text = stringResource(R.string.ai_playlist_title)) },
        icon = { Icon(painter = painterResource(R.drawable.auto_awesome), contentDescription = null) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.ai_playlist_label)) },
                    placeholder = { Text(stringResource(R.string.ai_playlist_placeholder)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularWavyProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }
        },
        buttons = {
            OutlinedButton(
                enabled = !isLoading,
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            Button(
                enabled = !isLoading && description.text.isNotBlank(),
                onClick = {
                    val prompt = description.text.trim()
                    isLoading = true
                    statusMessage = context.getString(R.string.ai_playlist_generating)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            if (!AiPlaylistGenerator.isConfigured(context)) {
                                withContext(Dispatchers.Main) {
                                    toast(context.getString(R.string.ai_playlist_not_configured))
                                }
                                return@launch
                            }

                            val draft = AiPlaylistGenerator.generate(context, prompt)

                            val songs =
                                CrossServicePlaylistImporter.resolveToYouTubeMusicMetadata(
                                    tracks = draft.tracks,
                                    onProgress = { done, total ->
                                        statusMessage = context.getString(R.string.ai_playlist_searching, done, total)
                                    },
                                ).distinctBy { it.id }

                            if (songs.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    toast(context.getString(R.string.ai_playlist_no_matches))
                                }
                                return@launch
                            }

                            val name = draft.title.ifBlank { prompt.take(40) }
                            val playlist =
                                PlaylistEntity(
                                    name = name,
                                    isEditable = true,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            database.withTransaction {
                                songs.forEach { meta -> insert(meta) }
                                insert(playlist)
                            }
                            database.playlist(playlist.id).firstOrNull()?.let { created ->
                                database.addSongToPlaylist(created, songs.map { it.id })
                            }

                            withContext(Dispatchers.Main) {
                                toast(context.getString(R.string.ai_playlist_success, name, songs.size))
                                onDismiss()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toast(context.getString(R.string.ai_playlist_failed, e.message ?: "Unknown error"))
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                statusMessage = null
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(R.string.ai_playlist_generate))
            }
        },
    )
}
