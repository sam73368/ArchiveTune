/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.shared.innertube.SearchResult
import moe.rukamori.archivetune.shared.innertube.YTMusicApi

private val ArchiveTuneColors =
    darkColorScheme(
        primary = Color(0xFFFFB0CB),
        background = Color(0xFF141218),
        surface = Color(0xFF141218),
    )

@Composable
fun App() {
    MaterialTheme(colorScheme = ArchiveTuneColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SearchScreen()
        }
    }
}

@Composable
private fun SearchScreen() {
    val api = remember { YTMusicApi() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { api.search(q) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(12.dp))
        Text("ArchiveTune", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Version iOS — aperçu technique",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Rechercher sur YouTube Music") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        when {
            loading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text("Erreur : $error", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.videoId ?: it.browseId ?: it.title }) { item ->
                        ListItem(
                            overlineContent = { Text(item.kind.label()) },
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.subtitle, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

private fun SearchResult.Kind.label(): String =
    when (this) {
        SearchResult.Kind.SONG -> "Chanson"
        SearchResult.Kind.VIDEO -> "Vidéo"
        SearchResult.Kind.ALBUM -> "Album"
        SearchResult.Kind.ARTIST -> "Artiste"
        SearchResult.Kind.PLAYLIST -> "Playlist"
        SearchResult.Kind.OTHER -> "Autre"
    }
