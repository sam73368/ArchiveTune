/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.utils.ExplicitDuplicates.dropCleanTwins
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplicitDuplicatesTest {
    private fun song(
        id: String,
        title: String,
        explicit: Boolean,
        duration: Int = 200,
        artist: String = "Artist",
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(artist, "artist-id")),
        duration = duration,
        thumbnail = "",
        explicit = explicit,
    )

    @Test
    fun `clean edition is dropped when its explicit twin is listed`() {
        val result =
            listOf(song("clean", "Hello", false), song("explicit", "Hello", true)).dropCleanTwins()
        assertEquals(listOf("explicit"), result.map { it.id })
    }

    @Test
    fun `clean song without twin stays`() {
        val result =
            listOf(song("a", "Hello", false), song("b", "Other", true)).dropCleanTwins()
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `different length or edition is not a twin`() {
        val result =
            listOf(
                song("live", "Hello (Live)", false),
                song("explicit", "Hello", true),
                song("long", "Hello", false, duration = 260),
            ).dropCleanTwins()
        assertEquals(listOf("live", "explicit", "long"), result.map { it.id })
    }
}
