/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.artist.ArtistBlockRequest
import moe.rukamori.archivetune.artist.ObserveArtistBlockedUseCase
import moe.rukamori.archivetune.artist.SetArtistBlockedUseCase
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.ui.screens.artist.ArtistCanvasArtworkCache
import moe.rukamori.archivetune.constants.AlbumCanvasEnabledKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.filterExplicitAlbums
import moe.rukamori.archivetune.extensions.filterVideo
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.innertube.pages.isInlineCarousel
import moe.rukamori.archivetune.spotify.SpotifyGapFinder
import moe.rukamori.archivetune.spotify.withSpotifyGaps
import moe.rukamori.archivetune.utils.AlbumEditionService
import moe.rukamori.archivetune.utils.AlbumKindCatalog
import moe.rukamori.archivetune.utils.ArtistAlbumGrouping
import moe.rukamori.archivetune.utils.AlbumKindRepository
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.ExplicitDuplicates.dropCleanTwins
import moe.rukamori.archivetune.utils.SongViewRanking
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import java.util.Locale
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

sealed interface ArtistBlockState {
    data object Loading : ArtistBlockState

    @Immutable
    data class Success(
        val isBlocked: Boolean,
    ) : ArtistBlockState

    data object Empty : ArtistBlockState

    @Immutable
    data class Error(
        @StringRes val messageRes: Int,
    ) : ArtistBlockState
}

sealed interface ArtistAction {
    data object Share : ArtistAction

    data object CopyLink : ArtistAction

    data object ToggleBlock : ArtistAction
}

