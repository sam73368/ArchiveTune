/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.distinctByPlaylistEntry
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.models.response.GetQueueResponse
import moe.rukamori.archivetune.innertube.models.response.GetSearchSuggestionsResponse
import moe.rukamori.archivetune.innertube.models.response.NextResponse
import moe.rukamori.archivetune.innertube.models.response.SearchResponse
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsContinuationPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.NewReleaseAlbumPage
import moe.rukamori.archivetune.innertube.pages.NextPage
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.innertube.pages.SearchPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSuggestionPage
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage

/**
 * Multiplatform subset of :core's YouTube facade, used by the iOS port.
 *
 * Function bodies are kept as close as possible to :core (YouTube.kt) so fixes can be carried
 * over by diffing; the parsers they call (models/ and pages/) are compiled directly from :core.
 */
object YouTube {
    val innerTube = KmpInnerTube()

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> =
        runCatching {
            val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
            SearchSuggestions(
                queries =
                    response.contents
                        ?.getOrNull(0)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            content.searchSuggestionRenderer
                                ?.suggestion
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                        }.orEmpty(),
                recommendedItems =
                    response.contents
                        ?.getOrNull(1)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }.orEmpty(),
            )
        }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val topItems = mutableListOf<YTItem>()
            val summaries = mutableListOf<SearchSummary>()

            contents.forEach { content ->
                content.musicCardShelfRenderer?.let { renderer ->
                    topItems +=
                        listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(renderer))
                            .plus(
                                renderer.contents
                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                                    .orEmpty(),
                            )
                    return@forEach
                }

                content.itemSectionRenderer?.contents?.let { sectionContents ->
                    topItems +=
                        sectionContents.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }
                    summaries += sectionContents.mapNotNull { it.musicShelfRenderer?.toSearchSummary() }
                    return@forEach
                }

                content.musicShelfRenderer?.toSearchSummary()?.let(summaries::add)
            }

            SearchSummaryPage(
                summaries =
                    buildList {
                        topItems
                            .distinctBy { it.id }
                            .takeIf { it.isNotEmpty() }
                            ?.let { add(SearchSummary(title = "Top results", items = it)) }
                        addAll(summaries)
                    },
            )
        }

    suspend fun search(
        query: String,
        filter: SearchFilter,
    ): Result<SearchResult> =
        runCatching {
            val response = innerTube.search(client = WEB_REMIX, query = query, params = filter.value).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val shelves =
                contents.flatMap { content ->
                    buildList {
                        content.musicShelfRenderer?.let { add(it) }
                        content.itemSectionRenderer
                            ?.contents
                            ?.mapNotNull { it.musicShelfRenderer }
                            ?.let { addAll(it) }
                    }
                }
            val inlineItems =
                contents.flatMap { content ->
                    content.itemSectionRenderer
                        ?.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        .orEmpty()
                }
            SearchResult(
                items =
                    shelves
                        .flatMap { it.contents?.getItems().orEmpty() }
                        .plus(inlineItems)
                        .mapNotNull { SearchPage.toYTItem(it) }
                        .distinctBy { it.id },
                continuation =
                    shelves
                        .asSequence()
                        .mapNotNull { it.continuations?.getContinuation() ?: it.contents?.getContinuation() }
                        .firstOrNull(),
            )
        }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> =
        runCatching {
            val response = innerTube.search(client = WEB_REMIX, continuation = continuation).body<SearchResponse>()
            val continuationPage = response.continuationContents?.musicShelfContinuation
            val items =
                continuationPage
                    ?.contents
                    ?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { renderer -> SearchPage.toYTItem(renderer) }
                    }
                    ?: emptyList()
            SearchResult(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        continuationPage?.continuations?.getContinuation()
                            ?: continuationPage
                                ?.contents
                                ?.firstOrNull { it.continuationItemRenderer != null }
                                ?.continuationItemRenderer
                                ?.continuationEndpoint
                                ?.continuationCommand
                                ?.token
                    },
            )
        }

    private fun MusicShelfRenderer.toSearchSummary(): SearchSummary? {
        val items =
            contents
                ?.getItems()
                ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                ?.distinctBy { it.id }
                .orEmpty()
        if (items.isEmpty()) return null
        val title =
            title
                ?.runs
                ?.joinToString(separator = "") { it.text }
                ?.takeIf { it.isNotBlank() }
                ?: "Other"
        return SearchSummary(title = title, items = items)
    }

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val playlistId =
                AlbumPage.getPlaylistId(response)
                    ?: throw IllegalStateException("Missing album playlist id for $browseId")
            val albumTitle =
                AlbumPage.getTitle(response)
                    ?: throw IllegalStateException("Missing album title for $browseId")
            val albumArtists = AlbumPage.getArtists(response).takeIf { it.isNotEmpty() }
            val albumYear = AlbumPage.getYear(response)
            val albumThumbnail =
                AlbumPage.getThumbnailInfo(response)
                    ?: throw IllegalStateException("Missing album thumbnail url for $browseId")
            val albumItem =
                AlbumItem(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = albumTitle,
                    artists = albumArtists,
                    year = albumYear,
                    thumbnail = albumThumbnail.normalizedUrl,
                    thumbnailWidth = albumThumbnail.width,
                    thumbnailHeight = albumThumbnail.height,
                    explicit = false,
                )
            val inlineSongs = if (withSongs) AlbumPage.getAllSongs(response, albumItem) else emptyList()
            val songs =
                if (withSongs) {
                    val fetchedSongs =
                        albumSongs(playlistId, albumItem).getOrElse { error ->
                            if (inlineSongs.isNotEmpty()) emptyList() else throw error
                        }
                    // Never let a shorter track list win (see :core YouTube.album).
                    if (inlineSongs.size > fetchedSongs.size) {
                        val fetchedById = fetchedSongs.associateBy { it.id }
                        inlineSongs.map { fetchedById[it.id] ?: it }
                    } else {
                        fetchedSongs
                    }
                } else {
                    emptyList()
                }

            AlbumPage(
                album = albumItem,
                songs = songs,
                otherVersions =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull { it.musicCarouselShelfRenderer }
                        ?.flatMap { it.contents }
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        ?.distinctBy { it.id }
                        .orEmpty(),
            )
        }

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>> =
        runCatching {
            val normalizedPlaylistId = playlistId.removePrefix("VL").removePrefix("VL")
            var response = innerTube.browse(WEB_REMIX, "VL$normalizedPlaylistId").body<BrowseResponse>()
            val songs = linkedMapOf<String, SongItem>()

            fun appendSongs(
                candidates: List<moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer>,
                parsedSongs: List<SongItem>,
                source: String,
            ): Boolean {
                if (candidates.isNotEmpty() && parsedSongs.isEmpty()) {
                    throw IllegalStateException("Unable to parse album songs from $source for playlist $playlistId")
                }
                val previousSize = songs.size
                parsedSongs.forEach { if (it.id !in songs) songs[it.id] = it }
                return songs.size > previousSize
            }

            appendSongs(
                candidates = AlbumPage.getSongRenderers(response),
                parsedSongs = AlbumPage.getSongs(response, album),
                source = "initial response",
            )

            var continuation = AlbumPage.getSongContinuation(response)
            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            var consecutiveEmptyResponses = 0
            while (continuation != null && requestCount < 50) {
                if (continuation in seenContinuations) break
                seenContinuations.add(continuation)
                requestCount++

                response =
                    runCatching {
                        innerTube.browse(client = WEB_REMIX, continuation = continuation).body<BrowseResponse>()
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException || songs.isEmpty()) throw error
                        null
                    } ?: break

                val newSongCandidates = AlbumPage.getContinuationSongRenderers(response)
                val newSongs = AlbumPage.getContinuationSongs(response, album)
                val hasNewSongs =
                    if (newSongCandidates.isNotEmpty() || newSongs.isNotEmpty()) {
                        runCatching {
                            appendSongs(newSongCandidates, newSongs, "continuation response")
                        }.getOrElse { error ->
                            if (songs.isEmpty()) throw error
                            null
                        } ?: break
                    } else {
                        false
                    }

                if (!hasNewSongs) {
                    consecutiveEmptyResponses++
                    if (consecutiveEmptyResponses >= 2) break
                } else {
                    consecutiveEmptyResponses = 0
                }
                continuation = AlbumPage.getNextSongContinuation(response)
            }
            songs.values.toList()
        }

    suspend fun artist(browseId: String): Result<ArtistPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val immersiveHeader = response.header?.musicImmersiveHeaderRenderer
            val subscribeButtonRenderer = immersiveHeader?.subscriptionButton?.subscribeButtonRenderer
            val artistThumbnail =
                immersiveHeader?.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
                    ?: response.header
                        ?.musicVisualHeaderRenderer
                        ?.foregroundThumbnail
                        ?.musicThumbnailRenderer
                        ?.getBestThumbnail()
                    ?: response.header
                        ?.musicDetailHeaderRenderer
                        ?.thumbnail
                        ?.musicThumbnailRenderer
                        ?.getBestThumbnail()
            val firstShelfItem =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicShelfRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveListItemRenderer

            ArtistPage(
                artist =
                    ArtistItem(
                        id = browseId,
                        title =
                            immersiveHeader?.title?.runs?.firstOrNull()?.text
                                ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                                ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                        thumbnail = artistThumbnail?.normalizedUrl,
                        thumbnailWidth = artistThumbnail?.width,
                        thumbnailHeight = artistThumbnail?.height,
                        channelId = subscribeButtonRenderer?.channelId,
                        playEndpoint =
                            firstShelfItem
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint,
                        shuffleEndpoint =
                            immersiveHeader?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                                ?: firstShelfItem?.navigationEndpoint?.watchPlaylistEndpoint,
                        radioEndpoint = immersiveHeader?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                        subscriberCountText =
                            subscribeButtonRenderer?.subscriberCountText?.runs?.firstOrNull()?.text
                                ?: subscribeButtonRenderer?.subscriberCountWithSubscribeText?.runs?.firstOrNull()?.text,
                        monthlyListenerCountText = immersiveHeader?.monthlyListenerCount?.runs?.firstOrNull()?.text,
                    ),
                sections =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
                description =
                    immersiveHeader
                        ?.description
                        ?.runs
                        ?.joinToString(separator = "") { run -> run.text }
                        ?.takeIf { description -> description.isNotBlank() },
            )
        }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
            val sectionContents =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val gridRenderer = sectionContents.firstNotNullOfOrNull { it.findGridRenderer() }
            if (gridRenderer != null) {
                ArtistItemsPage(
                    title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                    items =
                        gridRenderer.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer -> ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer) }
                        },
                    continuation = gridRenderer.continuations?.getContinuation(),
                    layout = ArtistItemsPageLayout.GRID,
                )
            } else {
                val musicPlaylistShelfRenderer = sectionContents.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }
                val musicShelfRenderer = sectionContents.firstNotNullOfOrNull { it.findMusicShelfRenderer() }
                val shelfContents = musicPlaylistShelfRenderer?.contents ?: musicShelfRenderer?.contents.orEmpty()
                ArtistItemsPage(
                    title =
                        response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                            ?: musicShelfRenderer?.title?.runs?.firstOrNull()?.text
                            ?: "",
                    items = shelfContents.getItems().mapNotNull { ArtistItemsPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation =
                        shelfContents.getContinuation()
                            ?: musicPlaylistShelfRenderer?.continuations?.getContinuation()
                            ?: musicShelfRenderer?.continuations?.getContinuation(),
                    layout = ArtistItemsPageLayout.LIST,
                )
            }
        }

    private fun SectionListRenderer.Content.findGridRenderer(): GridRenderer? =
        gridRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.gridRenderer }

    private fun SectionListRenderer.Content.findMusicShelfRenderer(): MusicShelfRenderer? =
        musicShelfRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.musicShelfRenderer }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            val gridContinuation = response.continuationContents?.gridContinuation
            val playlistShelfContinuation = response.continuationContents?.musicPlaylistShelfContinuation
            when {
                gridContinuation != null -> {
                    val items =
                        gridContinuation.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer -> ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer) }
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else gridContinuation.continuations?.getContinuation(),
                    )
                }
                playlistShelfContinuation != null -> {
                    val items =
                        playlistShelfContinuation.contents.getItems().mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else playlistShelfContinuation.continuations?.getContinuation(),
                    )
                }
                else -> {
                    val continuationItems =
                        response.onResponseReceivedActions
                            ?.firstOrNull()
                            ?.appendContinuationItemsAction
                            ?.continuationItems
                    val items =
                        continuationItems?.getItems()?.mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        } ?: emptyList()
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else continuationItems?.getContinuation(),
                    )
                }
            }
        }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        runCatching {
            val normalizedPlaylistId = playlistId.removePrefix("VL").removePrefix("VL")
            val response =
                innerTube.browse(client = WEB_REMIX, browseId = "VL$normalizedPlaylistId", setLogin = true).body<BrowseResponse>()
            val primarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val allFirstColumnContents = primarySection?.contents.orEmpty()
            val base =
                allFirstColumnContents.firstOrNull {
                    it.musicResponsiveHeaderRenderer != null || it.musicEditablePlaylistDetailHeaderRenderer != null
                }
            val header =
                base?.musicResponsiveHeaderRenderer
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
                    ?: throw IllegalStateException("PLAYLIST_PRIVATE")
            val title = header.title.runs?.firstOrNull()?.text ?: throw IllegalStateException("PLAYLIST_PRIVATE")
            val thumbnail =
                header.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
                    ?: throw IllegalStateException("PLAYLIST_PRIVATE")
            val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null
            val headerMenuItems = header.buttons.firstOrNull { it.menuRenderer != null }?.menuRenderer?.items.orEmpty()
            val description =
                base
                    ?.musicEditablePlaylistDetailHeaderRenderer
                    ?.header
                    ?.musicDetailHeaderRenderer
                    ?.description
                    ?.runs
                    ?.joinToString("") { it.text }
                    ?: allFirstColumnContents.firstNotNullOfOrNull {
                        it.musicDescriptionShelfRenderer?.description?.runs?.joinToString("") { run -> run.text }
                    }
            val secondarySection = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            val secondaryContents = secondarySection?.contents.orEmpty()
            val songContents =
                buildList {
                    secondaryContents.forEach { addAll(it.playlistSongContents()) }
                    allFirstColumnContents.forEach { addAll(it.playlistSongContents()) }
                }
            val songsContinuation =
                secondaryContents.firstNotNullOfOrNull { it.playlistSongContinuation() }
                    ?: allFirstColumnContents.firstNotNullOfOrNull { it.playlistSongContinuation() }

            PlaylistPage(
                playlist =
                    PlaylistItem(
                        id = playlistId,
                        title = title,
                        author =
                            header.straplineTextOne?.runs?.firstOrNull()?.let {
                                Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                            },
                        songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        description = description,
                        playEndpoint = header.buttons.firstOrNull()?.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint,
                        shuffleEndpoint =
                            headerMenuItems.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                        radioEndpoint =
                            headerMenuItems
                                .find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        isEditable = editable,
                    ),
                songs =
                    songContents
                        .getItems()
                        .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it, playlistId) }
                        .distinctByPlaylistEntry(),
                songsContinuation = songsContinuation,
                continuation =
                    secondarySection?.continuations?.getContinuation()
                        ?: primarySection?.continuations?.getContinuation(),
            )
        }

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage> =
        runCatching {
            val response =
                innerTube.browse(client = WEB_REMIX, continuation = continuation, browseId = "", setLogin = true).body<BrowseResponse>()
            playlistContinuationPageFromResponse(response, playlistId)
        }

    /** Loads every page of a playlist (used before queueing it). */
    suspend fun playlistAllSongs(playlistId: String): Result<Pair<PlaylistPage, List<SongItem>>> =
        runCatching {
            val page = playlist(playlistId).getOrThrow()
            val songs = page.songs.toMutableList()
            var continuation = page.songsContinuation
            var requests = 0
            while (continuation != null && requests < 100) {
                requests++
                val next = playlistContinuation(continuation, playlistId).getOrNull() ?: break
                if (next.songs.isEmpty()) break
                songs += next.songs
                continuation = next.continuation
            }
            page to songs.distinctByPlaylistEntry()
        }

    suspend fun home(continuation: String? = null): Result<HomePage> =
        runCatching {
            if (continuation != null) {
                val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
                val sections =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.contents
                        ?.mapNotNull { it.musicCarouselShelfRenderer }
                        ?.mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
                        .orEmpty()
                return@runCatching HomePage(
                    chips = null,
                    sections = sections,
                    continuation =
                        if (sections.isEmpty()) {
                            null
                        } else {
                            response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                        },
                )
            }
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", setLogin = true).body<BrowseResponse>()
            val sectionListRender =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val sections =
                sectionListRender
                    ?.contents
                    .orEmpty()
                    .mapNotNull { it.musicCarouselShelfRenderer }
                    .mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
            val chips = sectionListRender?.header?.chipCloudRenderer?.chips?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
            HomePage(chips, sections, sectionListRender?.continuations?.getContinuation())
        }

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult> =
        runCatching {
            val response =
                innerTube
                    .next(
                        WEB_REMIX,
                        endpoint.videoId,
                        endpoint.playlistId,
                        endpoint.playlistSetVideoId,
                        endpoint.index,
                        endpoint.params,
                        continuation,
                    ).body<NextResponse>()
            val playlistPanelRenderer =
                response.continuationContents?.playlistPanelContinuation
                    ?: response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.get(0)
                        ?.tabRenderer
                        ?.content
                        ?.musicQueueRenderer
                        ?.content
                        ?.playlistPanelRenderer!!
            val tabs = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs
            val title =
                tabs
                    ?.get(0)
                    ?.tabRenderer
                    ?.content
                    ?.musicQueueRenderer
                    ?.header
                    ?.musicQueueHeaderRenderer
                    ?.subtitle
                    ?.runs
                    ?.firstOrNull()
                    ?.text
            val items =
                playlistPanelRenderer.contents.mapNotNull { content ->
                    content.playlistPanelVideoRenderer
                        ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                        ?.let { it to content.playlistPanelVideoRenderer.selected }
                }
            val songs = items.map { it.first }
            val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }
            val lyricsEndpoint = tabs?.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint
            val relatedEndpoint = tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint

            if (followAutomixPreview) {
                playlistPanelRenderer.contents
                    .lastOrNull()
                    ?.automixPreviewVideoRenderer
                    ?.content
                    ?.automixPlaylistVideoRenderer
                    ?.navigationEndpoint
                    ?.watchPlaylistEndpoint
                    ?.let { watchPlaylistEndpoint ->
                        return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                            result.copy(
                                title = title,
                                items = songs + result.items,
                                lyricsEndpoint = lyricsEndpoint,
                                relatedEndpoint = relatedEndpoint,
                                currentIndex = currentIndex,
                                endpoint = watchPlaylistEndpoint,
                            )
                        }
                    }
            }
            NextResult(
                title = title,
                items = songs,
                currentIndex = currentIndex,
                lyricsEndpoint = lyricsEndpoint,
                relatedEndpoint = relatedEndpoint,
                continuation = playlistPanelRenderer.continuations?.getContinuation(),
                endpoint = endpoint,
            )
        }

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>> =
        runCatching {
            innerTube
                .getQueue(WEB_REMIX, videoIds, playlistId)
                .body<GetQueueResponse>()
                .queueDatas
                .mapNotNull {
                    it.content.playlistPanelVideoRenderer?.let { renderer -> NextPage.fromPlaylistPanelVideoRenderer(renderer) }
                }
        }
}

