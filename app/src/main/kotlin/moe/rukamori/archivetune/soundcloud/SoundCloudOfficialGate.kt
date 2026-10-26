/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.soundcloud

import java.text.Normalizer
import java.util.Locale

object SoundCloudOfficialGate {
    const val MAX_DURATION_DIFF_MS = 6_000L

    data class Candidate(
        val title: String,
        val artistNames: List<String>,
        val album: String?,
        val isrc: String?,
        val explicit: Boolean?,
        val verifiedUploader: Boolean,
        val uploaderName: String?,
        val publisherArtist: String?,
        val labelName: String?,
        val description: String?,
        val durationMs: Long?,
    )

    data class Wanted(
        val title: String,
        val artists: List<String>,
        val album: String?,
        val isrc: String?,
        val explicit: Boolean,
        val durationMs: Long?,
    )

    data class Verdict(
        val accepted: Boolean,
        val reason: String,
    )

    private val titleVersionPatterns =
        listOf(
            "live", "en vivo", "ao vivo", "en direct", "concert", "tour", "session", "sessions", "unplugged",
            "acoustic", "acoustique", "demo", "rehearsal", "soundboard", "bootleg", "remix", "rework", "mashup",
            "mash up", "flip", "edit", "cover", "tribute", "karaoke", "instrumental", "a cappella", "acapella",
            "sped up", "speed up", "slowed", "reverb", "nightcore", "8d", "bass boosted", "chopped", "screwed",
            "lofi", "lo fi", "extended", "radio edit", "vip", "re recorded", "rerecorded", "preview", "snippet",
            "reprise", "alternative take", "outtake", "unreleased", "leak", "leaked",
        )

    private val cleanMarkers = listOf("clean", "censored", "radio edit", "family friendly", "no swearing")

    private val descriptionRedFlags =
        Regex(
            """\b(live (at|in|from|on|@|session|version|performance)|recorded live|filmed live|bootleg|rehearsal|""" +
                """soundboard|audience recording|cover of|re-?recorded|remix|demo version|sped up|slowed|nightcore|""" +
                """8d audio|tribute|fan[- ]?made|unofficial|not (the )?official|re-?upload|ripped from|fan edit|""" +
                """bass boosted|karaoke|instrumental version)\b""",
            RegexOption.IGNORE_CASE,
        )

    fun normalize(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    fun containsVersionMarker(value: String?): Boolean = markersIn(value).isNotEmpty()

    fun markersIn(value: String?): Set<String> {
        val text = " ${normalize(value)} "
        if (text.isBlank()) return emptySet()
        return titleVersionPatterns.filterTo(linkedSetOf()) { " $it " in text }
    }

    fun versionMismatch(
        wantedText: String?,
        candidateText: String?,
    ): Boolean {
        val wanted = markersIn(wantedText)
        val candidate = markersIn(candidateText)
        return candidate.any { it !in wanted }
    }

    fun descriptionLooksUnofficial(
        wantedText: String?,
        description: String?,
    ): Boolean {
        val text = description?.take(1200)?.takeIf { it.isNotBlank() } ?: return false
        val wanted = wantedText.orEmpty()
        val flags = descriptionRedFlags.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()
        if (flags.isEmpty()) return false
        return flags.any { flag -> !wanted.contains(flag, ignoreCase = true) && !wanted.contains(flag.substringBefore(' '), ignoreCase = true) }
    }

    private fun isrcOf(value: String?): String? =
        value?.uppercase(Locale.ROOT)?.filter { it.isLetterOrDigit() }?.takeIf { it.length == 12 }

    private fun artistMatches(
        wanted: String,
        candidate: String,
    ): Boolean {
        val a = normalize(wanted)
        val b = normalize(candidate)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a in b || b in a)) return true
        val aTokens = a.split(' ').filter { it.length > 1 }.toSet()
        val bTokens = b.split(' ').filter { it.length > 1 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return false
        val overlap = aTokens.intersect(bTokens).size
        return if (aTokens.size <= 1) overlap == aTokens.size else overlap >= aTokens.size - 1
    }

    private fun anyArtistMatches(
        wanted: List<String>,
        candidate: String?,
    ): Boolean = candidate != null && wanted.any { artistMatches(it, candidate) }

    fun evaluate(
        wanted: Wanted,
        candidate: Candidate,
    ): Verdict {
        val wantedText = listOf(wanted.title, wanted.album.orEmpty()).joinToString(" ")
        if (versionMismatch(wanted.title, candidate.title)) {
            return Verdict(false, "title carries a different version marker")
        }
        if (candidate.album != null && versionMismatch(wanted.album ?: wanted.title, candidate.album)) {
            return Verdict(false, "album carries a different version marker")
        }
        if (descriptionLooksUnofficial(wantedText, candidate.description)) {
            return Verdict(false, "description flags a live, cover or unofficial upload")
        }

        val wantedDuration = wanted.durationMs?.takeIf { it > 0L }
        val candidateDuration = candidate.durationMs?.takeIf { it > 0L }
        val wantedIsrc = isrcOf(wanted.isrc)
        val isrcMatch = wantedIsrc != null && wantedIsrc == isrcOf(candidate.isrc)
        val isrcConflict = wantedIsrc != null && isrcOf(candidate.isrc) != null && !isrcMatch
        if (isrcConflict) return Verdict(false, "ISRC differs from the studio recording")

        if (wantedDuration != null && candidateDuration != null) {
            if (kotlin.math.abs(wantedDuration - candidateDuration) > MAX_DURATION_DIFF_MS) {
                return Verdict(false, "duration differs from the studio recording")
            }
        } else if (!isrcMatch) {
            return Verdict(false, "duration cannot be verified")
        }

        if (wanted.explicit) {
            if (candidate.explicit == false) return Verdict(false, "clean edit while the explicit version is wanted")
            if (cleanMarkers.any { " $it " in " ${normalize(candidate.title)} " }) {
                return Verdict(false, "clean edit marker in title")
            }
            if (candidate.explicit == null && !isrcMatch) {
                return Verdict(false, "explicit flag unknown")
            }
        }

        if (isrcMatch) return Verdict(true, "ISRC match")

        val artistOk =
            anyArtistMatches(wanted.artists, candidate.publisherArtist) ||
                (candidate.verifiedUploader && anyArtistMatches(wanted.artists, candidate.uploaderName)) ||
                (candidate.labelName != null && candidate.artistNames.any { anyArtistMatches(wanted.artists, it) })
        if (!artistOk) return Verdict(false, "no official artist signal")

        val hasReleaseMetadata =
            !candidate.album.isNullOrBlank() || !candidate.isrc.isNullOrBlank() || !candidate.labelName.isNullOrBlank()
        val official =
            (candidate.verifiedUploader && anyArtistMatches(wanted.artists, candidate.uploaderName)) ||
                (anyArtistMatches(wanted.artists, candidate.publisherArtist) && hasReleaseMetadata)
        if (!official) return Verdict(false, "no official release metadata")

        val wantedAlbum = normalize(wanted.album)
        val candidateAlbum = normalize(candidate.album)
        if (wantedAlbum.isNotBlank() && candidateAlbum.isNotBlank() &&
            !(wantedAlbum.contains(candidateAlbum) || candidateAlbum.contains(wantedAlbum))
        ) {
            val sharedTokens = wantedAlbum.split(' ').toSet().intersect(candidateAlbum.split(' ').toSet())
            if (sharedTokens.none { it.length > 3 }) return Verdict(false, "album differs from the studio release")
        }
        return Verdict(true, "official release metadata")
    }
}
