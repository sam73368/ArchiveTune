/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.ShowViewCountsKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.utils.formatCompactCount

/**
 * YouTube view counts shown next to song rows. Counts are fetched lazily, only for rows that are
 * actually on screen, with a small concurrency limit, and kept on disk so scrolling back (or the
 * next launch) does not hit the network again.
 */
object ViewCountCache {
    private const val PREFS_NAME = "view_counts"
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L
    private const val MAX_PERSISTED = 4000
    private const val GROUP_PREFIX = "g|"
    private const val LOOKUP_PREFIX = "gl|"

    /** Observed from composables; written from background threads (snapshot state is thread-safe). */
    val counts = mutableStateMapOf<String, Int>()

    val groupCounts = mutableStateMapOf<String, Int>()

    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val groupFailedAt = ConcurrentHashMap<String, Long>()
    private val groupInFlight = ConcurrentHashMap.newKeySet<String>()
    private val groupLookedUp = ConcurrentHashMap.newKeySet<String>()
    private val semaphore = Semaphore(permits = 6)

    @Volatile
    private var prefs: SharedPreferences? = null

    @Synchronized
    private fun ensureLoaded(context: Context): SharedPreferences {
        prefs?.let { return it }
        val loaded = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loaded.all.forEach { (id, value) ->
            if (value !is Int) return@forEach
            when {
                id.startsWith(GROUP_PREFIX) -> groupCounts[id.removePrefix(GROUP_PREFIX)] = value
                id.startsWith(LOOKUP_PREFIX) -> groupLookedUp.add(id.removePrefix(LOOKUP_PREFIX))
                else -> counts[id] = value
            }
        }
        prefs = loaded
        return loaded
    }

    fun raiseGroup(
        key: String,
        value: Long,
    ) {
        val clamped = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (clamped <= (groupCounts[key] ?: -1)) return
        groupCounts[key] = clamped
        val store = prefs ?: return
        if (store.all.size < MAX_PERSISTED) store.edit().putInt(GROUP_PREFIX + key, clamped).apply()
    }

    suspend fun loadGroup(
        context: Context,
        group: SongVersions.ViewGroup,
    ) {
        val store = ensureLoaded(context)
        if (groupLookedUp.contains(group.key)) return
        val failed = groupFailedAt[group.key]
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) return
        if (!groupInFlight.add(group.key)) return
        try {
            semaphore.withPermit {
                val items =
                    YouTube
                        .search(group.query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                if (items == null) {
                    groupFailedAt[group.key] = System.currentTimeMillis()
                    return@withPermit
                }
                items
                    .filterIsInstance<SongItem>()
                    .filter { SongVersions.groupKeyOf(it) == group.key }
                    .mapNotNull { it.viewCount }
                    .maxOrNull()
                    ?.let { raiseGroup(group.key, it) }
                groupLookedUp.add(group.key)
                if (store.all.size < MAX_PERSISTED) store.edit().putInt(LOOKUP_PREFIX + group.key, 1).apply()
            }
        } finally {
            groupInFlight.remove(group.key)
        }
    }

    /** True while another caller (typically a visible row) is already fetching [videoId]. */
    fun isInFlight(videoId: String): Boolean = inFlight.contains(videoId)

    /** Like [load] but ignores an earlier failure (used when a ranking needs the missing counts). */
    suspend fun retry(
        context: Context,
        videoId: String,
    ) {
        failedAt.remove(videoId)
        load(context, videoId)
    }

    /** Fetches the count for [videoId] unless it is already known, in flight, or failed recently. */
    suspend fun load(
        context: Context,
        videoId: String,
    ) {
        val store = ensureLoaded(context)
        if (counts.containsKey(videoId)) return
        val failed = failedAt[videoId]
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) return
        if (!inFlight.add(videoId)) return
        try {
            semaphore.withPermit {
                val count = YouTube.getViewCount(videoId).getOrNull()
                if (count != null && count >= 0) {
                    counts[videoId] = count
                    if (store.all.size < MAX_PERSISTED) store.edit().putInt(videoId, count).apply()
                } else {
                    failedAt[videoId] = System.currentTimeMillis()
                }
            }
        } finally {
            inFlight.remove(videoId)
        }
    }
}

private val YouTubeVideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * The compact "1.2M views" label for [videoId], or null while unknown / disabled. The request only
 * runs while the row is composed, so fast scrolling cancels the rows that were skipped.
 */
@Composable
fun rememberViewCountText(
    videoId: String?,
    enabled: Boolean = true,
    group: SongVersions.ViewGroup? = null,
    knownCount: Long? = null,
): String? {
    val context = LocalContext.current
    val showViewCounts by rememberPreference(ShowViewCountsKey, defaultValue = true)
    val lowDataMode by rememberPreference(LowDataModeKey, defaultValue = true)
    val active = enabled && showViewCounts && videoId != null && YouTubeVideoIdRegex.matches(videoId)

    LaunchedEffect(videoId, active, lowDataMode) {
        if (active && !lowDataMode && videoId != null) {
            delay(250)
            ViewCountCache.load(context, videoId)
        }
    }

    val ownCount = if (active && videoId != null) ViewCountCache.counts[videoId]?.toLong() else null
    val groupActive = group != null && showViewCounts && (active || knownCount != null)
    val ownEffective = ownCount ?: knownCount?.takeIf { showViewCounts }

    LaunchedEffect(group?.key, ownEffective, groupActive) {
        if (groupActive && group != null && ownEffective != null) ViewCountCache.raiseGroup(group.key, ownEffective)
    }
    LaunchedEffect(group?.key, groupActive, lowDataMode) {
        if (groupActive && !lowDataMode && group != null) {
            delay(400)
            ViewCountCache.loadGroup(context, group)
        }
    }

    val groupCount = if (groupActive && group != null) ViewCountCache.groupCounts[group.key]?.toLong() else null
    val best = listOfNotNull(ownEffective, groupCount).maxOrNull()
    return best?.let { stringResource(R.string.view_count_value, formatCompactCount(it)) }
}