private fun SectionListRenderer.Content.playlistSongContents(): List<MusicShelfRenderer.Content> =
    buildList {
        addAll(musicPlaylistShelfRenderer?.contents.orEmpty())
        addAll(musicShelfRenderer?.contents.orEmpty())
        itemSectionRenderer?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { renderer ->
                add(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = renderer, continuationItemRenderer = null))
            }
            addAll(content.musicShelfRenderer?.contents.orEmpty())
        }
    }

private fun SectionListRenderer.Content.playlistSongContinuation(): String? =
    musicPlaylistShelfRenderer?.let { shelf ->
        shelf.contents.getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: musicShelfRenderer?.let { shelf ->
        shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: itemSectionRenderer?.contents.orEmpty().firstNotNullOfOrNull { content ->
        content.musicShelfRenderer?.let { shelf ->
            shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
        }
    }

private fun playlistContinuationPageFromResponse(
    response: BrowseResponse,
    playlistId: String? = null,
): PlaylistContinuationPage {
    val appendedContents =
        response.onResponseReceivedActions
            ?.firstOrNull()
            ?.appendContinuationItemsAction
            ?.continuationItems
            .orEmpty()

    val candidates =
        listOf(
            PlaylistContinuationCandidate(
                contents =
                    buildList {
                        response.continuationContents
                            ?.sectionListContinuation
                            ?.contents
                            .orEmpty()
                            .forEach { addAll(it.playlistSongContents()) }
                        addAll(appendedContents)
                    },
                continuation =
                    response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                        ?: appendedContents.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents = response.continuationContents?.musicPlaylistShelfContinuation?.contents.orEmpty(),
                continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents = response.continuationContents?.musicShelfContinuation?.contents.orEmpty(),
                continuation = response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents = appendedContents,
                continuation = appendedContents.getContinuation(),
            ),
        ).map { candidate ->
            candidate.copy(
                songs =
                    candidate.contents
                        .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it, playlistId) }
                        .distinctByPlaylistEntry(),
            )
        }

    val selected =
        candidates.firstOrNull { it.songs.isNotEmpty() }
            ?: candidates.firstOrNull { it.contents.isNotEmpty() }

    return PlaylistContinuationPage(
        songs = selected?.songs.orEmpty(),
        continuation = selected?.continuation?.takeUnless(String::isBlank),
    )
}

private data class PlaylistContinuationCandidate(
    val contents: List<MusicShelfRenderer.Content>,
    val continuation: String?,
    val songs: List<SongItem> = emptyList(),
)

