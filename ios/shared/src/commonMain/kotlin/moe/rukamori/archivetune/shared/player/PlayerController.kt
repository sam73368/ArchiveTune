/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.shared.ui.resizedThumbnail

/**
 * Queue logic shared with every platform; the actual audio goes through [AudioEngine].
 *
 * Behaviour mirrors the Android fixes: playing an album or playlist queues every track from the
 * tapped one, and when the queue runs out a YouTube Music radio seeded by the last track is
 * appended (like Android's "infinite queue").
 */
class PlayerController(
    private val engine: AudioEngine,
    private val scope: CoroutineScope,
) : AudioEngineListener {
    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _queueTitle = MutableStateFlow<String?>(null)
    val queueTitle: StateFlow<String?> = _queueTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var autoRadio = true
    private var radioJob: Job? = null
    private var consecutiveErrors = 0

    init {
        engine.setListener(this)
    }

    val currentSong: SongItem?
        get() = _queue.value.getOrNull(_currentIndex.value)

    /** Plays [songs] starting at [startIndex] (album, playlist, list of results...). */
    fun playQueue(
        songs: List<SongItem>,
        startIndex: Int = 0,
        title: String? = null,
        continueWithRadio: Boolean = true,
    ) {
        if (songs.isEmpty()) return
        radioJob?.cancel()
        autoRadio = continueWithRadio
        _queue.value = songs
        _queueTitle.value = title
        playIndex(startIndex.coerceIn(songs.indices))
    }

    /** Plays one song followed by its YouTube Music radio. */
    fun playRadio(song: SongItem) {
        playQueue(listOf(song), 0, title = null, continueWithRadio = true)
        extendWithRadio(song)
    }

    fun playIndex(index: Int) {
        val song = _queue.value.getOrNull(index) ?: return
        _currentIndex.value = index
        _positionMs.value = 0L
        _durationMs.value = (song.duration ?: 0) * 1000L
        _error.value = null
        engine.load(song.toPlayerTrack(), autoplay = true)
        updateNavigation()
        maybeExtendQueue()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) engine.pause() else if (currentSong != null) engine.play()
    }

    fun next() {
        val nextIndex = _currentIndex.value + 1
        if (nextIndex < _queue.value.size) {
            playIndex(nextIndex)
        } else if (autoRadio) {
            currentSong?.let { extendWithRadio(it, playWhenLoaded = true) }
        }
    }

    fun previous() {
        if (_positionMs.value > 3_000L || _currentIndex.value <= 0) {
            seekTo(0L)
        } else {
            playIndex(_currentIndex.value - 1)
        }
    }

    fun seekTo(positionMs: Long) {
        _positionMs.value = positionMs
        engine.seekTo(positionMs)
    }

    fun playNext(song: SongItem) {
        val list = _queue.value.toMutableList()
        if (list.isEmpty()) {
            playQueue(listOf(song))
            return
        }
        list.add(_currentIndex.value + 1, song)
        _queue.value = list
        updateNavigation()
    }

    fun addToQueue(songs: List<SongItem>) {
        if (_queue.value.isEmpty()) {
            playQueue(songs)
            return
        }
        _queue.value = _queue.value + songs
        updateNavigation()
    }

    fun removeFromQueue(index: Int) {
        if (index == _currentIndex.value) return
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list
        if (index < _currentIndex.value) _currentIndex.value -= 1
        updateNavigation()
    }

    fun dismissError() {
        _error.value = null
    }

    private fun maybeExtendQueue() {
        if (!autoRadio) return
        val remaining = _queue.value.size - 1 - _currentIndex.value
        if (remaining <= 2) _queue.value.lastOrNull()?.let { extendWithRadio(it) }
    }

    private fun extendWithRadio(
        seed: SongItem,
        playWhenLoaded: Boolean = false,
    ) {
        if (radioJob?.isActive == true) return
        radioJob =
            scope.launch {
                val result = YouTube.next(WatchEndpoint(videoId = seed.id))
                result.onSuccess { next ->
                    val known = _queue.value.mapTo(HashSet()) { it.id }
                    val newSongs = next.items.filter { known.add(it.id) }
                    if (newSongs.isNotEmpty()) {
                        _queue.value = _queue.value + newSongs
                        updateNavigation()
                        if (playWhenLoaded) playIndex(_currentIndex.value + 1)
                    }
                }
            }
    }

    private fun updateNavigation() {
        engine.setQueueNavigation(
            hasNext = _currentIndex.value < _queue.value.size - 1 || autoRadio,
            hasPrevious = _currentIndex.value > 0,
        )
    }

    // ---- AudioEngineListener ----

    override fun onPlaybackStateChanged(
        isPlaying: Boolean,
        isBuffering: Boolean,
    ) {
        _isPlaying.value = isPlaying
        _isBuffering.value = isBuffering
        if (isPlaying) consecutiveErrors = 0
    }

    override fun onProgress(
        positionMs: Long,
        durationMs: Long,
    ) {
        _positionMs.value = positionMs
        if (durationMs > 0) _durationMs.value = durationMs
    }

    override fun onTrackEnded() {
        next()
    }

    override fun onError(message: String) {
        _error.value = message
        _isBuffering.value = false
        consecutiveErrors++
        // Skip unplayable tracks, but never loop forever on a dead queue.
        if (consecutiveErrors < 3) next()
    }

    override fun onRemoteNext() = next()

    override fun onRemotePrevious() = previous()
}

fun SongItem.toPlayerTrack(): PlayerTrack =
    PlayerTrack(
        id = id,
        title = title,
        artist = artists.joinToString { it.name },
        album = album?.name,
        artworkUrl = thumbnail.resizedThumbnail(720),
        durationMs = (duration ?: 0) * 1000L,
    )
