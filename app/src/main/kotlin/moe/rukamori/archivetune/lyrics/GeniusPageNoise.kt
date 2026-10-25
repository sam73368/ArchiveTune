/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

object GeniusPageNoise {
    private const val HEADER_WINDOW = 300
    private const val MAX_PREFACE = 4000

    private val HEADER_WORD = Regex("""\bLyrics\b""")
    private val GLUED_HEADER = Regex("""^[^\n]{0,$HEADER_WINDOW}?\bLyrics(?=[\p{L}"“‘'«])""")
    private val SECTION_MARKER = Regex("""(?m)^\[[^\]\n]{1,80}]""")

    fun stripHeader(text: String): String {
        val window = text.take(HEADER_WINDOW)
        if (!HEADER_WORD.containsMatchIn(window)) return text
        val marker = SECTION_MARKER.find(text) ?: return text
        if (marker.range.first == 0 || marker.range.first > MAX_PREFACE) return text
        val headerEnd = HEADER_WORD.find(window)?.range?.last ?: return text
        if (marker.range.first <= headerEnd) return text
        return text.substring(marker.range.first).trim()
    }

    fun hasGluedHeader(text: String): Boolean = GLUED_HEADER.containsMatchIn(text)
}
