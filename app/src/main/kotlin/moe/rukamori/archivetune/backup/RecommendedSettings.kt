/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppleMusicExperienceKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.MaxImageCacheSizeKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.ShowCodecOnPlayerKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.utils.dataStore

object RecommendedSettings {
    val summaryResIds: List<Int> =
        listOf(
            R.string.recommended_item_dark_theme,
            R.string.recommended_item_player,
            R.string.recommended_item_refresh_rate,
            R.string.recommended_item_low_data,
            R.string.recommended_item_cache,
            R.string.recommended_item_misc,
        )

    suspend fun apply(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[DarkModeKey] = DarkMode.ON.name
            prefs[PlayerDesignStyleKey] = PlayerDesignStyle.V9.name
            prefs[AppleMusicExperienceKey] = false
            prefs[EnableUpdateNotificationKey] = true
            prefs[LowDataModeKey] = false
            prefs[ForceHighRefreshRateKey] = true
            prefs[SmartTrimmerKey] = true
            prefs[MaxSongCacheSizeKey] = CACHE_SIZE_MB
            prefs[MaxImageCacheSizeKey] = CACHE_SIZE_MB
            prefs[ShowCodecOnPlayerKey] = false
        }
    }

    private const val CACHE_SIZE_MB = 128
}
