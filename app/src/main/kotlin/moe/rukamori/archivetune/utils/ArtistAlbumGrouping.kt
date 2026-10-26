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
    enum class Kind { ALBUMS, LIVE, EPS, SINGLES }

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

    fun kindOf(album: AlbumItem): Kind =
        when {
            album.releaseType == AlbumReleaseType.SINGLE -> Kind.SINGLES
            album.releaseType == AlbumReleaseType.EP -> Kind.EPS
            isLive(album.title) -> Kind.LIVE
            else -> Kind.ALBUMS
        }

    fun newestFirst(albums: List<AlbumItem>): List<AlbumItem> =
        albums
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AlbumItem>> { it.value.year ?: Int.MIN_VALUE }
                    .thenBy { it.index },
            ).map { it.value }

    fun group(albums: List<AlbumItem>): List<Group> =
        albums
            .groupBy(::kindOf)
            .map { (kind, items) -> Group(kind, newestFirst(items)) }
            .sortedBy { it.kind.ordinal }
}
