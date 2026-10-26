/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.utils.ExplicitDuplicates.dropCleanTwins
import moe.rukamori.archivetune.utils.SongViewRanking
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        val routeArtistId: String = savedStateHandle.get<String>("artistId") ?: browseId
        val songsSection: Boolean =
            savedStateHandle.get<String>("songs")?.equals("true", ignoreCase = true) == true
        private val params =
            savedStateHandle
                .get<String>("params")
                ?.takeUnless { it.isBlank() || it == "null" }

        private val routeTitle =
            savedStateHandle
                .get<String>("title")
                ?.takeUnless { it.isBlank() || it == "null" }

        val title = MutableStateFlow(routeTitle.orEmpty())
        val itemsPage = MutableStateFlow<ItemsPage?>(null)
        val itemsLayout = MutableStateFlow(ArtistItemsPageLayout.LIST)

        val artist =
            database
                .artist(routeArtistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        // While the whole list is being ranked by views, loading "more" would race with it.
        @Volatile
        private var ranking = false

        private companion object {
            const val MAX_ALBUM_PAGES = 12
        }

        init {
            viewModelScope.launch {
                YouTube
                    .artistItems(
                        BrowseEndpoint(
                            browseId = browseId,
                            params = params,
                        ),
                    ).onSuccess { artistItemsPage ->
                        title.value = artistItemsPage.title
                        itemsLayout.value = artistItemsPage.layout
                        itemsPage.value =
                            ItemsPage(
                                items =
                                    artistItemsPage.items
                                        .distinctBy { it.id }
                                        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                        .filterVideo(context.dataStore.get(HideVideoKey, true))
                                        .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                                        .dropCleanTwins(),
                                continuation = artistItemsPage.continuation,
                            )

                        // A list of songs is shown from the most viewed to the least viewed. The
                        // first page appears at once in YouTube's order; the whole list is then
                        // loaded, ranked, and swapped in.
                        val onlySongs =
                            artistItemsPage.layout == ArtistItemsPageLayout.LIST &&
                                artistItemsPage.items.isNotEmpty() &&
                                artistItemsPage.items.all { it is SongItem }
                        val onlyAlbums =
                            artistItemsPage.layout == ArtistItemsPageLayout.GRID &&
                                artistItemsPage.items.isNotEmpty() &&
                                artistItemsPage.items.all { it is AlbumItem }
                        if (onlyAlbums && artistItemsPage.continuation != null) {
                            ranking = true
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    loadRemainingAlbumPages()
                                } finally {
                                    ranking = false
                                }
                            }
                        }
                        if (onlySongs && !context.isLowDataModeActive()) {
                            ranking = true
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    rankBySongViews(artistItemsPage)
                                } finally {
                                    ranking = false
                                }
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        private suspend fun loadRemainingAlbumPages() {
            var pages = 0
            while (pages < MAX_ALBUM_PAGES) {
                val current = itemsPage.value ?: return
                val continuation = current.continuation ?: return
                val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: return
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, true)
                val blocked = database.getBlockedArtistIds().toSet()
                itemsPage.value =
                    ItemsPage(
                        items =
                            (current.items + next.items)
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)
                                .filterBlockedArtists(blocked)
                                .dropCleanTwins(),
                        continuation = next.continuation,
                    )
                pages++
            }
        }

        private suspend fun rankBySongViews(firstPage: ArtistItemsPage) {
            val collected = SongViewRanking.collectSongs(firstPage)
            val ranked =
                SongViewRanking.rank(
                    context,
                    collected.songs
                        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                        .filterVideo(context.dataStore.get(HideVideoKey, true))
                        .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                        .dropCleanTwins(),
                )
            if (ranked.isEmpty()) return
            // Songs beyond the ranked head stay reachable through the remaining continuation.
            itemsPage.value = ItemsPage(items = ranked, continuation = collected.continuation)
        }

        fun loadMore() {
            if (ranking) return
            viewModelScope.launch {
                val oldItemsPage = itemsPage.value ?: return@launch
                val continuation = oldItemsPage.continuation ?: return@launch
                YouTube
                    .artistItemsContinuation(continuation)
                    .onSuccess { artistItemsContinuationPage ->
                        itemsPage.update {
                            ItemsPage(
                                items =
                                    (oldItemsPage.items + artistItemsContinuationPage.items)
                                        .distinctBy { it.id }
                                        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                        .filterVideo(context.dataStore.get(HideVideoKey, true))
                                        .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                                        .dropCleanTwins(),
                                continuation = artistItemsContinuationPage.continuation,
                            )
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }
