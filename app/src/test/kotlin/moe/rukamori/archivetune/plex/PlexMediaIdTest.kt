/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexMediaIdTest {
    @Test
    fun trackIdRoundTrips() {
        val id = plexTrackMediaId("abc123", "4567")
        assertEquals("plex://track/abc123/4567", id)
        assertTrue(id.isPlexMediaId())
        assertEquals("4567", plexRatingKeyOf(id))
    }

    @Test
    fun otherIdsAreNotPlex() {
        assertFalse("dQw4w9WgXcQ".isPlexMediaId())
        assertFalse("content://media/external/audio/1".isPlexMediaId())
        assertNull(plexRatingKeyOf("telegram://track/v2/1/2"))
    }

    @Test
    fun mimeTypesFollowTheContainer() {
        assertEquals("audio/flac", PlexLibrarySync.mimeTypeOf("flac", "flac"))
        assertEquals("audio/mpeg", PlexLibrarySync.mimeTypeOf("mp3", null))
        assertEquals("audio/mp4", PlexLibrarySync.mimeTypeOf(null, "aac"))
        assertEquals("", PlexLibrarySync.mimeTypeOf("wma", null))
    }
}
