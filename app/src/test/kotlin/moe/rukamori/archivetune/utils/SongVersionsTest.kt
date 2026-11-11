/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.utils.SongVersions.dropSingleTwins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVersionsTest {
    private fun song(
        id: String,
        title: String,
        artist: String,
        album: String?,
        views: Long? = null,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist, id = "artist-$artist")),
        album = album?.let { Album(name = it, id = "album-$it") },
        thumbnail = "",
        viewCount = views,
    )

    @Test
    fun singleIsDetectedFromAlbumName() {
        assertTrue(SongVersions.isSingleRelease("Without Me", "Without Me"))
        assertTrue(SongVersions.isSingleRelease("Without Me", "Without Me - Single"))
        assertFalse(SongVersions.isSingleRelease("Without Me", "The Eminem Show"))
        assertFalse(SongVersions.isSingleRelease("Without Me", null))
    }

    @Test
    fun liveVersionsAreNotGroupedWithStudioVersions() {
        val studio = SongVersions.groupKeyOf("Without Me", "Eminem")
        val live = SongVersions.groupKeyOf("Without Me (Live)", "Eminem")
        assertEquals(studio, SongVersions.groupKeyOf("Without Me (Deluxe)", "Eminem"))
        assertNotEquals(studio, live)
    }

    @Test
    fun mergeKeepsAlbumVersionWithHighestViews() {
        val single = song("a", "Without Me", "Eminem", "Without Me", views = 100)
        val album = song("b", "Without Me", "Eminem", "The Eminem Show", views = 50)
        val merged = SongVersions.mergeSameSongs(listOf(single, album))
        assertEquals(1, merged.size)
        assertEquals("b", merged.single().id)
        assertEquals(100L, merged.single().viewCount)
    }

    @Test
    fun singleTwinsAreDroppedOnlyWhenAnAlbumVersionIsPresent() {
        val single = song("a", "Without Me", "Eminem", "Without Me")
        val album = song("b", "Without Me", "Eminem", "The Eminem Show")
        val other = song("c", "Lose Yourself", "Eminem", "Lose Yourself")
        assertEquals(listOf(album, other), listOf(single, album, other).dropSingleTwins())
        assertEquals(listOf(single, other), listOf(single, other).dropSingleTwins())
    }

    @Test
    fun albumLessSongLosesToAlbumVersion() {
        val bare = song("a", "Without Me", "Eminem", null, views = 100)
        val album = song("b", "Without Me", "Eminem", "The Eminem Show", views = 50)
        assertTrue(SongVersions.needsAlbumLookup(bare))
        assertFalse(SongVersions.needsAlbumLookup(album))
        assertEquals("b", SongVersions.mergeSameSongs(listOf(bare, album)).single().id)
        assertEquals(listOf(album), listOf(bare, album).dropSingleTwins())
    }

    @Test
    fun prefixMatchesRankByPopularity() {
        val exactButObscure = song("a", "Without M", "Nobody", "Album", views = 50)
        val popular = song("b", "Without Me", "Eminem", "The Eminem Show", views = 1_000)
        val sorted = SongVersions.sortByPopularity(listOf(exactButObscure, popular), "without m")
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }

    @Test
    fun exactTitleMatchesComeFirstThenPopularity() {
        val unrelated = song("a", "Something Else", "Someone", "Album", views = 1_000)
        val lessPopular = song("b", "Without Me", "Eminem", "The Eminem Show", views = 10)
        val morePopular = song("c", "Without Me", "Halsey", "Manic", views = 500)
        val sorted = SongVersions.sortByPopularity(listOf(unrelated, lessPopular, morePopular), "without me")
        assertEquals(listOf("c", "b", "a"), sorted.map { it.id })
    }
}
