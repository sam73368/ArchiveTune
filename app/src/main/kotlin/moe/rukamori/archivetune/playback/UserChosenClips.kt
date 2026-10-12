/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Music videos are never part of the app: every song, album track or recommendation plays as its
 * studio recording. The single exception is a clip the user picks in the search results (with the
 * "Music videos" switch on): it is registered here so the player shows and plays it as a clip.
 */
object UserChosenClips {
    private val chosen = MutableStateFlow<Set<String>>(emptySet())

    val ids: StateFlow<Set<String>> = chosen.asStateFlow()

    fun add(mediaId: String) {
        chosen.update { (it + mediaId).takeLast(MAX_REMEMBERED).toSet() }
    }

    fun contains(mediaId: String?): Boolean = mediaId != null && mediaId in chosen.value

    private const val MAX_REMEMBERED = 50

    private fun Set<String>.takeLast(count: Int): List<String> = toList().takeLast(count)
}
