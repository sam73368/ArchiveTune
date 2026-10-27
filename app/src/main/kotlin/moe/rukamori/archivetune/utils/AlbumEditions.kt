/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object AlbumEditions {
    const val MAX_UPDATE_YEAR_GAP = 2
    private const val MIN_TRACK_OVERLAP = 0.5

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

    private val updateMarkerWords = setOf("reloaded", "revisited", "redux", "reimagined")

    private val sequelWords =
        setOf(
            "part", "pt", "vol", "volume", "chapter", "ii", "iii", "iv", "vi", "vii", "viii", "ix", "x", "live",
            "unplugged", "soundtrack", "ost", "best", "hits", "greatest", "remixes", "instrumentals", "sessions",
        )

    private val nonStudioMarkers =
        listOf(
            "live", "demo", "demos", "instrumental", "instrumentals", "acoustic", "karaoke", "a cappella", "acapella",
            "remix", "mix", "rough mix", "outtake", "outtakes", "rehearsal", "session", "sessions", "alternate",
            "alternative", "unplugged", "sped up", "slowed", "en vivo", "en direct", "ao vivo", "interview",
            "commentary", "soundcheck", "stripped", "orchestral",
        )

    private fun fold(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")

    private fun words(raw: String): List<String> =
        fold(raw).replace(Regex("""[^\p{L}\p{N}]+"""), " ").split(' ').filter { it.isNotBlank() }

    fun familyKey(title: String): String =
        fold(title)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in familyNoiseWords }
            .joinToString(" ")

    fun hasEditionMarker(title: String): Boolean = words(title).any { it in editionMarkerWords }

    fun hasUpdateMarker(title: String): Boolean = words(title).any { it in updateMarkerWords }

    private fun prefixRelated(
        first: String,
        second: String,
    ): Boolean {
        val a = first.split(' ').filter { it.isNotBlank() }
        val b = second.split(' ').filter { it.isNotBlank() }
        val (short, long) = if (a.size <= b.size) a to b else b to a
        if (short.size < 2 || long.size == short.size) return false
        if (long.take(short.size) != short) return false
        val extra = long.drop(short.size)
        return extra.none { token -> token in sequelWords || token.all { it.isDigit() } }
    }

    private fun keysRelated(
        first: String,
        second: String,
    ): Boolean =
        first.isNotBlank() &&
            second.isNotBlank() &&
            (first == second || first.replace(" ", "") == second.replace(" ", "") || prefixRelated(first, second))

    fun sameFamilyTitles(
        first: String,
        second: String,
    ): Boolean = keysRelated(familyKey(first), familyKey(second))

    fun sharesArtist(
        first: AlbumItem,
        second: AlbumItem,
    ): Boolean {
        val a = first.artists.orEmpty()
        val b = second.artists.orEmpty()
        if (a.isEmpty() || b.isEmpty()) return true
        return a.any { left ->
            b.any { right ->
                (left.id != null && left.id == right.id) ||
                    AlbumKindCatalog.normalizeArtist(left.name) == AlbumKindCatalog.normalizeArtist(right.name)
            }
        }
    }

    private fun isFullAlbum(album: AlbumItem): Boolean = album.releaseType == AlbumReleaseType.ALBUM

    private fun allowedGap(album: AlbumItem): Int = MAX_UPDATE_YEAR_GAP + if (hasUpdateMarker(album.title)) 1 else 0

    private fun yearsCompatible(
        a: AlbumItem,
        b: AlbumItem,
    ): Boolean {
        val ya = a.year
        val yb = b.year
        return ya == null || yb == null || abs(ya - yb) <= MAX_UPDATE_YEAR_GAP
    }

    fun isStudioLike(trackTitle: String): Boolean {
        val folded = fold(trackTitle).replace(" \u2013 ", " - ")
        val bracketText = Regex("""\(([^)]*)\)|\[([^]]*)]""").findAll(folded).joinToString(" ") { it.value }
        val dashText = folded.substringAfter(" - ", "").substringAfter(" – ", "")
        val suffix = " " + (bracketText + " " + dashText).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() + " "
        if (nonStudioMarkers.any { " $it " in suffix }) return false
        val head = words(trackTitle).take(2).joinToString(" ")
        return !(head == "live at" || head == "live in" || head == "live from" || head == "demo of")
    }

    fun trackKey(trackTitle: String): String =
        fold(trackTitle)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\s[-–]\s.*$"""), " ")
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun rawKey(trackTitle: String): String = fold(trackTitle).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    fun families(albums: List<AlbumItem>): List<List<AlbumItem>> {
        val items = albums.distinctBy { it.id }
        val keys = items.map { familyKey(it.title) }
        val parent = IntArray(items.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                if (!keysRelated(keys[i], keys[j])) continue
                if (isFullAlbum(items[i]) != isFullAlbum(items[j])) continue
                val marker = hasEditionMarker(items[i].title) || hasEditionMarker(items[j].title)
                if (marker || yearsCompatible(items[i], items[j])) {
                    parent[find(j)] = find(i)
                }
            }
        }
        val groups = LinkedHashMap<Int, MutableList<AlbumItem>>()
        for (index in items.indices) groups.getOrPut(find(index)) { mutableListOf() } += items[index]
        return groups.values.toList()
    }

    private fun overlap(
        baseKeys: Set<String>,
        tracks: List<String>,
    ): Double {
        if (baseKeys.isEmpty()) return 0.0
        val keys = tracks.map(::trackKey).toSet()
        return baseKeys.count { it in keys }.toDouble() / baseKeys.size
    }

    private fun earliest(editions: List<Edition>): Edition =
        editions
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Edition>> { it.value.album.year ?: Int.MAX_VALUE }
                    .thenBy { if (hasEditionMarker(it.value.album.title)) 1 else 0 }
                    .thenBy { it.value.tracks?.size ?: Int.MAX_VALUE }
                    .thenBy { it.index },
            ).first()
            .value

    fun partition(editions: List<Edition>): List<List<Edition>> {
        if (editions.size < 2 || editions.any { it.tracks.isNullOrEmpty() }) return listOf(editions)
        val remaining = editions.toMutableList()
        val clusters = mutableListOf<List<Edition>>()
        while (remaining.isNotEmpty()) {
            val base = earliest(remaining)
            val baseKeys = base.tracks.orEmpty().map(::trackKey).filter { it.isNotBlank() }.toSet()
            val cluster =
                remaining.filter {
                    it === base ||
                        (
                            overlap(baseKeys, it.tracks.orEmpty()) >= MIN_TRACK_OVERLAP &&
                                !(baseKeys.size < 4 && it.tracks.orEmpty().size >= baseKeys.size * 3)
                        )
                }
            clusters += cluster
            remaining.removeAll(cluster.toSet())
        }
        return clusters
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

        val base = earliest(family)
        val baseTracks = base.tracks
        if (baseTracks.isNullOrEmpty()) {
            return heuristicDecision(family.map { it.album })
        }

        val baseKeys = baseTracks.map(::trackKey).filter { it.isNotBlank() }.toSet()
        val baseRawKeys = baseTracks.map(::rawKey).toSet()
        val candidates = mutableListOf(base)
        for (edition in family) {
            if (edition === base) continue
            val tracks = edition.tracks ?: continue
            if (overlap(baseKeys, tracks) < MIN_TRACK_OVERLAP) continue
            val baseYear = base.album.year
            val editionYear = edition.album.year
            if (baseYear != null && editionYear != null) {
                val gap = editionYear - baseYear
                if (gap < 0 || gap > allowedGap(edition.album)) continue
            }
            if (tracks.any { !isStudioLike(it) && rawKey(it) !in baseRawKeys }) continue
            val extras = tracks.filter { trackKey(it) !in baseKeys }
            val explicitTwin = extras.isEmpty() && edition.album.explicit && !base.album.explicit
            if (!explicitTwin && extras.isEmpty()) continue
            candidates += edition
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
        val updates =
            family.filter { edition ->
                edition.id != base.id &&
                    hasUpdateMarker(edition.title) &&
                    (base.year == null || edition.year == null || edition.year!! - base.year!! in 0..allowedGap(edition))
            }
        val display =
            if (updates.isNotEmpty()) {
                updates.sortedWith(compareByDescending<AlbumItem> { it.explicit }.thenByDescending { it.year ?: 0 }).first()
            } else {
                family.sortedWith(::orderForFallback).first()
            }
        return Decision(display.id, base.id)
    }
}
