/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Smart playlists: playlists defined by rules (liked, never played, not heard for N days, ...)
 * that are re-evaluated against the library every time they are opened.
 */

package moe.rukamori.archivetune.ui.screens

import moe.rukamori.archivetune.ui.tv.tvBackButtonFocus
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.smartplaylist.LikedFilter
import moe.rukamori.archivetune.smartplaylist.PlayFilter
import moe.rukamori.archivetune.smartplaylist.SmartRule
import moe.rukamori.archivetune.smartplaylist.SmartSort
import moe.rukamori.archivetune.ui.component.IconButton as ATIconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.SmartPlaylistsViewModel
import java.time.LocalDateTime

const val SMART_PLAYLISTS_ROUTE = "smart_playlists"
const val SMART_PLAYLIST_ROUTE_BASE = "smart_playlist"

fun smartPlaylistRoute(ruleId: String) = "$SMART_PLAYLIST_ROUTE_BASE/$ruleId"

@Composable
private fun SmartRule.displayName(): String =
    when (id) {
        "preset_rediscover" -> stringResource(R.string.smart_playlist_rediscover)
        "preset_never_played" -> stringResource(R.string.smart_playlist_never_played)
        "preset_recently_added" -> stringResource(R.string.smart_playlist_recently_added)
        "preset_obsessed" -> stringResource(R.string.smart_playlist_obsessed)
        "preset_liked_unheard" -> stringResource(R.string.smart_playlist_liked_unheard)
        else -> name.ifBlank { stringResource(R.string.smart_playlist_untitled) }
    }

