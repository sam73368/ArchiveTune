/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import javax.inject.Inject

@Immutable
data class SpotifyAlbumUiState(
    val album: SpotifyAlbum? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

@HiltViewModel
class SpotifyAlbumViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: SpotifyLibraryRepository,
    ) : ViewModel() {
        private val albumId: String = savedStateHandle.get<String>("albumId").orEmpty()

        private val _uiState = MutableStateFlow(SpotifyAlbumUiState())
        val uiState: StateFlow<SpotifyAlbumUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            if (albumId.isBlank()) {
                _uiState.value = SpotifyAlbumUiState(isLoading = false, failed = true)
                return
            }
            _uiState.value = SpotifyAlbumUiState(isLoading = true)
            viewModelScope.launch {
                try {
                    val album = repository.album(albumId)
                    _uiState.value = SpotifyAlbumUiState(album = album, isLoading = false)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _uiState.value = SpotifyAlbumUiState(isLoading = false, failed = true)
                }
            }
        }
    }
