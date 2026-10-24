/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

object LyricsUncensor {
    private val maskedToken = Regex("""(?<![\p{L}*])\p{L}*\*+[\p{L}*]*(?![\p{L}*])""")

    private val dominantWords = setOf("fuck", "shit", "bitch", "ass")

    private val dictionary: Map<Int, List<String>> =
        listOf(
            "fuck", "fucks", "fucked", "fucking", "fuckin", "fucker", "fuckers", "motherfucker", "motherfuckers",
            "motherfucking", "motherfuckin", "shit", "shits", "shitty", "shitting", "bullshit", "bitch", "bitches",
            "bitchin", "bastard", "bastards", "ass", "asses", "asshole", "assholes", "jackass", "dumbass", "badass",
            "dick", "dicks", "dickhead", "cock", "cocks", "pussy", "pussies", "cunt", "cunts", "whore", "whores",
            "slut", "sluts", "nigga", "niggas", "nigger", "niggers", "hoe", "hoes", "damn", "goddamn", "dammit",
            "goddammit", "piss", "pissed", "putain", "pute", "putes", "merde", "connard", "connards", "connasse",
            "salope", "salopes", "encule", "enculé", "enculer", "bordel",
        ).distinct().groupBy { it.length }

    fun restore(lyrics: String): String {
        if ('*' !in lyrics) return lyrics
        return maskedToken.replace(lyrics) { match -> restoreToken(match.value) ?: match.value }
    }

    fun maskedWordCount(lyrics: String): Int {
        if ('*' !in lyrics) return 0
        return maskedToken.findAll(lyrics).count { looksCensored(it.value) }
    }

    private fun looksCensored(token: String): Boolean =
        token.first().isLetter() || token.count { it == '*' } >= 3

    private fun restoreToken(token: String): String? {
        if (token.none { it.isLetter() }) return null
        val candidates =
            dictionary[token.length].orEmpty().filter { word ->
                word.indices.all { index -> token[index] == '*' || token[index].lowercaseChar() == word[index] }
            }
        val word =
            when {
                candidates.size == 1 -> candidates.first()
                else -> candidates.firstOrNull { it in dominantWords }
            } ?: return null
        return matchCase(word, token)
    }

    private fun matchCase(
        word: String,
        token: String,
    ): String {
        val letters = token.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> word.uppercase()
            token.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
