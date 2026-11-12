/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.search

import moe.rukamori.archivetune.ui.tv.tvBackButtonFocus
import moe.rukamori.archivetune.ui.tv.tvFocusRing
import moe.rukamori.archivetune.ui.tv.tvFocusTracker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.component.liquidGlassContentColor
import moe.rukamori.archivetune.ui.component.liquidGlass
import moe.rukamori.archivetune.ui.component.glassSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.ExperimentalFoundationApi
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DefaultSearchSourceKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.SearchProvider
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.ui.component.SearchSourcePicker
import moe.rukamori.archivetune.ui.screens.LocalSearchHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.HazeProgressive
import moe.rukamori.archivetune.viewmodels.SearchHistoryViewModel
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.utils.SongVersions
import moe.rukamori.archivetune.utils.SongVersions.dropSingleTwins
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.ShowVideosInSearchKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import androidx.compose.runtime.setValue

private val SearchHorizontalPadding = 24.dp
private const val RecentsHeightFraction = 0.55f
private val LiveChromeReserve = 84.dp
private const val LiveArtistLimit = 10
private const val LiveSongRefineCount = 6
private const val LiveRefineWaitMs = 2_000L

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    onSearchQuery: (String) -> Unit,
    onVoiceSearch: () -> Unit = {},
    headerScrollConnection: NestedScrollConnection? = null,
    historyViewModel: SearchHistoryViewModel = hiltViewModel(),
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searchQuery.isNotEmpty()) {
        searchQuery = ""
    }
    var searchProvider by rememberEnumPreference(DefaultSearchSourceKey, SearchProvider.YOUTUBE)

    var liveSuggestions by remember { mutableStateOf<SearchSuggestions?>(null) }
    var liveArtists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var liveSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    val (hideExplicit) = rememberPreference(HideExplicitKey, defaultValue = false)
    val (showVideosInSearch) = rememberPreference(ShowVideosInSearchKey, defaultValue = false)
    val hideVideo = !showVideosInSearch
    val database = moe.rukamori.archivetune.LocalDatabase.current
    LaunchedEffect(searchQuery, searchProvider, hideExplicit, hideVideo) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            liveSuggestions = null
            liveArtists = emptyList()
            liveSongs = emptyList()
            return@LaunchedEffect
        }

        delay(250)
        if (searchQuery.trim() != query) return@LaunchedEffect

        val plexArtists = plexArtistsMatching(database, query)
        if (plexArtists.isNotEmpty()) {
            liveArtists = (plexArtists + liveArtists).distinctBy { it.id }
        }

        val suggestionPage = YouTube.searchSuggestions(query).getOrNull()
        val recommended =
            suggestionPage
                ?.recommendedItems
                .orEmpty()
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)
        if (searchQuery.trim() != query) return@LaunchedEffect
        suggestionPage?.let { page ->
            liveSuggestions = SearchSuggestions(queries = page.queries, recommendedItems = recommended)
        }
        val recommendedArtists = recommended.filterIsInstance<ArtistItem>().distinctBy { it.id }
        val recommendedSongs = recommended.filterIsInstance<SongItem>()
        val isYouTubeSearch = searchProvider == SearchProvider.YOUTUBE
        if (!isYouTubeSearch || liveArtists.size <= plexArtists.size) {
            liveArtists = (plexArtists + recommendedArtists).distinctBy { it.id }
        }
        if (!isYouTubeSearch || liveSongs.isEmpty()) {
            liveSongs =
                SongVersions.sortByPopularity(
                    SongVersions.mergeSameSongs(recommendedSongs.map { SongVersions.withKnownAlbumVersion(it) }),
                    query,
                )
        }
        if (!isYouTubeSearch) return@LaunchedEffect

        coroutineScope {
            val completedQuery =
                suggestionPage
                    ?.queries
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.length > query.length && it.startsWith(query, ignoreCase = true) }
            val completedArtistsJob =
                async {
                    completedQuery
                        ?.let { YouTube.search(it, YouTube.SearchFilter.FILTER_ARTIST).getOrNull() }
                        ?.items
                        ?.filterIsInstance<ArtistItem>()
                        ?.take(3)
                        .orEmpty()
                }
            val exactArtistsJob =
                async {
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_ARTIST)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<ArtistItem>()
                        ?.take(5)
                        .orEmpty()
                }
            val songsJob =
                async {
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.filterExplicit(hideExplicit)
                        ?.filterVideo(hideVideo)
                        .orEmpty()
                }

            val artists =
                (plexArtists + recommendedArtists + completedArtistsJob.await() + exactArtistsJob.await())
                    .distinctBy { it.id }
                    .take(LiveArtistLimit)
            if (searchQuery.trim() != query) return@coroutineScope
            if (artists.map { it.id } != liveArtists.map { it.id }) liveArtists = artists

            val songs =
                (songsJob.await() + recommendedSongs)
                    .map { SongVersions.withKnownAlbumVersion(it) }
                    .let { SongVersions.mergeSameSongs(it) }
                    .let { it.dropSingleTwins() }
            if (searchQuery.trim() != query) return@coroutineScope
            val ranked = SongVersions.sortByPopularity(songs, query)

            suspend fun refine(): List<SongItem> {
                val refined =
                    SongVersions.preferAlbumVersions(ranked.take(LiveSongRefineCount), maxLookups = LiveSongRefineCount) +
                        ranked.drop(LiveSongRefineCount)
                return SongVersions.sortByPopularity(SongVersions.mergeSameSongs(refined).dropSingleTwins(), query)
            }

            fun publish(result: List<SongItem>) {
                if (searchQuery.trim() == query && result.map { it.id } != liveSongs.map { it.id }) liveSongs = result
            }

            val quick = withTimeoutOrNull(LiveRefineWaitMs) { refine() }
            if (quick != null) {
                publish(quick)
            } else {
                publish(ranked)
                publish(refine())
            }
            Unit
        }
    }

    val onSearchSourceSelection: (SearchSource, SearchProvider) -> Unit = { _, provider ->
        searchProvider = provider
    }
    val recentSearches by historyViewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHazeState = LocalSearchHazeState.current
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTvLayout = moe.rukamori.archivetune.ui.tv.LocalTvLayout.current
    val tvKeyboardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTvLayout) return@LaunchedEffect
        var attempt = 0
        while (attempt < 3) {
            delay(if (attempt == 0) 120L else 220L)
            val focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (focused) break
            attempt++
        }
        keyboardController?.show()
    }

    val barState = rememberSearchResultsBarState()

    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
    if (isTvLayout) {
        moe.rukamori.archivetune.ui.tv.TvSearchKeyboard(
            focusRequester = tvKeyboardFocus,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { text -> if (text.isNotBlank()) onSearchQuery(text.trim()) },
            onVoiceSearch = onVoiceSearch,
        )
    }
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxSize()
                .let { m -> if (searchHazeState != null) m.hazeSource(searchHazeState) else m }
                .then(
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .let { m ->
                        if (barState.backdrop != null) m.glassSource(barState.backdrop!!) else m
                    },
        ) {

        }

        BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.ime.union(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                            ),
                        ),
            ) {
                val recentsMaxHeight = if (isTvLayout) maxHeight - 40.dp else maxHeight * RecentsHeightFraction
                val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val liveMaxHeight =
                    if (isTvLayout) {
                        maxHeight - 40.dp
                    } else {
                        (maxHeight - LiveChromeReserve - statusBarInset - moe.rukamori.archivetune.constants.AppBarHeight)
                            .coerceAtLeast(0.dp)
                    }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(if (isTvLayout) Alignment.TopCenter else Alignment.BottomCenter)
                            .padding(top = if (isTvLayout) 24.dp else 0.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (searchQuery.isNotBlank()) {
                        LiveSearchSuggestionsPanel(
                            suggestions = liveSuggestions,
                            artists = liveArtists,
                            songs = liveSongs,
                            navController = navController,
                            maxHeight = liveMaxHeight,
                            hazeState = searchHazeState,
                            disableBlur = disableBlur,
                            onQueryPick = { picked ->
                                searchQuery = picked
                                onSearchQuery(picked)
                            },
                        )
                    } else {
                        RecentSearchesPanel(
                            recentSearches = recentSearches,
                            maxHeight = if (isTvLayout) recentsMaxHeight else liveMaxHeight,
                            fillHeight = !isTvLayout,
                            onClearAll = historyViewModel::clearAll,
                            onPick = onSearchQuery,
                        )
                    }

                    if (!isTvLayout) {
                    Spacer(Modifier.height(10.dp))

                    SearchTabBottomChrome(
                        barState = barState,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            onSearchQuery(it)
                        },
                        onVoiceSearch = onVoiceSearch,
                        onBack = navController::navigateUp,
                        onBackLongClick = navController::backToMain,
                        focusRequester = focusRequester,
                        searchProvider = searchProvider,
                        onSourceSelection = onSearchSourceSelection,
                    )
                    }
                }
            }
    }
    }
}

