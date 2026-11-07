/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.audiosource.TrackMatching

internal data class VoiceAlbumRequest(
    val albumName: String,
    val artistName: String,
)

internal object VoiceRequest {
    private const val AUDIO_FOCUS_SUFFIX = "/audio"
    private const val ALBUM_FOCUS_SUFFIX = "/album"
    private const val QUEUE_LIMIT = 25

    private val albumWord = Regex("""(?:\b(?:the|le|un)\s+|\bl['’])?\balbums?\b""", RegexOption.IGNORE_CASE)
    private val variantWord = Regex("""\b(live|remix|acoustic|demo|instrumental|karaoke|reprise)\b""")

    fun albumRequest(
        focus: String?,
        title: String?,
        album: String?,
        artist: String?,
        query: String?,
    ): VoiceAlbumRequest? {
        val titleText = title.orEmpty().trim()
        val albumText = album.orEmpty().trim()
        val artistText = artist.orEmpty().trim()
        val queryText = query.orEmpty().trim()
        val focusText = focus.orEmpty().trim().lowercase()
        val focusIsAlbum = focusText.endsWith(ALBUM_FOCUS_SUFFIX)
        val focusIsSong = focusText.endsWith(AUDIO_FOCUS_SUFFIX)
        val sameName =
            albumText.isNotBlank() &&
                titleText.isNotBlank() &&
                TrackMatching.normalize(albumText) == TrackMatching.normalize(titleText)
        val structuredAlbum =
            focusIsAlbum ||
                (albumText.isNotBlank() && (titleText.isBlank() || (sameName && !focusIsSong)))
        if (structuredAlbum) {
            val name =
                albumText
                    .ifBlank { titleText }
                    .ifBlank { stripAlbumWord(queryText) }
            return name.takeIf { it.isNotBlank() }?.let { VoiceAlbumRequest(it, artistText) }
        }
        val spokenAlbum = titleText.isBlank() && albumText.isBlank() && albumWord.containsMatchIn(queryText)
        if (!spokenAlbum) return null
        val name = stripAlbumWord(queryText)
        return name.takeIf { it.isNotBlank() }?.let { VoiceAlbumRequest(it, artistText) }
    }

    fun <T> coherentSongQueue(
        items: List<T>,
        query: String,
        artistOf: (T) -> String,
        titleOf: (T) -> String,
    ): List<T> {
        val first = items.firstOrNull() ?: return items
        val wantedQuery = TrackMatching.normalize(query)
        val firstTitle = TrackMatching.normalizeTitle(titleOf(first))
        if (firstTitle.isBlank() || !wantedQuery.contains(firstTitle)) return items
        val firstArtist = TrackMatching.normalize(artistOf(first))
        if (firstArtist.isBlank()) return items
        val firstIsVariant = isVariant(titleOf(first))
        val seen = HashSet<String>()
        return items
            .filterIndexed { index, item ->
                val artist = TrackMatching.normalize(artistOf(item))
                val sameArtist = artist.contains(firstArtist) || firstArtist.contains(artist)
                val keep = index == 0 || (sameArtist && (firstIsVariant || !isVariant(titleOf(item))))
                keep && seen.add("${TrackMatching.normalizeTitle(titleOf(item))}|$artist")
            }.take(QUEUE_LIMIT)
    }

    private const val SIMILAR_TITLE_THRESHOLD = 0.82

    fun songAnswersQuery(
        title: String?,
        query: String,
    ): Boolean {
        val wanted = TrackMatching.normalize(query)
        val found = TrackMatching.normalizeTitle(title.orEmpty())
        if (wanted.isEmpty() || found.isEmpty()) return false
        if (similarTitles(found, wanted)) return true
        return wanted.contains(found) && found.length * 2 >= wanted.length
    }

    fun similarTitles(
        candidate: String,
        wanted: String,
    ): Boolean {
        val a = TrackMatching.normalizeTitle(candidate).replace(" ", "")
        val b = TrackMatching.normalizeTitle(wanted).replace(" ", "")
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val longest = maxOf(a.length, b.length)
        if (longest < 6) return false
        if (kotlin.math.abs(a.length - b.length) * 5 > longest) return false
        return 1.0 - editDistance(a, b).toDouble() / longest >= SIMILAR_TITLE_THRESHOLD
    }

    private fun editDistance(
        a: String,
        b: String,
    ): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun stripAlbumWord(text: String): String =
        albumWord
            .replace(text, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isVariant(title: String): Boolean = variantWord.containsMatchIn(TrackMatching.normalize(title))
}
