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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.shared.player.PlayerController
import moe.rukamori.archivetune.shared.ui.AppIcons
import moe.rukamori.archivetune.shared.ui.ErrorBox
import moe.rukamori.archivetune.shared.ui.LoadingBox
import moe.rukamori.archivetune.shared.ui.Navigator
import moe.rukamori.archivetune.shared.ui.SectionTitle
import moe.rukamori.archivetune.shared.ui.SongRow
import moe.rukamori.archivetune.shared.ui.Thumbnail
import moe.rukamori.archivetune.shared.ui.currentSongIdState
import moe.rukamori.archivetune.shared.ui.subtitleText

private enum class SearchTab(
    val label: String,
    val filter: SearchFilter?,
) {
    ALL("Tout", null),
    SONGS("Chansons", SearchFilter.FILTER_SONG),
    ALBUMS("Albums", SearchFilter.FILTER_ALBUM),
    ARTISTS("Artistes", SearchFilter.FILTER_ARTIST),
    PLAYLISTS("Playlists", SearchFilter.FILTER_FEATURED_PLAYLIST),
    VIDEOS("Vidéos", SearchFilter.FILTER_VIDEO),
}

private sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Sections(
        val sections: List<Pair<String, List<YTItem>>>,
    ) : SearchState

    data class Error(
        val message: String,
    ) : SearchState
}

@Composable
fun SearchScreen(
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var tab by remember { mutableStateOf(SearchTab.ALL) }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var retry by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    val currentId = player.currentSongIdState()

    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty().take(6)
    }

    LaunchedEffect(submitted, tab, retry) {
        if (submitted.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        state = SearchState.Loading
        val filter = tab.filter
        state =
            if (filter == null) {
                YouTube.searchSummary(submitted).fold(
                    onSuccess = { page -> SearchState.Sections(page.summaries.map { it.title.localizedSection() to it.items }) },
                    onFailure = { SearchState.Error(it.message ?: it.toString()) },
                )
            } else {
                YouTube.search(submitted, filter).fold(
                    onSuccess = { result -> SearchState.Sections(listOf(tab.label to result.items)) },
                    onFailure = { SearchState.Error(it.message ?: it.toString()) },
                )
            }
    }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        submitted = q
        suggestions = emptyList()
        focusManager.clearFocus()
    }

    fun onItemClick(
        item: YTItem,
        siblings: List<YTItem>,
    ) {
        if (navigator.open(item)) return
        if (item is SongItem) {
            if (tab == SearchTab.SONGS) {
                val songs = siblings.filterIsInstance<SongItem>()
                player.playQueue(songs, songs.indexOf(item).coerceAtLeast(0), title = "Recherche : $submitted")
            } else {
                player.playRadio(item)
            }
        }
    }

    LazyColumn(contentPadding = contentPadding) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                placeholder = { Text("Chansons, albums, artistes…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (suggestions.isNotEmpty()) {
            items(suggestions) { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { submit(suggestion) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(suggestion, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (submitted.isNotBlank()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SearchTab.entries) { entry ->
                        FilterChip(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        }
        when (val current = state) {
            SearchState.Idle -> Unit
            SearchState.Loading -> item { LoadingBox() }
            is SearchState.Error -> item { ErrorBox(current.message, onRetry = { retry++ }) }
            is SearchState.Sections ->
                current.sections.forEachIndexed { sectionIndex, (title, items) ->
                    if (current.sections.size > 1) item(key = "s-$sectionIndex") { SectionTitle(title) }
                    items(items.distinctBy { it.id }, key = { "$sectionIndex-${it.id}" }) { item ->
                        if (item is SongItem) {
                            SongRow(
                                song = item,
                                isCurrent = currentId == item.id,
                                onClick = { onItemClick(item, items) },
                            )
                        } else {
                            ResultRow(item = item, onClick = { onItemClick(item, items) })
                        }
                    }
                }
        }
    }
}

@Composable
private fun ResultRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(
            url = item.thumbnail,
            size = 56.dp,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.subtitleText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String.localizedSection(): String = if (this == "Top results") "Meilleurs résultats" else this
