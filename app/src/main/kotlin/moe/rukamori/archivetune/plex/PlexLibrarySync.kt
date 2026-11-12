/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import android.content.Context
import androidx.datastore.preferences.core.edit
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlexLastSyncKey
import moe.rukamori.archivetune.constants.PlexServerMachineIdKey
import moe.rukamori.archivetune.constants.PlexServerTokenKey
import moe.rukamori.archivetune.constants.PlexServerUriKey
import moe.rukamori.archivetune.constants.PlexSyncVersionKey
import moe.rukamori.archivetune.constants.PlexSyncedSongCountKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.utils.AlbumKindCatalog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import timber.log.Timber

sealed interface PlexSyncState {
    data object Idle : PlexSyncState

    data class Running(
        val done: Int,
        val total: Int,
    ) : PlexSyncState

    data class Done(
        val songs: Int,
    ) : PlexSyncState

    data class Failed(
        val message: String,
    ) : PlexSyncState
}

object PlexLibrarySync {
    private const val TAG = "PlexLibrarySync"
    private const val SQL_BATCH = 500
    private const val SYNC_VERSION = 2
    private const val AUTO_SYNC_INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<PlexSyncState>(PlexSyncState.Idle)
    val state: StateFlow<PlexSyncState> = _state.asStateFlow()

