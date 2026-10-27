/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.AlbumItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumKindCatalogTest {
    private val releaseGroups =
        """
        {"release-group-count":4,"release-group-offset":0,"release-groups":[
          {"id":"a","title":"Master of Puppets","primary-type":"Album","secondary-types":[],"first-release-date":"1986-03-03"},
          {"id":"b","title":"S&M2","primary-type":"Album","secondary-types":["Live"],"first-release-date":"2020-08-28"},
          {"id":"c","title":"Garage Inc.","primary-type":"Album","secondary-types":["Compilation"],"first-release-date":"1998-11-24"},
          {"id":"d","title":"Hardwired","primary-type":"EP","first-release-date":"2016"}
        ]}
        """.trimIndent()

    @Test
    fun parsesKindsAndOriginalYears() {
        val page = AlbumKindCatalog.parseReleaseGroups(releaseGroups)
        assertNotNull(page)
        assertEquals(4, page!!.total)
        assertEquals(
            listOf(
                ArtistAlbumGrouping.Kind.ALBUMS,
                ArtistAlbumGrouping.Kind.LIVE,
                ArtistAlbumGrouping.Kind.COMPILATIONS,
                ArtistAlbumGrouping.Kind.EPS,
            ),
            page.entries.map { it.kind },
        )
        assertEquals(1986, page.entries.first().year)
        assertEquals(2016, page.entries.last().year)
    }

    @Test
    fun matchesYoutubeTitlesWithEditionNoise() {
        val entries = AlbumKindCatalog.parseReleaseGroups(releaseGroups)!!.entries

        assertEquals(
            ArtistAlbumGrouping.Kind.ALBUMS,
            AlbumKindCatalog.lookup(entries, "Master of Puppets (Remastered Deluxe Box Set)", 2017)?.kind,
        )
        assertEquals(ArtistAlbumGrouping.Kind.LIVE, AlbumKindCatalog.lookup(entries, "S&M 2", 2020)?.kind)
        assertEquals(1986, AlbumKindCatalog.lookup(entries, "Master of Puppets", 2017)?.year)
        assertNull(AlbumKindCatalog.lookup(entries, "Unknown Album", 2000))
    }

    @Test
    fun ambiguousTitlesUseTheClosestYear() {
        val entries =
            listOf(
                AlbumKindCatalog.Entry("black album", 1991, ArtistAlbumGrouping.Kind.ALBUMS),
                AlbumKindCatalog.Entry("black album", 2005, ArtistAlbumGrouping.Kind.LIVE),
            )

        assertEquals(ArtistAlbumGrouping.Kind.LIVE, AlbumKindCatalog.lookup(entries, "The Black Album", 2004)?.kind)
        assertNull(AlbumKindCatalog.lookup(entries, "The Black Album", null))
    }

    @Test
    fun picksTheArtistWithAnExactNameAndHighScore() {
        val body =
            """
            {"artists":[
              {"id":"wrong","name":"Metallica Tribute","score":100},
              {"id":"right","name":"Metallica","score":98},
              {"id":"low","name":"Metallica","score":40}
            ]}
            """.trimIndent()

        assertEquals("right", AlbumKindCatalog.parseBestArtistId(body, "Metallica"))
        assertNull(AlbumKindCatalog.parseBestArtistId(body, "Megadeth"))
    }

    @Test
    fun liveOverridesAStudioLookingTitleAndReissueYearDoesNotReorder() {
        val live =
            AlbumItem(browseId = "x", playlistId = "x", title = "S&M 2", artists = null, year = 2020, thumbnail = "")
        val studio =
            AlbumItem(browseId = "y", playlistId = "y", title = "Master of Puppets", artists = null, year = 2017, thumbnail = "")
        val overrides =
            mapOf(
                "x" to ArtistAlbumGrouping.Override(ArtistAlbumGrouping.Kind.LIVE, 2020),
                "y" to ArtistAlbumGrouping.Override(ArtistAlbumGrouping.Kind.ALBUMS, 1986),
            )

        val groups = ArtistAlbumGrouping.group(listOf(studio, live), overrides)

        assertEquals(listOf(ArtistAlbumGrouping.Kind.ALBUMS, ArtistAlbumGrouping.Kind.LIVE), groups.map { it.kind })
        assertEquals(
            listOf("Live Through This"),
            ArtistAlbumGrouping
                .group(
                    listOf(
                        AlbumItem(browseId = "z", playlistId = "z", title = "Live Through This", artists = null, year = 1994, thumbnail = ""),
                    ),
                    mapOf("z" to ArtistAlbumGrouping.Override(ArtistAlbumGrouping.Kind.ALBUMS, 1994)),
                ).single()
                .albums
                .map { it.title },
        )
    }
}
