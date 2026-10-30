/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.ShowVideosInSearchKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.constants.SearchProvider
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.spotify.SpotifyGapFinder
import moe.rukamori.archivetune.spotify.SpotifyGaps
import moe.rukamori.archivetune.spotify.withSpotifyGaps
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchProviderArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.utils.ExplicitDuplicates.dropCleanTwins
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue

enum class OnlineSearchSort {
    DEFAULT,
    VIEWS,
}

val PODCAST_SEARCH_FILTER = YouTube.SearchFilter("CLIENT_PODCASTS_CATEGORY")

private const val SUMMARY_FAILURE_KEY = "summary"

private fun YTItem.isPodcastEpisode(): Boolean {
    val musicVideoType =
        (this as? SongItem)
            ?.endpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType
    return musicVideoType != null && musicVideoType.startsWith("MUSIC_VIDEO_TYPE_PODCAST")
}

@HiltViewModel
class OnlineSearchViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
        private val spotifyGapFinder: SpotifyGapFinder,
        private val database: MusicDatabase,
    ) : ViewModel() {
        val query =
            decodeOnlineSearchQuery(
                savedStateHandle.get<String>(OnlineSearchResultArgument).orEmpty(),
            )
        val searchProvider: SearchProvider =
            savedStateHandle
                .get<String>(OnlineSearchProviderArgument)
                .toEnum(SearchProvider.YOUTUBE)
        val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
        var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
        val failedFilters = mutableStateSetOf<String>()
        var loadMoreFailed by mutableStateOf(false)
            private set
        private var loadGeneration = 0
        private var isLoadingMore = false

        private val allModeFilters =
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            )
        private var suggestedArtistsCache: List<ArtistItem>? = null
        private var spotifyGapsCache: SpotifyGaps? = null
        private val spotifyGapsMutex = Mutex()
        private var isSummaryLoading = false
        private val loadingFilters = mutableSetOf<String>()

        init {
            val historyQuery = query.trim()
            if (historyQuery.isNotEmpty()) {
                viewModelScope.launch {
                    if (!context.dataStore.get(PauseSearchHistoryKey, false)) {
                        database.query { insert(SearchHistory(query = historyQuery)) }
                    }
                }
            }
            if (searchProvider == SearchProvider.YOUTUBE) {
                viewModelScope.launch {
                    filter.collect { selectedFilter -> loadFor(selectedFilter) }
                }
                viewModelScope.launch {
                    context.dataStore.data
                        .map { it[ShowVideosInSearchKey] ?: false }
                        .distinctUntilChanged()
                        .drop(1)
                        .collect {
                            loadGeneration++
                            summaryPage = null
                            viewStateMap.clear()
                            failedFilters.clear()
                            loadMoreFailed = false
                            isLoadingMore = false
                            loadingFilters.clear()
                            isSummaryLoading = false
                            loadFor(filter.value)
                        }
                }
            }
        }

        fun retry() {
            failedFilters.clear()
            loadFor(filter.value)
        }

        private fun loadFor(selectedFilter: YouTube.SearchFilter?) {
            if (selectedFilter == null) {
                viewModelScope.launch {
                    loadSummaryIfNeeded()
                }
                allModeFilters.forEach { allModeFilter ->
                    viewModelScope.launch {
                        loadFilterIfNeeded(allModeFilter)
                    }
                }
            } else {
                viewModelScope.launch {
                    loadFilterIfNeeded(selectedFilter)
                }
            }
        }

        private suspend fun spotifyGaps(known: List<YTItem>): SpotifyGaps =
            spotifyGapsMutex.withLock {
                spotifyGapsCache?.let { return@withLock it }
                val gaps =
                    try {
                        spotifyGapFinder.missingForSearch(
                            query = query,
                            known = known,
                            hideExplicit = context.dataStore.get(HideExplicitKey, false),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        SpotifyGaps.EMPTY
                    }
                spotifyGapsCache = gaps
                gaps
            }

        private suspend fun withSpotifyOnlyItems(
            filter: YouTube.SearchFilter,
            items: List<YTItem>,
        ): List<YTItem> {
            if (filter != FILTER_SONG && filter != FILTER_ALBUM) return items
            val gaps = spotifyGaps(items)
            if (gaps.isEmpty) return items
            return if (filter == FILTER_SONG) {
                items.withSpotifyGaps(gaps.songs)
            } else {
                items.withSpotifyGaps(gaps.albums)
            }
        }

        private suspend fun withSpotifyOnlySummary(page: SearchSummaryPage): SearchSummaryPage {
            val allItems = page.summaries.flatMap { it.items }
            val gaps = spotifyGaps(allItems)
            if (gaps.isEmpty) return page
            var songsPlaced = gaps.songs.isEmpty()
            var albumsPlaced = gaps.albums.isEmpty()
            val updated =
                page.summaries.map { summary ->
                    when {
                        summary.items.isNotEmpty() && summary.items.all { it is SongItem } && !songsPlaced -> {
                            songsPlaced = true
                            summary.copy(items = summary.items.withSpotifyGaps(gaps.songs))
                        }

                        summary.items.isNotEmpty() && summary.items.all { it is AlbumItem } && !albumsPlaced -> {
                            albumsPlaced = true
                            summary.copy(items = summary.items.withSpotifyGaps(gaps.albums))
                        }

                        else -> summary
                    }
                }
            val extras =
                buildList {
                    if (!songsPlaced) add(SearchSummary(context.getString(R.string.filter_songs), gaps.songs))
                    if (!albumsPlaced) add(SearchSummary(context.getString(R.string.filter_albums), gaps.albums))
                }
            return page.copy(summaries = updated + extras)
        }

        private suspend fun suggestedArtists(): List<ArtistItem> {
            suggestedArtistsCache?.let { return it }
            val page = YouTube.searchSuggestions(query).getOrNull()
            val fromSuggestions = page?.recommendedItems?.filterIsInstance<ArtistItem>().orEmpty()
            val completedQuery =
                page
                    ?.queries
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.length > query.length && it.startsWith(query, ignoreCase = true) }
            val fromCompletion =
                completedQuery
                    ?.let { YouTube.search(it, FILTER_ARTIST).getOrNull() }
                    ?.items
                    ?.filterIsInstance<ArtistItem>()
                    ?.take(2)
                    .orEmpty()
            return (fromSuggestions + fromCompletion).distinctBy { it.id }.take(3).also { suggestedArtistsCache = it }
        }

        private suspend fun loadSummaryIfNeeded() {
            if (summaryPage != null || isSummaryLoading) return

            isSummaryLoading = true
            val generation = loadGeneration
            failedFilters.remove(SUMMARY_FAILURE_KEY)
            try {
                val extraArtists = suggestedArtists()
                YouTube
                    .searchSummary(query)
                    .onSuccess {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()

                        val contentFilteredPage =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(!context.dataStore.get(ShowVideosInSearchKey, false))
                        val builtSummary =
                            withSpotifyOnlySummary(contentFilteredPage.copy(
                                summaries =
                                    contentFilteredPage.summaries.mapIndexedNotNull { summaryIndex, summary ->
                                        summary
                                            .copy(
                                                items =
                                                    filterAiContent(
                                                        (if (summaryIndex == 0) extraArtists + summary.items else summary.items)
                                                            .distinctBy { item -> item.id }
                                                            .dropCleanTwins(),
                                                        aiContentFilterPolicy,
                                                    ),
                                            )
                                            .takeIf { filteredSummary -> filteredSummary.items.isNotEmpty() }
                                    },
                            ))
                        if (generation == loadGeneration) summaryPage = builtSummary
                    }.onFailure {
                        reportException(it)
                        if (generation == loadGeneration) failedFilters.add(SUMMARY_FAILURE_KEY)
                    }
            } finally {
                if (generation == loadGeneration) isSummaryLoading = false
            }
        }

        private suspend fun loadFilterIfNeeded(filter: YouTube.SearchFilter) {
            val filterKey = filter.value
            if (viewStateMap.containsKey(filterKey) || !loadingFilters.add(filterKey)) return

            val generation = loadGeneration
            failedFilters.remove(filterKey)
            try {
                if (filter == PODCAST_SEARCH_FILTER) {
                    loadPodcastFilter()
                    return
                }
                if (filter == FILTER_VIDEO && !context.dataStore.get(ShowVideosInSearchKey, false)) {
                    viewStateMap[filterKey] = ItemsPage(emptyList(), null)
                    return
                }
                val extraArtists = if (filter == FILTER_ARTIST) suggestedArtists() else emptyList()
                YouTube
                    .search(query, filter)
                    .onSuccess { result ->
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val builtPage =
                            ItemsPage(
                                withSpotifyOnlyItems(
                                    filter,
                                    filterAiContent(
                                    (extraArtists + result.items)
                                        .distinctBy { it.id }
                                        .filterExplicit(
                                            context.dataStore.get(
                                                HideExplicitKey,
                                                false,
                                            ),
                                        ).filterVideo(!context.dataStore.get(ShowVideosInSearchKey, false))
                                        .dropCleanTwins(),
                                    aiContentFilterPolicy,
                                    ),
                                ),
                                result.continuation,
                            )
                        if (generation == loadGeneration) viewStateMap[filterKey] = builtPage
                    }.onFailure {
                        reportException(it)
                        if (generation == loadGeneration) failedFilters.add(filterKey)
                    }
            } finally {
                if (generation == loadGeneration) loadingFilters.remove(filterKey)
            }
        }

        private suspend fun loadPodcastFilter() {
            val generation = loadGeneration
            try {
                val songsResult = YouTube.search(query, FILTER_SONG)
                val videosResult = YouTube.search(query, FILTER_VIDEO)
                if (songsResult.isFailure && videosResult.isFailure) {
                    if (generation == loadGeneration) failedFilters.add(PODCAST_SEARCH_FILTER.value)
                    return
                }
                val episodes =
                    (songsResult.getOrNull()?.items.orEmpty() + videosResult.getOrNull()?.items.orEmpty())
                        .filter { it.isPodcastEpisode() }
                        .distinctBy { it.id }
                val aiContentFilterPolicy = loadAiContentFilterPolicy()
                if (generation != loadGeneration) return
                viewStateMap[PODCAST_SEARCH_FILTER.value] =
                    ItemsPage(
                        filterAiContent(
                            episodes,
                            aiContentFilterPolicy,
                        ),
                        null,
                    )
            } finally {
                if (generation == loadGeneration) loadingFilters.remove(PODCAST_SEARCH_FILTER.value)
            }
        }

        fun loadMore() {
            val filter = filter.value?.value
            if (isLoadingMore) return
            val generation = loadGeneration
            loadMoreFailed = false
            viewModelScope.launch {
                if (filter == null) return@launch
                val viewState = viewStateMap[filter] ?: return@launch
                val continuation = viewState.continuation
                if (continuation != null) {
                    isLoadingMore = true
                    try {
                        loadMoreInternal(filter, viewState, continuation, generation)
                    } finally {
                        isLoadingMore = false
                    }
                }
            }
        }

        private suspend fun loadMoreInternal(
            filter: String,
            viewState: ItemsPage,
            continuation: String,
            generation: Int,
        ) {
            val searchResult = YouTube.searchContinuation(continuation).getOrNull()
            if (searchResult == null) {
                if (generation == loadGeneration) loadMoreFailed = true
                return
            }
            if (generation != loadGeneration) return
            val aiContentFilterPolicy = loadAiContentFilterPolicy()
            val continuedItems =
                filterAiContent(
                    searchResult.items
                        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                        .filterVideo(!context.dataStore.get(ShowVideosInSearchKey, false)),
                    aiContentFilterPolicy,
                )
            if (generation != loadGeneration) return
            viewStateMap[filter] =
                ItemsPage(
                    (viewState.items + continuedItems).distinctBy { it.id }.dropCleanTwins(),
                    searchResult.continuation,
                )
        }

        fun sortedItems(
            items: List<YTItem>,
            sort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
        ): List<YTItem> =
            when (sort) {
                OnlineSearchSort.DEFAULT -> {
                    items
                }

                OnlineSearchSort.VIEWS -> {
                    items
                        .withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<YTItem>> {
                                (it.value as? SongItem)?.viewCount ?: Long.MIN_VALUE
                            }.thenBy { it.index },
                        ).map { it.value }
                }
            }
    }
