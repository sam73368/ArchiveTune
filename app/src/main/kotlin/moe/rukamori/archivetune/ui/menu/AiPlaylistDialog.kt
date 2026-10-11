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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
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
    var trackCount by remember { mutableStateOf(25) }
    var createOnYouTube by remember { mutableStateOf(true) }
    val canSyncToYouTube by produceState(initialValue = false) {
        val prefs = context.dataStore.data.firstOrNull()
        value = prefs != null &&
            hasYouTubeLoginCookie(prefs[InnerTubeCookieKey].orEmpty()) &&
            (prefs[YtmSyncKey] ?: true)
    }
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
                Text(
                    text = stringResource(R.string.ai_playlist_count_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(10, 15, 25, 40, 60).forEach { count ->
                        FilterChip(
                            selected = trackCount == count,
                            onClick = { trackCount = count },
                            enabled = !isLoading,
                            label = { Text(text = count.toString()) },
                        )
                    }
                }
                if (canSyncToYouTube) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ai_playlist_create_on_youtube),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = createOnYouTube,
                            onCheckedChange = { createOnYouTube = it },
                            enabled = !isLoading,
                        )
                    }
                }
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

                            val draft = AiPlaylistGenerator.generate(context, prompt, trackCount)

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

                            if (canSyncToYouTube && createOnYouTube) {
                                YouTube
                                    .createPlaylist(name, songs.map { it.id })
                                    .onSuccess { remoteBrowseId ->
                                        if (remoteBrowseId.isNotBlank()) {
                                            database.playlist(playlist.id).firstOrNull()?.let { created ->
                                                database.query {
                                                    update(
                                                        created.playlist.copy(
                                                            browseId = remoteBrowseId,
                                                            lastUpdateTime = LocalDateTime.now(),
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }.onFailure { error ->
                                        Timber.w(error, "AI playlist: YouTube Music playlist creation failed; kept local-only.")
                                    }
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
