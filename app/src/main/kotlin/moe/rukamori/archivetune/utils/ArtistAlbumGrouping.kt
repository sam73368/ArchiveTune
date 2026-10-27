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

object ArtistAlbumGrouping {
    enum class Kind { ALBUMS, LIVE, COMPILATIONS, EPS, SINGLES }

    data class Override(
        val kind: Kind,
        val year: Int?,
    )

    data class Group(
        val kind: Kind,
        val albums: List<AlbumItem>,
    )

    private val liveWords =
        setOf("live", "concert", "unplugged", "tour", "en vivo", "ao vivo", "en direct", "in concert", "symphony orchestra")

    fun isLive(title: String): Boolean {
        val text =
            " " +
                Normalizer
                    .normalize(title, Normalizer.Form.NFD)
                    .replace(Regex("""\p{Mn}+"""), "")
                    .lowercase(Locale.ROOT)
                    .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                    .trim() +
                " "
        return liveWords.any { " $it " in text }
    }

    fun kindOf(
        album: AlbumItem,
        overrides: Map<String, Override> = emptyMap(),
    ): Kind =
        overrides[album.id]?.kind ?: when {
            album.releaseType == AlbumReleaseType.SINGLE -> Kind.SINGLES
            album.releaseType == AlbumReleaseType.EP -> Kind.EPS
            isLive(album.title) -> Kind.LIVE
            else -> Kind.ALBUMS
        }

    fun newestFirst(
        albums: List<AlbumItem>,
        overrides: Map<String, Override> = emptyMap(),
    ): List<AlbumItem> =
        albums
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AlbumItem>> {
                    overrides[it.value.id]?.year ?: it.value.year ?: Int.MIN_VALUE
                }.thenBy { it.index },
            ).map { it.value }
            .map { album ->
                val year = overrides[album.id]?.year
                if (year != null && year != album.year) album.copy(year = year) else album
            }

    fun musicBrainzOverrides(
        albums: List<AlbumItem>,
        entries: List<AlbumKindCatalog.Entry>,
    ): Map<String, Override> {
        val overrides = HashMap<String, Override>()
        for (album in albums) {
            val entry = AlbumKindCatalog.lookup(entries, album.title, album.year) ?: continue
            overrides[album.id] = Override(entry.kind, entry.year ?: album.year)
        }
        return overrides
    }

    fun withEditionBases(
        musicBrainz: Map<String, Override>,
        baseOf: Map<String, AlbumItem>,
    ): Map<String, Override> {
        val overrides = HashMap(musicBrainz)
        baseOf.forEach { (displayId, base) ->
            val baseOverride = musicBrainz[base.id]
            val kind = musicBrainz[displayId]?.kind ?: baseOverride?.kind ?: kindOf(base)
            overrides[displayId] = Override(kind, baseOverride?.year ?: base.year)
        }
        return overrides
    }

    fun group(
        albums: List<AlbumItem>,
        overrides: Map<String, Override> = emptyMap(),
    ): List<Group> =
        albums
            .groupBy { kindOf(it, overrides) }
            .map { (kind, items) -> Group(kind, newestFirst(items, overrides)) }
            .sortedBy { it.kind.ordinal }
}
