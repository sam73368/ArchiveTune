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

    /** Observed from composables; written from background threads (snapshot state is thread-safe). */
    val counts = mutableStateMapOf<String, Int>()

    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val semaphore = Semaphore(permits = 3)

    @Volatile
    private var prefs: SharedPreferences? = null

    @Synchronized
    private fun ensureLoaded(context: Context): SharedPreferences {
        prefs?.let { return it }
        val loaded = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loaded.all.forEach { (id, value) -> if (value is Int) counts[id] = value }
        prefs = loaded
        return loaded
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
): String? {
    val context = LocalContext.current
    val showViewCounts by rememberPreference(ShowViewCountsKey, defaultValue = true)
    val lowDataMode by rememberPreference(LowDataModeKey, defaultValue = false)
    val active = enabled && showViewCounts && videoId != null && YouTubeVideoIdRegex.matches(videoId)

    LaunchedEffect(videoId, active, lowDataMode) {
        if (active && !lowDataMode && videoId != null) {
            delay(250)
            ViewCountCache.load(context, videoId)
        }
    }

    val count = if (active && videoId != null) ViewCountCache.counts[videoId] else null
    return count?.let { stringResource(R.string.view_count_value, formatCompactCount(it.toLong())) }
}