@Composable
private fun RecentSearchesPanel(
    recentSearches: List<SearchHistory>,
    maxHeight: androidx.compose.ui.unit.Dp,
    fillHeight: Boolean,
    onClearAll: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight))
                .verticalScroll(rememberScrollState()),
    ) {
        if (recentSearches.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.search_no_recent),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = SearchHorizontalPadding,
                            end = SearchHorizontalPadding,
                            top = 10.dp,
                            bottom = 6.dp,
                        ),
            ) {
                Text(
                    text = stringResource(R.string.search_recent_searches),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.clear),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            recentSearches.forEachIndexed { index, item ->
                RecentSearchRow(
                    history = item,
                    onClick = { onPick(item.query) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SearchHorizontalPadding),
                )
                if (index < recentSearches.lastIndex) {
                    HorizontalDivider(
                        modifier =
                            Modifier.padding(
                                start = SearchHorizontalPadding,
                                end = SearchHorizontalPadding,
                            ),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun LiveSearchSuggestionsPanel(
    suggestions: SearchSuggestions?,
    artists: List<ArtistItem>,
    songs: List<SongItem>,
    navController: NavController,
    maxHeight: Dp,
    hazeState: HazeState?,
    disableBlur: Boolean,
    onQueryPick: (String) -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val queries = suggestions?.queries.orEmpty()

    if (queries.isEmpty() && artists.isEmpty() && songs.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
        )
        return
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            moe.rukamori.archivetune.utils.LocalViewCountGroupLookup provides false,
        ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            if (artists.isNotEmpty()) {
                item(key = "artists_header", contentType = "header") {
                    LiveSectionLabel(stringResource(R.string.artists))
                }
                item(key = "artists_row", contentType = "artists_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SearchHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                    ) {
                        items(
                            items = artists,
                            key = { artist -> "artist_${artist.id}" },
                        ) { artist ->
                            SearchArtistChip(
                                artist = artist,
                                onClick = { navController.navigate("artist/${artist.id}") },
                            )
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "songs_header", contentType = "header") {
                    LiveSectionLabel(stringResource(R.string.songs))
                }
            }

            items(
                items = songs,
                key = { item -> "song_${item.id}" },
                contentType = { 2 },
            ) { item ->
                SuggestedResultRow(
                    item = item,
                    currentMediaId = mediaMetadata?.id,
                    currentAlbumId = mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    haptic = haptic,
                )
            }

            if (queries.isNotEmpty()) {
                item(key = "queries_header", contentType = "header") {
                    LiveSectionLabel(stringResource(R.string.suggestions))
                }
            }

            items(
                count = queries.size,
                key = { index -> "query_$index" },
                contentType = { 1 },
            ) { index ->
                val query = queries[index]
                val tv = moe.rukamori.archivetune.ui.tv.LocalTvLayout.current
                val queryFocus = moe.rukamori.archivetune.ui.tv.rememberTvFocusState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (tv) {
                                    Modifier
                                        .tvFocusTracker(queryFocus)
                                        .background(
                                            if (queryFocus.focused) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                } else {
                                    Modifier
                                },
                            )
                            .combinedClickable(
                                onClick = { onQueryPick(query) },
                                onLongClick = {},
                            )
                            .padding(horizontal = SearchHorizontalPadding, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        }

        if (hazeState != null && !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scrolledAway by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }
            val fadeAlpha by animateFloatAsState(
                targetValue = if (scrolledAway) 1f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "searchSuggestionsHaze",
            )
            if (fadeAlpha > 0.01f) {
                val pageColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .graphicsLayer { alpha = fadeAlpha }
                            .hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.ultraThin(pageColor),
                            ) {
                                progressive =
                                    HazeProgressive.verticalGradient(
                                        easing = EaseOutCubic,
                                        startIntensity = 0.75f,
                                        endIntensity = 0f,
                                    )
                                noiseFactor = 0f
                            },
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .graphicsLayer { alpha = fadeAlpha }
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        Array(4) { i ->
                                            val t = i / 3f
                                            t to pageColor.copy(alpha = 0.30f * (1f - t))
                                        },
                                ),
                            ),
                )
            }
        }
    }
}

