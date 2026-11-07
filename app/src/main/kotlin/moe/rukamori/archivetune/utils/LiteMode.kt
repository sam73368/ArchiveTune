/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.AlbumCanvasEnabledKey
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.BackAnimationStyle
import moe.rukamori.archivetune.constants.BackAnimationStyleKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.EnableVideoPlaybackKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HideScrollbarKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.KaraokeModeKey
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.constants.LiquidGlassNavBarEnabledKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.MaxImageCacheSizeKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PreloadSongsCountKey
import moe.rukamori.archivetune.constants.ProceduralArtworkEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey
import moe.rukamori.archivetune.constants.ShowViewCountsKey
import moe.rukamori.archivetune.constants.SmartShuffleKey
import moe.rukamori.archivetune.constants.SpotifyCanvasKey
import moe.rukamori.archivetune.constants.SukisuLiquidGlassNavBarKey

object LiteMode {
    val isLite: Boolean = BuildConfig.DEVICE == "lite"

    val hardwareBitmapsAllowed: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isLite

    const val PRIMARY_MIN_BUFFER_MS = 15_000
    const val PRIMARY_MAX_BUFFER_MS = 40_000

    private val defaultsAppliedKey = booleanPreferencesKey("liteDefaultsApplied")

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
            Pair(CrossfadeEnabledKey, false),
            Pair(AutomixEnabledKey, false),
            Pair(EnableVideoPlaybackKey, false),
            Pair(HideVideoKey, true),
            Pair(EnableDiscordRPCKey, false),
        )

    fun isForcedKey(key: Preferences.Key<*>): Boolean = isLite && key in forcedValues

    @Suppress("UNCHECKED_CAST")
    fun <T> forced(key: Preferences.Key<T>): T? {
        if (!isLite) return null
        return forcedValues[key] as T?
    }

    fun firstRunDefaults(): Map<Preferences.Key<*>, Any> =
        mapOf<Preferences.Key<*>, Any>(
            Pair(PlayerDesignStyleKey, PlayerDesignStyle.V4.name),
            Pair(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT.name),
            Pair(BackAnimationStyleKey, BackAnimationStyle.DEFAULT.name),
            Pair(DisableAnimationsKey, true),
            Pair(DynamicThemeKey, false),
            Pair(HideScrollbarKey, true),
            Pair(ForceHighRefreshRateKey, false),
            Pair(ShowViewCountsKey, false),
            Pair(SmartShuffleKey, false),
            Pair(KaraokeModeKey, false),
            Pair(PreloadSongsCountKey, 1),
            Pair(QueueLyricsPreloadCountKey, 0),
            Pair(MaxSongCacheSizeKey, SONG_CACHE_MB),
            Pair(MaxImageCacheSizeKey, IMAGE_CACHE_MB),
        )

    @Suppress("UNCHECKED_CAST")
    suspend fun applyFirstRunDefaults(dataStore: DataStore<Preferences>) {
        if (!isLite) return
        dataStore.edit { prefs ->
            if (prefs[defaultsAppliedKey] == true) return@edit
            firstRunDefaults().forEach { (key, value) ->
                prefs[key as Preferences.Key<Any>] = value
            }
            prefs[defaultsAppliedKey] = true
        }
    }

    private const val SONG_CACHE_MB = 256
    private const val IMAGE_CACHE_MB = 64
}
