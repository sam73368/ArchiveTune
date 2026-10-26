/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAlbumGroupingTest {
    private fun album(
        title: String,
        year: Int?,
        type: AlbumReleaseType = AlbumReleaseType.ALBUM,
    ) = AlbumItem(
        browseId = title,
        playlistId = title,
        title = title,
        artists = null,
        year = year,
        thumbnail = "",
        releaseType = type,
    )

    @Test
    fun sortsStudioAlbumsNewestFirst() {
        val groups =
            ArtistAlbumGrouping.group(
                listOf(
                    album("Master of Puppets", 1986),
                    album("Ride The Lightning", 1984),
                    album("Kill 'Em All", 1983),
                    album("72 Seasons", 2023),
                    album("Death Magnetic", 2008),
                ),
            )

        assertEquals(1, groups.size)
        assertEquals(
            listOf("72 Seasons", "Death Magnetic", "Master of Puppets", "Ride The Lightning", "Kill 'Em All"),
            groups.single().albums.map { it.title },
        )
    }

    @Test
    fun separatesLiveAlbumsAndKeepsOrderInsideEachGroup() {
        val groups =
            ArtistAlbumGrouping.group(
                listOf(
                    album("Live & Acoustic At The Masonic", 2019),
                    album("Kill 'Em All", 1983),
                    album("Garage Inc.", 1998),
                    album("Live at Wembley", 2003),
                    album("Hardwired", 2016),
                ),
            )

        assertEquals(listOf(ArtistAlbumGrouping.Kind.ALBUMS, ArtistAlbumGrouping.Kind.LIVE), groups.map { it.kind })
        assertEquals(listOf("Hardwired", "Garage Inc.", "Kill 'Em All"), groups[0].albums.map { it.title })
        assertEquals(listOf("Live & Acoustic At The Masonic", "Live at Wembley"), groups[1].albums.map { it.title })
    }

    @Test
    fun placesSinglesAndEpsAfterAlbums() {
        val groups =
            ArtistAlbumGrouping.group(
                listOf(
                    album("Some Single", 2020, AlbumReleaseType.SINGLE),
                    album("Some EP", 2019, AlbumReleaseType.EP),
                    album("An Album", 2010),
                ),
            )

        assertEquals(
            listOf(ArtistAlbumGrouping.Kind.ALBUMS, ArtistAlbumGrouping.Kind.EPS, ArtistAlbumGrouping.Kind.SINGLES),
            groups.map { it.kind },
        )
    }

    @Test
    fun undatedAlbumsGoLast() {
        val sorted = ArtistAlbumGrouping.newestFirst(listOf(album("No year", null), album("Old", 1990), album("New", 2020)))

        assertEquals(listOf("New", "Old", "No year"), sorted.map { it.title })
    }

    @Test
    fun liveDetectionIgnoresWordsContainingLive() {
        assertTrue(ArtistAlbumGrouping.isLive("Live & Acoustic At The Masonic"))
        assertFalse(ArtistAlbumGrouping.isLive("Olive Branch"))
        assertFalse(ArtistAlbumGrouping.isLive("Delivery"))
    }
}
