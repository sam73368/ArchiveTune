/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.AlbumItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumEditionsTest {
    private fun album(
        id: String,
        title: String,
        year: Int?,
        explicit: Boolean = true,
    ) = AlbumItem(browseId = id, playlistId = id, title = title, artists = null, year = year, thumbnail = "", explicit = explicit)

    private fun edition(
        album: AlbumItem,
        tracks: List<String>?,
    ) = AlbumEditions.Edition(album, tracks)

    private val loadTracks = (1..14).map { "Track $it" }

    @Test
    fun deluxeWithLiveAndDemosStaysInOtherVersions() {
        val original = album("load", "Load", 1996)
        val deluxe = album("deluxe", "Load (Remastered Deluxe Edition)", 1996)
        val box = album("box", "Load (Remastered Deluxe Box Set)", 2026, explicit = false)
        val decision =
            AlbumEditions.decide(
                listOf(
                    edition(original, loadTracks),
                    edition(deluxe, loadTracks + listOf("Ain't My Bitch (Live)", "Hero of the Day (Demo)")),
                    edition(box, loadTracks + (1..136).map { "Live Track $it (Live)" }),
                ),
            )

        assertEquals("load", decision.displayId)
    }

    @Test
    fun updateWithNewStudioSongsReplacesTheOriginal() {
        val original = album("tdoss", "The Death Of Slim Shady", 2024)
        val update = album("cdg", "THE DEATH OF SLIM SHADY (Coup De Grâce)", 2024)
        val originalTracks = (1..19).map { "Song $it" }
        val decision =
            AlbumEditions.decide(
                listOf(
                    edition(original, originalTracks),
                    edition(update, originalTracks + listOf("Fuel", "Antichrist", "Brand New Song (feat. Someone)")),
                ),
            )

        assertEquals("cdg", decision.displayId)
        assertEquals("tdoss", decision.baseId)
    }

    @Test
    fun sameTitleExpandedEditionBeatsTheVersionOriginale() {
        val short = album("short", "La vraie vie (Version originale)", 2017)
        val full = album("full", "La vraie vie", 2017)
        val shortTracks = (1..15).map { "Titre $it" }
        val decision =
            AlbumEditions.decide(
                listOf(edition(short, shortTracks), edition(full, shortTracks + (16..25).map { "Titre $it" })),
            )

        assertEquals("full", decision.displayId)
    }

    @Test
    fun updateWithInstrumentalsOrLateReissueIsNotPromoted() {
        val original = album("a", "Album", 2020)
        val instrumental = album("b", "Album (Deluxe)", 2020)
        val lateReissue = album("c", "Album (Anniversary Edition)", 2025)
        val tracks = (1..10).map { "Song $it" }
        val decision =
            AlbumEditions.decide(
                listOf(
                    edition(original, tracks),
                    edition(instrumental, tracks + listOf("Song 1 (Instrumental)", "Song 2 (Instrumental)")),
                    edition(lateReissue, tracks + listOf("Bonus Song")),
                ),
            )

        assertEquals("a", decision.displayId)
    }

    @Test
    fun explicitTwinWinsOverCleanBase() {
        val clean = album("clean", "Album", 2020, explicit = false)
        val explicit = album("explicit", "Album", 2020, explicit = true)
        val tracks = (1..10).map { "Song $it" }

        val decision = AlbumEditions.decide(listOf(edition(clean, tracks), edition(explicit, tracks)))

        assertEquals("explicit", decision.displayId)
    }

    @Test
    fun withoutTrackListsTheCleanestExplicitEditionIsShown() {
        val original = album("a", "Load", 1996)
        val deluxe = album("b", "Load (Remastered Deluxe Edition)", 1996)
        val box = album("c", "Load (Remastered Deluxe Box Set)", 2026)

        assertEquals("a", AlbumEditions.heuristicDecision(listOf(deluxe, box, original)).displayId)
        assertEquals("a", AlbumEditions.decide(listOf(edition(deluxe, null), edition(box, null), edition(original, null))).displayId)
    }

    @Test
    fun familiesOnlyMergeRealEditions() {
        val families =
            AlbumEditions.families(
                listOf(
                    album("1", "Load", 1996),
                    album("2", "Load (Remastered Deluxe Box Set)", 2026),
                    album("3", "Reload", 1997),
                    album("4", "Garage Inc.", 1998),
                    album("5", "Garage Inc.", 2015),
                ),
            )

        assertEquals(listOf(2, 1, 1, 1), families.map { it.size })
    }

    @Test
    fun studioDetectionAndTrackKeys() {
        assertTrue(AlbumEditions.isStudioLike("Nothing Else Matters"))
        assertTrue(AlbumEditions.isStudioLike("Take On Me"))
        assertFalse(AlbumEditions.isStudioLike("Enter Sandman (Live at Wembley)"))
        assertFalse(AlbumEditions.isStudioLike("One - Instrumental"))
        assertFalse(AlbumEditions.isStudioLike("Olive Tree (Demo)"))
        assertTrue(AlbumEditions.isStudioLike("Olive Tree"))
        assertEquals(AlbumEditions.trackKey("Hero of the Day (Remastered)"), AlbumEditions.trackKey("Hero Of The Day"))
        assertEquals("load", AlbumEditions.familyKey("Load (Remastered Deluxe Edition)"))
        assertEquals("the death of slim shady".replace("the ", ""), AlbumEditions.familyKey("THE DEATH OF SLIM SHADY (Coup De Grâce)"))
    }

    @Test
    fun reloadedEditionIsShownAndTheOriginalIsHidden() {
        val original = album("adhd2", "ADHD 2", 2024)
        val reloaded = album("reloaded", "ADHD 2: Reloaded", 2025)
        val tracks = (1..12).map { "Titre $it" }
        val decision =
            AlbumEditions.decide(
                listOf(edition(original, tracks), edition(reloaded, tracks + listOf("Nouveau 1", "Nouveau 2"))),
            )

        assertEquals("reloaded", decision.displayId)
        assertEquals("adhd2", decision.baseId)
        assertEquals(1, AlbumEditions.families(listOf(original, reloaded)).size)
        assertEquals("reloaded", AlbumEditions.heuristicDecision(listOf(original, reloaded)).displayId)
    }

    @Test
    fun reloadedWithoutYearsStillGroupsWithTheOriginal() {
        val original = album("o", "ADHD 2", null)
        val reloaded = album("r", "ADHD2 Reloaded", null)

        assertEquals(1, AlbumEditions.families(listOf(original, reloaded)).size)
        assertEquals("r", AlbumEditions.heuristicDecision(listOf(original, reloaded)).displayId)
    }

    @Test
    fun subtitleVariantsOfTheSameAlbumAreOneFamily() {
        val families =
            AlbumEditions.families(
                listOf(
                    album("a", "The Death of Slim Shady", 2024),
                    album("b", "The Death of Slim Shady: Coup de Grâce", 2024),
                    album("c", "THE DEATH OF SLIM SHADY (Coup De Grâce)", 2024, explicit = false),
                    album("d", "Music To Be Murdered By", 2020),
                ),
            )

        assertEquals(listOf(3, 1), families.map { it.size })
    }

    @Test
    fun sequelsAreNotMergedWithTheirPredecessor() {
        assertFalse(AlbumEditions.sameFamilyTitles("ADHD", "ADHD 2"))
        assertFalse(AlbumEditions.sameFamilyTitles("Load", "Load Live"))
        assertTrue(AlbumEditions.sameFamilyTitles("Album", "Album (Deluxe)"))
    }

    @Test
    fun unrelatedAlbumsSharingATitleAreSplitByTrackOverlap() {
        val first = album("x", "Greatest Hits", 2000)
        val second = album("y", "Greatest Hits", 2001)
        val clusters =
            AlbumEditions.partition(
                listOf(
                    edition(first, (1..10).map { "Alpha $it" }),
                    edition(second, (1..10).map { "Beta $it" }),
                ),
            )

        assertEquals(2, clusters.size)
    }

    @Test
    fun dashSuffixedNonStudioTracksAreDetected() {
        assertFalse(AlbumEditions.isStudioLike("One \u2013 Instrumental"))
        assertFalse(AlbumEditions.isStudioLike("Song - Live in Paris"))
        assertTrue(AlbumEditions.isStudioLike("Song - Remastered 2011"))
    }
}