private suspend fun plexArtistsMatching(
    database: moe.rukamori.archivetune.db.MusicDatabase,
    query: String,
): List<ArtistItem> {
    val words =
        moe.rukamori.archivetune.utils.AlbumKindCatalog
            .normalizeArtist(query)
            .split(' ')
            .filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return runCatching { database.plexArtists() }
        .getOrDefault(emptyList())
        .filter { artist ->
            val name = moe.rukamori.archivetune.utils.AlbumKindCatalog.normalizeArtist(artist.name)
            words.all { word -> name.split(' ').any { it.startsWith(word) } }
        }.sortedBy { it.name.length }
        .take(4)
        .map { artist ->
            ArtistItem(
                id = artist.id,
                title = artist.name,
                thumbnail = artist.thumbnailUrl,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
        }
}

@Composable
private fun LiveSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SearchArtistChip(
    artist: ArtistItem,
    onClick: () -> Unit,
) {
    val tv = moe.rukamori.archivetune.ui.tv.LocalTvLayout.current
    val tvFocus = moe.rukamori.archivetune.ui.tv.rememberTvFocusState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(if (tv) 96.dp else 80.dp)
                .then(
                    if (tv) {
                        Modifier
                            .tvFocusRing(tvFocus, 12.dp)
                            .tvFocusTracker(tvFocus)
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        ItemThumbnail(
            thumbnailUrl = artist.thumbnail,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SuggestedResultRow(
    item: YTItem,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    haptic: HapticFeedback,
) {
    YouTubeListItem(
        item = item,
        isActive =
            when (item) {
                is SongItem -> currentMediaId == item.id
                is AlbumItem -> currentAlbumId == item.id
                else -> false
            },
        isPlaying = isPlaying,
        isSwipeable = false,
        modifier =
            Modifier.combinedClickable(
                onClick = {
                    when (item) {
                        is SongItem -> {
                            if (item.id == currentMediaId) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playSongKeepingQueue(
                                    item.toMediaMetadata(),
                                    YouTubeQueue(
                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                        item.toMediaMetadata(),
                                    ),
                                )
                            }
                        }

                        is AlbumItem -> navController.navigate("album/${item.id}")

                        is ArtistItem -> navController.navigate("artist/${item.id}")

                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")

                        else -> {}
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            ),
    )
}

@Composable
private fun SearchTabBottomChrome(
    barState: SearchResultsBarState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit,
    focusRequester: FocusRequester,
    searchProvider: SearchProvider,
    onSourceSelection: (SearchSource, SearchProvider) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val backdrop = barState.backdrop
    val glassContentColor = if (backdrop != null) liquidGlassContentColor() else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                ),
    ) {
        val backShape = CircleShape
        val backModifier =
            if (backdrop != null) {
                Modifier.liquidGlass(
                    backdrop = backdrop,
                    shape = backShape,
                    interactive = true,
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, backShape)
            }
        Box(
            modifier =
                backModifier
                    .size(48.dp)
                    .tvBackButtonFocus()
                    .combinedClickable(
                        onClick = onBack,
                        onLongClick = onBackLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.back_button_desc),
                tint = glassContentColor,
                modifier = Modifier.size(22.dp),
            )
        }

        val pillShape = RoundedCornerShape(24.dp)
        val pillModifier =
            if (backdrop != null) {
                Modifier.liquidGlass(
                    backdrop = backdrop,
                    shape = pillShape,
                    interactive = true,
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, pillShape)
            }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .then(pillModifier)
                    .padding(start = 6.dp, end = 2.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = glassContentColor.copy(alpha = 0.72f),
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .size(22.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleMedium
                        .copy(fontSize = 16.sp)
                        .copy(color = glassContentColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            if (query.isNotEmpty()) {
                                onSearch(query)
                                keyboardController?.hide()
                            }
                        },
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text =
                                    stringResource(
                                        if (searchProvider == SearchProvider.SPOTIFY) {
                                            R.string.search_source_spotify
                                        } else if (searchProvider == SearchProvider.APPLE_MUSIC) {
                                            R.string.search_source_apple_music
                                        } else if (searchProvider == SearchProvider.SOUNDCLOUD) {
                                            R.string.search_source_soundcloud
                                        } else {
                                            R.string.search_yt_music
                                        },
                                    ),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                color = glassContentColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                onClick = onVoiceSearch,

                modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = stringResource(R.string.voice_search),
                    tint = glassContentColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(modifier = Modifier.padding(end = 6.dp)) {
                SearchSourcePicker(
                    currentScope = SearchSource.ONLINE,
                    currentProvider = searchProvider,
                    onSelection = onSourceSelection,
                    includeLocal = false,
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    history: SearchHistory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = history.query,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.search_recent_label),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

