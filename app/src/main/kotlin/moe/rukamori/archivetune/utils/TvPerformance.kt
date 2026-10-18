/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.constants.AlbumCanvasEnabledKey
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.constants.LiquidGlassNavBarEnabledKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.ProceduralArtworkEnabledKey
import moe.rukamori.archivetune.constants.SpotifyCanvasKey
import moe.rukamori.archivetune.constants.SukisuLiquidGlassNavBarKey
import moe.rukamori.archivetune.constants.TvPerformanceModeKey

object TvPerformance {
    @Volatile
    var isTelevision: Boolean = false

    private val forcedValues: Map<Preferences.Key<*>, Any> =
        mapOf<Preferences.Key<*>, Any>(
            Pair(DisableBlurKey, true),
            Pair(LiquidGlassEnabledKey, false),
            Pair(LiquidGlassNavBarEnabledKey, false),
            Pair(SukisuLiquidGlassNavBarKey, false),
            Pair(AlbumCanvasEnabledKey, false),
            Pair(ArchiveTuneCanvasKey, false),
            Pair(SpotifyCanvasKey, false),
            Pair(ProceduralArtworkEnabledKey, false),
            Pair(LyricsLineBlurKey, false),
        )

    fun isActive(preferences: Preferences?): Boolean {
        if (!isTelevision) return false
        return preferences?.get(TvPerformanceModeKey) ?: true
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> forced(
        key: Preferences.Key<T>,
        preferences: Preferences?,
    ): T? {
        if (key !in forcedValues || !isActive(preferences)) return null
        return forcedValues[key] as T?
    }
}