sealed interface ArtistEvent {
    @Immutable
    data class Share(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class CopyLink(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : ArtistEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        observeArtistBlocked: ObserveArtistBlockedUseCase,
        private val setArtistBlocked: SetArtistBlockedUseCase,
        private val spotifyGapFinder: SpotifyGapFinder,
        private val albumKindRepository: AlbumKindRepository,
        private val albumEditionService: AlbumEditionService,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val artistId = savedStateHandle.get<String>("artistId")!!
        var artistPage by mutableStateOf<ArtistPage?>(null)

        val canvasArtwork = MutableStateFlow<CanvasArtwork?>(null)

        var isManuallyRefreshing by mutableStateOf(false)
        var loadFailed by mutableStateOf(false)
            private set
        private var fetchJob: Job? = null
        private var rankJob: Job? = null
        private val eventChannel = Channel<ArtistEvent>(capacity = Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var blockJob: Job? = null
        private var inlineExpandJob: Job? = null
        private var spotifyOnlyAlbums: List<AlbumItem> = emptyList()
        private var spotifyAlbumsChecked = false

        val libraryArtist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        val blockState =
            observeArtistBlocked(artistId)
                .map { blocked ->
                    if (blocked == null) {
                        ArtistBlockState.Empty
                    } else {
                        ArtistBlockState.Success(isBlocked = blocked)
                    }
                }.catch {
                    emit(ArtistBlockState.Error(R.string.error_unknown))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistBlockState.Loading)
        val librarySongs =
            context.dataStore.data
                .map { preferences ->
                    (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: true)
                }
                .distinctUntilChanged()
                .flatMapLatest { (hideExplicit, hideVideo) ->
                    database.artistSongsByCreateDateAsc(artistId).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val libraryAlbums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            viewModelScope.launch {
                context.dataStore.data
                    .map { preferences ->
                        (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: true)
                    }
                    .distinctUntilChanged()
                    .collect {
                        fetchArtistsFromYTM()
                    }
            }
        }

        fun manualRefresh() {
            if (isManuallyRefreshing) return
            isManuallyRefreshing = true
            fetchArtistsFromYTM(manual = true)
        }

        fun fetchArtistsFromYTM(manual: Boolean = false) {
            if (artistId.startsWith(LOCAL_ARTIST_PREFIX)) {
                if (manual) isManuallyRefreshing = false
                return
            }
            inlineExpandJob?.cancel()
            inlineExpandJob = null
            rankJob?.cancel()
            rankJob = null
            fetchJob?.cancel()
            loadFailed = false
            fetchJob = viewModelScope.launch {
                try {
                    fetchArtistFromYTM()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    if (manual) isManuallyRefreshing = false
                }
            }
        }

        private suspend fun fetchArtistFromYTM() {
            run {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, true)
                val blockedArtistIds = database.getBlockedArtistIds().toSet()
                YouTube
                    .artist(artistId)
                    .onSuccess { page ->
                        val filteredSections =
                            page.sections
                                .map { section ->
                                    section.copy(
                                        items =
                                            section.items
                                                .filterExplicit(hideExplicit)
                                                .filterVideo(hideVideo)
                                                .filterBlockedArtists(blockedArtistIds)
                                                .dropCleanTwins(),
                                    )
                                }

                        artistPage = page.copy(sections = filteredSections)
                        loadFailed = false
                        if (spotifyAlbumsChecked) {
                            warmAlbumKinds(page)
                            applySpotifyOnlyAlbums()
                        } else {
                            loadSpotifyOnlyAlbums(page)
                        }

                        expandInlineCarouselSections(filteredSections, hideExplicit, hideVideo, blockedArtistIds)

                        rankJob =
                            viewModelScope.launch(Dispatchers.IO) {
                                rankTopSongs(filteredSections, hideExplicit, hideVideo, blockedArtistIds)
                            }

                        viewModelScope.launch(Dispatchers.IO) {
                            fetchArtistCanvas(page)
                        }

                        withContext(Dispatchers.IO) {
                            database.artist(artistId).firstOrNull()?.artist?.let { artistEntity ->
                                database.update(artistEntity, page)
                            }
                        }
                    }.onFailure {
                        reportException(it)
                        if (artistPage == null) loadFailed = true
                    }
            }
        }

        val albumReplacements = MutableStateFlow<Map<String, AlbumItem>>(emptyMap())
        val albumOverrides = MutableStateFlow<Map<String, ArtistAlbumGrouping.Override>>(emptyMap())
        private var musicBrainzEntries: List<AlbumKindCatalog.Entry> = emptyList()
        private var editionBases: Map<String, AlbumItem> = emptyMap()
        private var knownAlbums: List<AlbumItem> = emptyList()

        private fun publishAlbumOverrides() {
            val musicBrainz =
                if (musicBrainzEntries.isEmpty()) emptyMap() else ArtistAlbumGrouping.musicBrainzOverrides(knownAlbums, musicBrainzEntries)
            albumOverrides.value = ArtistAlbumGrouping.withEditionBases(musicBrainz, editionBases)
        }

        private fun applyEditions(
            albums: List<AlbumItem>,
            result: AlbumEditionService.Result,
        ) {
            albumReplacements.value = result.replacements
            editionBases = result.baseOf
            knownAlbums = (albums + result.display).distinctBy { it.id }
            publishAlbumOverrides()
        }

        private fun warmAlbumKinds(page: ArtistPage) {
            val albums = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>().distinctBy { it.id }
            viewModelScope.launch(Dispatchers.IO) {
                if (albums.isNotEmpty()) applyEditions(albums, albumEditionService.quick(albums))
                if (context.isLowDataModeActive()) return@launch
                val entries = runCatching { albumKindRepository.entriesFor(page.artist.title) }.getOrNull()
                if (!entries.isNullOrEmpty()) {
                    musicBrainzEntries = entries
                    publishAlbumOverrides()
                }
                if (albums.isNotEmpty()) {
                    runCatching { albumEditionService.refine(albums) }.getOrNull()?.let { applyEditions(albums, it) }
                }
            }
        }

        private fun loadSpotifyOnlyAlbums(page: ArtistPage) {
            warmAlbumKinds(page)
            viewModelScope.launch(Dispatchers.IO) {
                val known = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
                val missing =
                    try {
                        spotifyGapFinder.missingAlbumsForArtist(page.artist.title, known)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        return@launch
                    }
                spotifyAlbumsChecked = true
                if (missing.isEmpty()) return@launch
                spotifyOnlyAlbums = missing
                withContext(Dispatchers.Main) { applySpotifyOnlyAlbums() }
            }
        }

        private fun applySpotifyOnlyAlbums() {
            val extra = spotifyOnlyAlbums
            val current = artistPage ?: return
            if (extra.isEmpty()) return
            val index = current.sections.indexOfFirst { section -> section.items.any { it is AlbumItem } }
            val sections =
                if (index >= 0) {
                    current.sections.mapIndexed { sectionIndex, section ->
                        if (sectionIndex == index) {
                            section.copy(items = section.items.withSpotifyGaps(extra))
                        } else {
                            section
                        }
                    }
                } else {
                    current.sections +
                        ArtistSection(
                            title = context.getString(R.string.filter_albums),
                            items = extra,
                            moreEndpoint = null,
                            layout = ArtistSectionLayout.GRID,
                        )
                }
            artistPage = current.copy(sections = sections)
        }

        private suspend fun rankTopSongs(
            sections: List<ArtistSection>,
            hideExplicit: Boolean,
            hideVideo: Boolean,
            blockedArtistIds: Set<String>,
        ) {
            if (context.isLowDataModeActive()) return
            val section =
                sections.firstOrNull { candidate ->
                    candidate.layout == ArtistSectionLayout.LIST &&
                        candidate.items.isNotEmpty() &&
                        candidate.items.all { it is SongItem }
                } ?: return
            val previewSize = section.items.size
            val endpoint = section.moreEndpoint

            val everySong =
                (endpoint?.let { SongViewRanking.fetchAllSongs(it)?.songs } ?: emptyList())
                    .filterExplicit(hideExplicit)
                    .filterVideo(hideVideo)
                    .filterBlockedArtists(blockedArtistIds)
                    .dropCleanTwins()
            val candidates =
                if (everySong.size >= previewSize) {
                    everySong
                } else {
                    (section.items.filterIsInstance<SongItem>() + everySong).distinctBy { it.id }
                }

            val ranked = SongViewRanking.rank(context, candidates).take(previewSize)
            if (ranked.isEmpty()) return

            withContext(Dispatchers.Main) {
                val current = artistPage ?: return@withContext
                if (current.sections.none { it === section }) return@withContext
                artistPage =
                    current.copy(
                        sections = current.sections.map { if (it === section) it.copy(items = ranked) else it },
                    )
            }
        }

        private fun mergeFilteredSectionItems(
            existing: List<YTItem>,
            incoming: List<YTItem>,
            hideExplicit: Boolean,
            hideVideo: Boolean,
            blockedArtistIds: Set<String>,
        ): List<YTItem> =
            (existing + incoming
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)
                .filterBlockedArtists(blockedArtistIds))
                .distinctBy { it.id }

        private fun publishInlineSectionItems(
            sectionIndex: Int,
            endpoint: BrowseEndpoint,
            mergedItems: List<YTItem>,
        ) {
            val current = artistPage ?: return
            if (sectionIndex !in current.sections.indices) return
            if (current.sections[sectionIndex].moreEndpoint != endpoint) return
            val updatedSections = current.sections.toMutableList()
            updatedSections[sectionIndex] =
                updatedSections[sectionIndex].copy(
                    items =
                        if (mergedItems.any { it is AlbumItem }) {
                            mergedItems.withSpotifyGaps(spotifyOnlyAlbums)
                        } else {
                            mergedItems
                        },
                )
            artistPage = current.copy(sections = updatedSections)
        }

        private fun expandInlineCarouselSections(
            sections: List<ArtistSection>,
            hideExplicit: Boolean,
            hideVideo: Boolean,
            blockedArtistIds: Set<String>,
        ) {
            val expandable =
                sections
                    .mapIndexedNotNull { index, section ->
                        if (section.isInlineCarousel) {
                            index to section
                        } else {
                            null
                        }
                    }
            if (expandable.isEmpty()) return
            inlineExpandJob =
                viewModelScope.launch {
                    expandable.forEach { (index, section) ->
                        val endpoint = section.moreEndpoint ?: return@forEach
                        launch {
                            var merged = section.items
                            var continuation: String? = null
                            YouTube
                                .artistItems(endpoint)
                                .onSuccess { firstPage ->
                                    merged =
                                        mergeFilteredSectionItems(merged, firstPage.items, hideExplicit, hideVideo, blockedArtistIds)
                                    if (merged.size > section.items.size) {
                                        publishInlineSectionItems(index, endpoint, merged)
                                    }
                                    continuation = firstPage.continuation
                                }.onFailure {
                                    reportException(it)
                                }
                            var pages = 0
                            while (isActive && continuation != null && pages < MAX_INLINE_CAROUSEL_PAGES) {
                                val nextContinuation = continuation ?: break
                                val nextPage =
                                    YouTube
                                        .artistItemsContinuation(nextContinuation)
                                        .getOrNull() ?: break
                                if (nextPage.items.isEmpty()) break
                                merged =
                                    mergeFilteredSectionItems(merged, nextPage.items, hideExplicit, hideVideo, blockedArtistIds)
                                if (merged.isNotEmpty()) {
                                    publishInlineSectionItems(index, endpoint, merged)
                                }
                                continuation = nextPage.continuation
                                pages++
                            }
                        }
                    }
                }
        }

        fun onAction(action: ArtistAction) {
            when (action) {
                ArtistAction.Share -> artistShareLink()?.let { eventChannel.trySend(ArtistEvent.Share(it)) }
                ArtistAction.CopyLink -> artistShareLink()?.let { eventChannel.trySend(ArtistEvent.CopyLink(it)) }
                ArtistAction.ToggleBlock -> toggleBlocked()
            }
        }

        private suspend fun fetchArtistCanvas(page: ArtistPage) {
            val artistName = page.artist.title.takeIf { it.isNotBlank() } ?: return

            val artistCanvas =
                runCatching {
                    if (context.dataStore.get(AlbumCanvasEnabledKey, true) && !context.isLowDataModeActive()) {
                        val country = Locale.getDefault().country
                        val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                        AppleMusicProvider.getByArtistName(artistName, storefront)
                    } else {
                        null
                    }
                }.getOrNull()
            if (artistCanvas != null) {
                canvasArtwork.value = artistCanvas
                ArtistCanvasArtworkCache.put(artistId, artistCanvas)
                return
            }

            val topSong =
                page.sections
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .filterIsInstance<SongItem>()
                    .firstOrNull() ?: return
            val artwork =
                runCatching {
                    fetchPlaylistCanvasArtwork(
                        context = context,
                        firstSongId = topSong.id,
                        firstSongTitle = topSong.title,
                        firstSongArtist = artistName,

                        allowSpotify = false,
                    )
                }.getOrNull()
            canvasArtwork.value = artwork
            if (artwork != null) {
                ArtistCanvasArtworkCache.put(artistId, artwork)
            }
        }

        private fun toggleBlocked() {
            if (blockJob?.isActive == true) return

            val pageArtist = artistPage?.artist
            val localArtist = libraryArtist.value?.artist
            val artistName = pageArtist?.title ?: localArtist?.name ?: return
            val currentlyBlocked = (blockState.value as? ArtistBlockState.Success)?.isBlocked == true

            blockJob =
                viewModelScope.launch {
                    try {
                        setArtistBlocked(
                            ArtistBlockRequest(
                                id = artistId,
                                name = artistName,
                                channelId = pageArtist?.channelId ?: localArtist?.channelId,
                                thumbnailUrl = pageArtist?.thumbnail ?: localArtist?.thumbnailUrl,
                                blocked = !currentlyBlocked,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.error_unknown))
                    }
                }
        }

        private fun artistShareLink(): String? =
            artistPage?.artist?.shareLink
                ?: artistId.takeIf { it.startsWith("UC") }?.let { "https://music.youtube.com/channel/$it" }

        private companion object {
            const val LOCAL_ARTIST_PREFIX = "LA"
            const val MAX_INLINE_CAROUSEL_PAGES = 40
        }
    }
