/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSyncOffsetsTest {
    @Test
    fun `stores and reads an offset per song`() {
        var raw = LyricsSyncOffsets.update(null, "a", 250)
        raw = LyricsSyncOffsets.update(raw, "b", -125)
        assertEquals(250, LyricsSyncOffsets.read(raw, "a"))
        assertEquals(-125, LyricsSyncOffsets.read(raw, "b"))
        assertEquals(0, LyricsSyncOffsets.read(raw, "c"))
    }

    @Test
    fun `zero removes the entry and values are clamped`() {
        var raw = LyricsSyncOffsets.update(null, "a", 250)
        raw = LyricsSyncOffsets.update(raw, "a", 0)
        assertEquals(0, LyricsSyncOffsets.read(raw, "a"))
        assertEquals("", raw)
        assertEquals(3000, LyricsSyncOffsets.read(LyricsSyncOffsets.update(null, "x", 99999), "x"))
    }

    @Test
    fun `oldest entries are dropped past the cap and bad lines are ignored`() {
        var raw: String? = "garbage\nno-number\tabc"
        repeat(LyricsSyncOffsets.MAX_ENTRIES + 10) { raw = LyricsSyncOffsets.update(raw, "song$it", 100) }
        assertEquals(0, LyricsSyncOffsets.read(raw, "song0"))
        assertEquals(100, LyricsSyncOffsets.read(raw, "song${LyricsSyncOffsets.MAX_ENTRIES + 9}"))
    }
}