@Composable
private fun SmartRule.description(): String =
    when (id) {
        "preset_rediscover" -> stringResource(R.string.smart_playlist_rediscover_desc)
        "preset_never_played" -> stringResource(R.string.smart_playlist_never_played_desc)
        "preset_recently_added" -> stringResource(R.string.smart_playlist_recently_added_desc)
        "preset_obsessed" -> stringResource(R.string.smart_playlist_obsessed_desc)
        "preset_liked_unheard" -> stringResource(R.string.smart_playlist_liked_unheard_desc)
        else -> stringResource(R.string.smart_playlist_custom_desc)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistsScreen(
    navController: NavController,
    viewModel: SmartPlaylistsViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SmartRule?>(null) }
    var pendingDelete by remember { mutableStateOf<SmartRule?>(null) }

    editing?.let { rule ->
        SmartRuleEditor(
            initial = rule,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.smart_playlist_delete_title)) },
            text = { Text(rule.displayName()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(rule.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.smart_playlists_title)) },
                navigationIcon = {
                    ATIconButton(
                        modifier = Modifier.tvBackButtonFocus(),
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(
                        onClick = { editing = viewModel.newRule() },
                    ) {
                        Icon(painterResource(R.drawable.add), contentDescription = stringResource(R.string.create))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.smart_playlists_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(rules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = {
                        Text(rule.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(rule.description(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent =
                        if (rule.preset) {
                            null
                        } else {
                            {
                                Row {
                                    androidx.compose.material3.IconButton(onClick = { editing = rule }) {
                                        Icon(
                                            painterResource(R.drawable.edit),
                                            contentDescription = stringResource(R.string.edit),
                                        )
                                    }
                                    androidx.compose.material3.IconButton(onClick = { pendingDelete = rule }) {
                                        Icon(
                                            painterResource(R.drawable.delete),
                                            contentDescription = stringResource(R.string.delete),
                                        )
                                    }
                                }
                            }
                        },
                    modifier = Modifier.clickable { navController.navigate(smartPlaylistRoute(rule.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmartPlaylistDetailScreen(
    navController: NavController,
    ruleId: String,
    viewModel: SmartPlaylistsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val rule = rules.firstOrNull { it.id == ruleId }
    val songsFlow = remember(ruleId) { viewModel.songsFor(ruleId) }
    val songs by songsFlow.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val title = rule?.displayName().orEmpty()
    val database = LocalDatabase.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.smart_playlist_saved, title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    ATIconButton(
                        modifier = Modifier.tvBackButtonFocus(),
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        val list = songs
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (rule != null) {
                        Text(
                            rule.description(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (list != null) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, list.size, list.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                enabled = list.isNotEmpty(),
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(title = title, items = list.map { it.toMediaItem() }),
                                    )
                                },
                            ) { Text(stringResource(R.string.play)) }
                            OutlinedButton(
                                enabled = list.isNotEmpty(),
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(title = title, items = list.shuffled().map { it.toMediaItem() }),
                                    )
                                },
                            ) { Text(stringResource(R.string.shuffle)) }
                            OutlinedButton(
                                enabled = list.isNotEmpty(),
                                onClick = {
                                    val ids = list.map { it.id }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val playlist =
                                            PlaylistEntity(
                                                name = title,
                                                isEditable = true,
                                                bookmarkedAt = LocalDateTime.now(),
                                            )
                                        database.withTransaction { insert(playlist) }
                                        database.playlist(playlist.id).firstOrNull()?.let { created ->
                                            database.addSongToPlaylist(created, ids)
                                        }
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            ) { Text(stringResource(R.string.smart_playlist_save)) }
                        }
                    }
                }
            }
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.smart_playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    showInLibraryIcon = true,
                    trailingContent = {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = title,
                                            items = list.orEmpty().map { it.toMediaItem() },
                                            startIndex = list.orEmpty().indexOfFirst { it.id == song.id }.coerceAtLeast(0),
                                        ),
                                    )
                                }
                            },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartRuleEditor(
    initial: SmartRule,
    onDismiss: () -> Unit,
    onSave: (SmartRule) -> Unit,
) {
    var rule by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smart_playlist_editor_title)) },
        text = {
            moe.rukamori.archivetune.ui.tv.TvTextEntryHost {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = rule.name,
                    onValueChange = { rule = rule.copy(name = it.take(60)) },
                    label = { Text(stringResource(R.string.smart_rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                RuleSection(stringResource(R.string.smart_rule_liked)) {
                    ChoiceChip(rule.liked == LikedFilter.ANY, stringResource(R.string.smart_rule_any)) {
                        rule = rule.copy(liked = LikedFilter.ANY)
                    }
                    ChoiceChip(rule.liked == LikedFilter.LIKED, stringResource(R.string.smart_rule_liked_yes)) {
                        rule = rule.copy(liked = LikedFilter.LIKED)
                    }
                    ChoiceChip(rule.liked == LikedFilter.NOT_LIKED, stringResource(R.string.smart_rule_liked_no)) {
                        rule = rule.copy(liked = LikedFilter.NOT_LIKED)
                    }
                }

                RuleSection(stringResource(R.string.smart_rule_plays)) {
                    ChoiceChip(rule.plays == PlayFilter.ANY, stringResource(R.string.smart_rule_any)) {
                        rule = rule.copy(plays = PlayFilter.ANY)
                    }
                    ChoiceChip(rule.plays == PlayFilter.NEVER, stringResource(R.string.smart_rule_plays_never)) {
                        rule = rule.copy(plays = PlayFilter.NEVER, idleDays = 0, minPlays = 0)
                    }
                    ChoiceChip(rule.plays == PlayFilter.PLAYED, stringResource(R.string.smart_rule_plays_played)) {
                        rule = rule.copy(plays = PlayFilter.PLAYED)
                    }
                }

                if (rule.plays != PlayFilter.NEVER) {
                    RuleSection(stringResource(R.string.smart_rule_idle)) {
                        listOf(0, 30, 90, 180, 365).forEach { days ->
                            ChoiceChip(
                                rule.idleDays == days,
                                if (days == 0) stringResource(R.string.smart_rule_off) else stringResource(R.string.smart_rule_days, days),
                            ) { rule = rule.copy(idleDays = days) }
                        }
                    }
                    RuleSection(stringResource(R.string.smart_rule_min_plays)) {
                        listOf(0, 2, 5, 10, 25).forEach { count ->
                            ChoiceChip(
                                rule.minPlays == count,
                                if (count == 0) stringResource(R.string.smart_rule_off) else "$count+",
                            ) { rule = rule.copy(minPlays = count) }
                        }
                    }
                }

                RuleSection(stringResource(R.string.smart_rule_added)) {
                    listOf(0, 7, 30, 90, 365).forEach { days ->
                        ChoiceChip(
                            rule.addedWithinDays == days,
                            if (days == 0) stringResource(R.string.smart_rule_off) else stringResource(R.string.smart_rule_days, days),
                        ) { rule = rule.copy(addedWithinDays = days) }
                    }
                }

                RuleSection(stringResource(R.string.smart_rule_sort)) {
                    SmartSort.entries.forEach { sort ->
                        ChoiceChip(rule.sort == sort, sortLabel(sort)) { rule = rule.copy(sort = sort) }
                    }
                }

                RuleSection(stringResource(R.string.smart_rule_limit)) {
                    listOf(0, 25, 50, 100, 200).forEach { limit ->
                        ChoiceChip(
                            rule.limit == limit,
                            if (limit == 0) stringResource(R.string.smart_rule_no_limit) else "$limit",
                        ) { rule = rule.copy(limit = limit) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.smart_rule_allow_explicit), modifier = Modifier.weight(1f))
                    Switch(
                        checked = rule.allowExplicit,
                        onCheckedChange = { rule = rule.copy(allowExplicit = it) },
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rule.name.isNotBlank(),
                onClick = { onSave(rule.copy(name = rule.name.trim())) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun sortLabel(sort: SmartSort): String =
    when (sort) {
        SmartSort.RECENTLY_ADDED -> stringResource(R.string.smart_sort_recently_added)
        SmartSort.MOST_PLAYED -> stringResource(R.string.smart_sort_most_played)
        SmartSort.LEAST_PLAYED -> stringResource(R.string.smart_sort_least_played)
        SmartSort.NOT_HEARD_FOR_LONGEST -> stringResource(R.string.smart_sort_longest_unheard)
        SmartSort.RANDOM -> stringResource(R.string.smart_sort_random)
        SmartSort.TITLE -> stringResource(R.string.smart_sort_title)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) { content() }
    }
}

@Composable
private fun ChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
