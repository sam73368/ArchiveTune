/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.YTItem
import java.util.concurrent.ConcurrentHashMap

object ArtistSectionRegistry {
    const val LOCAL_BROWSE_ID = "local"
    private const val MAX_ENTRIES = 24

    private val sections = ConcurrentHashMap<String, List<YTItem>>()

    fun keyFor(
        artistId: String,
        title: String,
        firstItemId: String?,
    ): String = "$artistId|$title|${firstItemId.orEmpty()}"

    fun put(
        key: String,
        items: List<YTItem>,
    ) {
        if (sections.size >= MAX_ENTRIES && !sections.containsKey(key)) sections.keys.firstOrNull()?.let(sections::remove)
        sections[key] = items
    }

    fun get(key: String): List<YTItem>? = sections[key]
}