    @Synchronized
    fun start(
        context: Context,
        database: MusicDatabase,
    ) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job =
            scope.launch {
                _state.value = PlexSyncState.Running(0, 0)
                _state.value =
                    try {
                        PlexSyncState.Done(sync(appContext, database))
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: PlexUnauthorizedException) {
                        Timber.tag(TAG).w(error, "Plex sync unauthorized")
                        PlexSyncState.Failed("unauthorized")
                    } catch (error: Exception) {
                        Timber.tag(TAG).w(error, "Plex sync failed")
                        PlexSyncState.Failed(error.message ?: error.javaClass.simpleName)
                    }
            }
    }

    suspend fun isDue(context: Context): Boolean {
        val uri = context.dataStore.getAsync(PlexServerUriKey)
        if (uri.isNullOrBlank()) return false
        if ((context.dataStore.getAsync(PlexSyncVersionKey) ?: 0) < SYNC_VERSION) return true
        val last = context.dataStore.getAsync(PlexLastSyncKey) ?: 0L
        return System.currentTimeMillis() - last > AUTO_SYNC_INTERVAL_MS
    }

    suspend fun removeAll(
        context: Context,
        database: MusicDatabase,
    ) {
        job?.cancel()
        val ids = database.plexSongIds()
        database.withTransaction {
            ids.chunked(SQL_BATCH).forEach(::deleteSongsByIds)
            pruneLocalAlbums()
            pruneLocalArtists()
        }
        PlexSession.forget()
        context.dataStore.edit {
            it.remove(PlexLastSyncKey)
            it.remove(PlexSyncedSongCountKey)
        }
        _state.value = PlexSyncState.Idle
    }

    private suspend fun sync(
        context: Context,
        database: MusicDatabase,
    ): Int {
        val baseUri = context.dataStore.getAsync(PlexServerUriKey)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No Plex server")
        val token = context.dataStore.getAsync(PlexServerTokenKey)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No Plex server")
        val machineId = context.dataStore.getAsync(PlexServerMachineIdKey)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No Plex server")
        val clientId = PlexSession.clientId(context)

        val sections = PlexClient.musicSections(clientId, baseUri, token)
        val seen = HashSet<String>()
        val albumTrackCounts = HashMap<String, Int>()
        val albumDurations = HashMap<String, Int>()
        var total = 0

        for (section in sections) {
            var start = 0
            while (true) {
                val page = PlexClient.trackPage(clientId, baseUri, token, section.key, start)
                if (page.tracks.isEmpty()) break
                total = maxOf(total, page.totalSize)
                writePage(database, baseUri, token, machineId, page.tracks, albumTrackCounts, albumDurations)
                page.tracks.forEach { track ->
                    seen += plexTrackMediaId(machineId, track.ratingKey)
                    track.partKey?.let { PlexSession.rememberPartKey(track.ratingKey, it) }
                }
                start += page.tracks.size
                _state.value = PlexSyncState.Running(seen.size, total)
                if (start >= page.totalSize) break
            }
        }

        val stale = database.plexSongIds().filterNot(seen::contains)
        database.withTransaction {
            stale.chunked(SQL_BATCH).forEach(::deleteSongsByIds)
            albumTrackCounts.forEach { (albumId, count) ->
                updateAlbumStats(albumId, count, albumDurations[albumId] ?: 0)
            }
            pruneLocalAlbums()
            pruneLocalArtists()
        }
        context.dataStore.edit {
            it[PlexLastSyncKey] = System.currentTimeMillis()
            it[PlexSyncedSongCountKey] = seen.size
            it[PlexSyncVersionKey] = SYNC_VERSION
        }
        Timber.tag(TAG).i("Plex sync: %d tracks in %d sections, %d removed", seen.size, sections.size, stale.size)
        return seen.size
    }

    private suspend fun writePage(
        database: MusicDatabase,
        baseUri: String,
        token: String,
        machineId: String,
        tracks: List<PlexTrack>,
        albumTrackCounts: MutableMap<String, Int>,
        albumDurations: MutableMap<String, Int>,
    ) {
        val songIds = tracks.map { plexTrackMediaId(machineId, it.ratingKey) }
        val existingSongs =
            songIds
                .chunked(SQL_BATCH)
                .flatMap { database.getSongsByIds(it) }
                .associateBy { it.song.id }
        val artistIds =
            tracks.flatMap { track ->
                val albumArtistName = track.albumArtist ?: track.artist
                (listOf(albumArtistName) + songArtistNames(track.artist, albumArtistName)).map(::artistIdOf)
            }.distinct()
        val existingArtists =
            artistIds
                .chunked(SQL_BATCH)
                .flatMap { database.getArtistEntitiesByIds(it) }
                .associateBy { it.id }
        val existingAlbums =
            tracks
                .mapNotNull { track -> track.albumKey?.let { "$PLEX_ALBUM_ID_PREFIX$it" } }
                .distinct()
                .chunked(SQL_BATCH)
                .flatMap { database.getAlbumEntitiesByIds(it) }
                .associateBy { it.id }
        val now = LocalDateTime.now()

        database.withTransaction {
            val writtenArtists = HashSet<String>()
            val writtenAlbums = HashSet<String>()
            tracks.forEachIndexed { position, track ->
                val songId = songIds[position]
                val existing = existingSongs[songId]?.song
                val albumArtistName = track.albumArtist ?: track.artist
                val albumArtistId = artistIdOf(albumArtistName)
                val songArtists = songArtistNames(track.artist, albumArtistName).map { it to artistIdOf(it) }
                val albumId = track.albumKey?.let { "$PLEX_ALBUM_ID_PREFIX$it" }
                val albumThumb = PlexClient.thumbUrl(baseUri, token, track.albumThumb)
                val durationSeconds = track.durationMs?.let { (it / 1000L).toInt() } ?: -1

                if (writtenArtists.add(albumArtistId)) {
                    val previous = existingArtists[albumArtistId]
                    upsert(
                        ArtistEntity(
                            id = albumArtistId,
                            name = albumArtistName,
                            thumbnailUrl =
                                PlexClient.thumbUrl(baseUri, token, track.artistThumb)
                                    ?: previous?.thumbnailUrl
                                    ?: albumThumb,
                            channelId = null,
                            lastUpdateTime = now,
                            bookmarkedAt = previous?.bookmarkedAt,
                            blockedAt = previous?.blockedAt,
                            isLocal = true,
                        ),
                    )
                }
                songArtists.forEach { (name, id) ->
                    if (id != albumArtistId && writtenArtists.add(id)) {
                        val previous = existingArtists[id]
                        upsert(
                            ArtistEntity(
                                id = id,
                                name = previous?.name ?: name,
                                thumbnailUrl = previous?.thumbnailUrl ?: albumThumb,
                                channelId = null,
                                lastUpdateTime = now,
                                bookmarkedAt = previous?.bookmarkedAt,
                                blockedAt = previous?.blockedAt,
                                isLocal = true,
                            ),
                        )
                    }
                }
                if (albumId != null && writtenAlbums.add(albumId)) {
                    val previous = existingAlbums[albumId]
                    upsert(
                        AlbumEntity(
                            id = albumId,
                            playlistId = null,
                            title = track.album ?: albumArtistName,
                            year = track.year,
                            thumbnailUrl = albumThumb,
                            themeColor = previous?.themeColor,
                            songCount = previous?.songCount ?: 0,
                            duration = previous?.duration ?: 0,
                            explicit = false,
                            lastUpdateTime = now,
                            bookmarkedAt = previous?.bookmarkedAt,
                            likedDate = previous?.likedDate,
                            inLibrary = previous?.inLibrary ?: now,
                            isLocal = true,
                        ),
                    )
                    deleteAlbumArtistMapsByAlbumIds(listOf(albumId))
                    insert(AlbumArtistMap(albumId = albumId, artistId = albumArtistId, order = 0))
                }
                if (albumId != null) {
                    albumTrackCounts[albumId] = (albumTrackCounts[albumId] ?: 0) + 1
                    albumDurations[albumId] = (albumDurations[albumId] ?: 0) + durationSeconds.coerceAtLeast(0)
                }

                upsert(
                    SongEntity(
                        id = songId,
                        title = if (existing?.titleOverride == true) existing.title else track.title,
                        titleOverride = existing?.titleOverride ?: false,
                        duration = durationSeconds,
                        thumbnailUrl = albumThumb,
                        albumId = albumId,
                        albumName = track.album,
                        explicit = existing?.explicit ?: false,
                        year = track.year,
                        date = existing?.date,
                        dateModified = existing?.dateModified,
                        liked = existing?.liked ?: false,
                        likedDate = existing?.likedDate,
                        totalPlayTime = existing?.totalPlayTime ?: 0L,
                        inLibrary = existing?.inLibrary ?: now,
                        dateDownload = existing?.dateDownload,
                        isLocal = true,
                    ),
                )
                upsert(
                    FormatEntity(
                        id = songId,
                        itag = -1,
                        mimeType = mimeTypeOf(track.container, track.audioCodec),
                        codecs = track.audioCodec.orEmpty(),
                        bitrate = (track.bitrateKbps ?: 0) * 1000,
                        sampleRate = null,
                        contentLength = track.sizeBytes ?: 0L,
                        loudnessDb = null,
                        playbackUrl = null,
                    ),
                )
                deleteSongArtistMaps(songId)
                val mappedIds = (songArtists.map { it.second } + albumArtistId).distinct()
                mappedIds.forEachIndexed { index, artistId ->
                    insert(SongArtistMap(songId = songId, artistId = artistId, position = index))
                }
                deleteSongAlbumMaps(songId)
                if (albumId != null) {
                    insert(SongAlbumMap(songId = songId, albumId = albumId, index = track.trackNumber ?: 0))
                }
            }
        }
    }

    fun artistIdOf(name: String): String {
        val key = AlbumKindCatalog.normalizeArtist(name).ifBlank { name.lowercase().trim() }
        return PLEX_ARTIST_ID_PREFIX + "n" + Integer.toHexString(key.hashCode())
    }

    private val ARTIST_SEPARATORS =
        Regex("""\s*(?:/|;|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+)\s*""", RegexOption.IGNORE_CASE)

    fun songArtistNames(
        raw: String,
        albumArtist: String,
    ): List<String> {
        if (raw == albumArtist) return listOf(raw)
        return raw
            .split(ARTIST_SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { AlbumKindCatalog.normalizeArtist(it) }
            .ifEmpty { listOf(raw) }
    }

    fun mimeTypeOf(
        container: String?,
        codec: String?,
    ): String =
        when ((container ?: codec)?.lowercase()) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac", "alac" -> "audio/mp4"
            "ogg", "opus", "vorbis" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> ""
        }
}
