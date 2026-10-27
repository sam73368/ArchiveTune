/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.AlbumItem
import java.text.Normalizer
import java.util.Locale

object AlbumEditions {
    const val MAX_UPDATE_YEAR_GAP = 2
    private const val MIN_TRACK_OVERLAP = 0.7

    data class Edition(
        val album: AlbumItem,
        val tracks: List<String>?,
    )

    data class Decision(
        val displayId: String,
        val baseId: String,
    )

    private val familyNoiseWords =
        setOf(
            "remastered", "remaster", "deluxe", "edition", "expanded", "bonus", "anniversary", "collector", "collectors",
            "special", "super", "ultimate", "platinum", "complete", "extended", "box", "set", "version", "originale",
            "original", "explicit", "clean", "reloaded", "revisited", "redux", "the", "a", "an", "le", "la", "les",
        )

    private val editionMarkerWords =
        setOf(
            "remastered", "remaster", "deluxe", "expanded", "anniversary", "collector", "collectors", "special", "super",
            "ultimate", "platinum", "complete", "extended", "box", "bonus", "reloaded", "revisited", "redux", "edition",
        )

    private val nonStudioTrackWords =
        listOf(
            "live", "demo", "demos", "instrumental", "acoustic", "karaoke", "a cappella", "acapella", "remix", "mix",
            "rough", "outtake", "outtakes", "rehearsal", "session", "sessions", "alternate", "alternative", "unplugged",
            "sped up", "slowed", "en vivo", "en direct", "ao vivo", "tour", "interview", "commentary", "soundcheck",
        )

    private fun fold(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")

    fun familyKey(title: String): String =
        fold(title)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in familyNoiseWords }
            .joinToString(" ")

    fun hasEditionMarker(title: String): Boolean {
        val words =
            fold(title)
                .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                .split(' ')
                .filter { it.isNotBlank() }
                .toSet()
        return words.any { it in editionMarkerWords }
    }

    fun isStudioLike(trackTitle: String): Boolean {
        val text = " " + fold(trackTitle).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() + " "
        return nonStudioTrackWords.none { " $it " in text }
    }

    fun trackKey(trackTitle: String): String =
        fold(trackTitle)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\s-\s.*$"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun rawKey(trackTitle: String): String =
        fold(trackTitle).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    fun families(albums: List<AlbumItem>): List<List<AlbumItem>> {
        val byKey = LinkedHashMap<String, MutableList<AlbumItem>>()
        for (album in albums.distinctBy { it.id }) {
            val key = familyKey(album.title)
            if (key.isBlank()) {
                byKey["\u0000${album.id}"] = mutableListOf(album)
            } else {
                byKey.getOrPut(key) { mutableListOf() } += album
            }
        }
        val result = mutableListOf<List<AlbumItem>>()
        for (group in byKey.values) {
            if (group.size < 2) {
                result += group
                continue
            }
            val anyMarker = group.any { hasEditionMarker(it.title) }
            if (anyMarker) {
                result += group
                continue
            }
            val years = group.mapNotNull { it.year }
            val closeInTime = years.size == group.size && (years.max() - years.min()) <= MAX_UPDATE_YEAR_GAP
            if (closeInTime) {
                result += group
            } else {
                group.forEach { result += listOf(it) }
            }
        }
        return result
    }

    private fun orderForFallback(
        a: AlbumItem,
        b: AlbumItem,
    ): Int {
        if (a.explicit != b.explicit) return if (a.explicit) -1 else 1
        val markerA = hasEditionMarker(a.title)
        val markerB = hasEditionMarker(b.title)
        if (markerA != markerB) return if (markerA) 1 else -1
        return compareValues(a.year ?: Int.MAX_VALUE, b.year ?: Int.MAX_VALUE)
    }

    fun decide(family: List<Edition>): Decision {
        require(family.isNotEmpty())
        if (family.size == 1) return Decision(family[0].album.id, family[0].album.id)

        val base =
            family
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<Edition>> { it.value.album.year ?: Int.MAX_VALUE }
                        .thenBy { if (hasEditionMarker(it.value.album.title)) 1 else 0 }
                        .thenBy { it.value.tracks?.size ?: Int.MAX_VALUE }
                        .thenBy { it.index },
                ).first()
                .value

        val baseTracks = base.tracks
        val candidates = mutableListOf(base)
        if (baseTracks != null && baseTracks.isNotEmpty()) {
            val baseKeys = baseTracks.map(::trackKey).filter { it.isNotBlank() }.toSet()
            val baseRawKeys = baseTracks.map(::rawKey).toSet()
            for (edition in family) {
                if (edition === base) continue
                val tracks = edition.tracks ?: continue
                val keys = tracks.map(::trackKey).toSet()
                val overlap = if (baseKeys.isEmpty()) 0.0 else baseKeys.count { it in keys }.toDouble() / baseKeys.size
                if (overlap < MIN_TRACK_OVERLAP) continue
                val yearGap =
                    if (base.album.year != null && edition.album.year != null) edition.album.year!! - base.album.year!! else null
                if (yearGap == null || yearGap < 0 || yearGap > MAX_UPDATE_YEAR_GAP) continue
                val nonStudioNew = tracks.filter { !isStudioLike(it) && rawKey(it) !in baseRawKeys }
                if (nonStudioNew.isNotEmpty()) continue
                val extras = tracks.filter { trackKey(it) !in baseKeys }
                val explicitTwin = extras.isEmpty() && edition.album.explicit && !base.album.explicit
                if (!explicitTwin && extras.isEmpty()) continue
                candidates += edition
            }
        } else {
            val fallback = family.map { it.album }.sortedWith(::orderForFallback).first()
            return Decision(fallback.id, base.album.id)
        }

        val display =
            candidates
                .sortedWith(
                    compareByDescending<Edition> { it.tracks?.size ?: 0 }
                        .thenByDescending { it.album.explicit }
                        .thenBy { it.album.year ?: Int.MAX_VALUE },
                ).first()
        return Decision(display.album.id, base.album.id)
    }

    fun heuristicDecision(family: List<AlbumItem>): Decision {
        val base =
            family
                .sortedWith(
                    compareBy<AlbumItem> { it.year ?: Int.MAX_VALUE }.thenBy { if (hasEditionMarker(it.title)) 1 else 0 },
                ).first()
        val display = family.sortedWith(::orderForFallback).first()
        return Decision(display.id, base.id)
    }
}
