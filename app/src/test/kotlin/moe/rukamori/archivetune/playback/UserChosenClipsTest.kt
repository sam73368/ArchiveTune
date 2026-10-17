/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserChosenClipsTest {
    @Test
    fun onlyRegisteredClipsAreExempt() {
        assertFalse(UserChosenClips.contains("clip-test-1"))
        assertFalse(UserChosenClips.contains(null))
        UserChosenClips.add("clip-test-1")
        assertTrue(UserChosenClips.contains("clip-test-1"))
        assertFalse(UserChosenClips.contains("some-album-track"))
    }

    @Test
    fun registryIsBounded() {
        repeat(200) { UserChosenClips.add("clip-bound-$it") }
        assertTrue(UserChosenClips.ids.value.size <= 50)
        assertTrue(UserChosenClips.contains("clip-bound-199"))
        assertFalse(UserChosenClips.contains("clip-bound-0"))
    }
}
