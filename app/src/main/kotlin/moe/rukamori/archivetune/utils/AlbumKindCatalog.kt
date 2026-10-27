/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object AlbumKindCatalog {
    @Serializable
    data class Entry(
        val title: String,
        val year: Int?,
        val kind: ArtistAlbumGrouping.Kind,
    )

    data class Page(
        val total: Int,
        val entries: List<Entry>,
    )

    private val noiseWords =
        setOf("remastered", "remaster", "deluxe", "edition", "expanded", "bonus", "anniversary", "explicit", "clean", "the")

    private val otherSecondaryTypes =
        setOf(
            "compilation", "remix", "dj-mix", "mixtape/street", "demo", "soundtrack", "spokenword", "interview",
            "audiobook", "audio drama", "field recording",
        )

    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeTitle(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in noiseWords }
            .joinToString(" ")

    fun normalizeArtist(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    fun kindFor(
        primaryType: String?,
        secondaryTypes: List<String>,
    ): ArtistAlbumGrouping.Kind {
        val secondary = secondaryTypes.map { it.trim().lowercase(Locale.ROOT) }
        return when {
            "live" in secondary -> ArtistAlbumGrouping.Kind.LIVE
            secondary.any { it in otherSecondaryTypes } -> ArtistAlbumGrouping.Kind.COMPILATIONS
            else ->
                when (primaryType?.trim()?.lowercase(Locale.ROOT)) {
                    "single" -> ArtistAlbumGrouping.Kind.SINGLES
                    "ep" -> ArtistAlbumGrouping.Kind.EPS
                    "broadcast", "other" -> ArtistAlbumGrouping.Kind.COMPILATIONS
                    else -> ArtistAlbumGrouping.Kind.ALBUMS
                }
        }
    }

    fun parseBestArtistId(
        body: String,
        artistName: String,
    ): String? {
        val wanted = normalizeArtist(artistName)
        if (wanted.isBlank()) return null
        val artists =
            runCatching { json.parseToJsonElement(body).jsonObject["artists"]?.jsonArray }.getOrNull() ?: return null
        return artists
            .mapNotNull { it as? JsonObject }
            .filter { artist ->
                val score = artist["score"]?.jsonPrimitive?.intOrNull ?: 0
                val name = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                score >= MIN_ARTIST_SCORE && normalizeArtist(name) == wanted
            }.firstOrNull()
            ?.get("id")
            ?.jsonPrimitive
            ?.contentOrNull
    }

    fun parseReleaseGroups(body: String): Page? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val total = root["release-group-count"]?.jsonPrimitive?.intOrNull ?: 0
        val groups = (root["release-groups"] as? JsonArray) ?: return null
        val entries =
            groups.mapNotNull { element ->
                val group = element as? JsonObject ?: return@mapNotNull null
                val title = group["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val normalized = normalizeTitle(title)
                if (normalized.isBlank()) return@mapNotNull null
                val secondary =
                    (group["secondary-types"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        .orEmpty()
                val year =
                    group["first-release-date"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.take(4)
                        ?.toIntOrNull()
                Entry(normalized, year, kindFor(group["primary-type"]?.jsonPrimitive?.contentOrNull, secondary))
            }
        return Page(total, entries)
    }

    fun lookup(
        entries: List<Entry>,
        title: String,
        year: Int?,
    ): Entry? {
        val normalized = normalizeTitle(title)
        if (normalized.isBlank()) return null
        val squashed = normalized.replace(" ", "")
        val matches = entries.filter { it.title.replace(" ", "") == squashed }
        if (matches.isEmpty()) return null
        if (matches.size == 1 || matches.map { it.kind }.distinct().size == 1) {
            return matches.minByOrNull { entry -> yearDistance(entry.year, year) }
        }
        if (year == null) return null
        val ranked = matches.sortedBy { yearDistance(it.year, year) }
        val best = ranked.first()
        val bestDistance = yearDistance(best.year, year)
        if (bestDistance == Int.MAX_VALUE) return null
        return if (ranked.drop(1).any { yearDistance(it.year, year) == bestDistance && it.kind != best.kind }) null else best
    }

    private fun yearDistance(
        entryYear: Int?,
        year: Int?,
    ): Int = if (entryYear == null || year == null) Int.MAX_VALUE else abs(entryYear - year)

    private const val MIN_ARTIST_SCORE = 90
}
