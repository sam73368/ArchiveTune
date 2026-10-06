/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.shared.player.PlayerController
import moe.rukamori.archivetune.shared.ui.ErrorBox
import moe.rukamori.archivetune.shared.ui.ItemCard
import moe.rukamori.archivetune.shared.ui.LoadingBox
import moe.rukamori.archivetune.shared.ui.Navigator
import moe.rukamori.archivetune.shared.ui.ScreenHeader
import moe.rukamori.archivetune.shared.ui.SectionTitle

@Composable
fun HomeScreen(
    navigator: Navigator,
    player: PlayerController,
    contentPadding: PaddingValues,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<HomePage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        error = null
        YouTube
            .home()
            .onSuccess { first ->
                // Load a couple of continuation pages so the feed is not just two shelves.
                var sections = first.sections
                var continuation = first.continuation
                repeat(3) {
                    val next = continuation?.let { YouTube.home(it).getOrNull() } ?: return@repeat
                    sections = sections + next.sections
                    continuation = next.continuation
                }
                page = first.copy(sections = sections, continuation = continuation)
            }.onFailure { error = it.message ?: it.toString() }
    }

    LazyColumn(contentPadding = contentPadding) {
        item { ScreenHeader(title = "Accueil", onBack = null) }
        when {
            error != null -> item { ErrorBox(error!!, onRetry = { reloadKey++ }) }
            page == null -> item { LoadingBox() }
            else ->
                page!!.sections.forEachIndexed { index, section ->
                    item(key = "title-$index") { SectionTitle(section.title) }
                    item(key = "row-$index") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(section.items.distinctBy { it.id }, key = { it.id }) { item ->
                                ItemCard(
                                    item = item,
                                    onClick = {
                                        if (!navigator.open(item) && item is SongItem) player.playRadio(item)
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
        }
    }
}
