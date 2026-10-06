/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.YTItem

enum class Tab { HOME, SEARCH, LIBRARY }

sealed interface Screen {
    data class Album(
        val browseId: String,
    ) : Screen

    data class Artist(
        val browseId: String,
    ) : Screen

    data class Playlist(
        val playlistId: String,
    ) : Screen

    data class ArtistItems(
        val title: String,
        val endpoint: BrowseEndpoint,
    ) : Screen
}

/** Minimal per-tab back stack (each tab keeps its own history, like the Android app). */
class Navigator {
    var tab = mutableStateOf(Tab.HOME)
        private set

    private val stacks: Map<Tab, MutableList<Screen>> = Tab.entries.associateWith { mutableStateListOf() }

    val current: Screen?
        get() = stacks.getValue(tab.value).lastOrNull()

    val canGoBack: Boolean
        get() = stacks.getValue(tab.value).isNotEmpty()

    fun selectTab(newTab: Tab) {
        if (tab.value == newTab) stacks.getValue(newTab).clear() else tab.value = newTab
    }

    fun push(screen: Screen) {
        stacks.getValue(tab.value).add(screen)
    }

    fun back() {
        val stack = stacks.getValue(tab.value)
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Opens albums, artists and playlists; returns false for songs (caller plays them). */
    fun open(item: YTItem): Boolean =
        when (item) {
            is AlbumItem -> {
                push(Screen.Album(item.browseId))
                true
            }
            is ArtistItem -> {
                push(Screen.Artist(item.id))
                true
            }
            is PlaylistItem -> {
                push(Screen.Playlist(item.id))
                true
            }
            else -> false
        }
}
