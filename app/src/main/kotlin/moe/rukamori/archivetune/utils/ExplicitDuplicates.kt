/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.audiosource.TrackMatching
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import kotlin.math.abs

/**
 * YouTube Music lists the clean and the explicit edition of a song (or album) side by side. The
 * explicit edition is the reference one, so a clean edition that has an explicit twin in the same
 * list is dropped. Nothing is dropped when "Hide explicit" already removed the explicit editions
 * (the clean ones are then the only ones left and have no twin).
 */
object ExplicitDuplicates {
    private val EDITION_MARKERS =
        listOf("remaster", "live", "remix", "acoustic", "demo", "instrumental", "radio edit", "slowed", "sped up")

    private const val DURATION_TOLERANCE_SEC = 4

    fun <T : YTItem> List<T>.dropCleanTwins(): List<T> {
        if (size < 2 || none { it.explicit }) return this

        val explicitByKey = HashMap<String, MutableList<YTItem>>()
        for (item in this) {
            if (!item.explicit) continue
            val key = twinKey(item) ?: continue
            explicitByKey.getOrPut(key) { mutableListOf() }.add(item)
        }
        if (explicitByKey.isEmpty()) return this

        return filterNot { item ->
            if (item.explicit) return@filterNot false
            val twins = twinKey(item)?.let(explicitByKey::get) ?: return@filterNot false
            twins.any { twin -> sameLength(item, twin) }
        }
    }

    private fun twinKey(item: YTItem): String? =
        when (item) {
            is SongItem -> {
                val artist = TrackMatching.normalize(item.artists.firstOrNull()?.name)
                "song|${TrackMatching.normalizeTitle(item.title)}|$artist|${markers(item.title)}"
            }

            is AlbumItem -> {
                val artist = TrackMatching.normalize(item.artists?.firstOrNull()?.name)
                "album|${TrackMatching.normalizeTitle(item.title)}|$artist|${markers(item.title)}"
            }

            else -> null
        }?.takeIf { key -> key.split('|').getOrNull(1).orEmpty().isNotBlank() }

    private fun markers(title: String): String {
        val normalized = TrackMatching.normalize(title)
        return EDITION_MARKERS.filter { " $normalized ".contains(" $it ") }.joinToString(",")
    }

    private fun sameLength(
        a: YTItem,
        b: YTItem,
    ): Boolean {
        val first = (a as? SongItem)?.duration ?: return true
        val second = (b as? SongItem)?.duration ?: return true
        return abs(first - second) <= DURATION_TOLERANCE_SEC
    }
}
