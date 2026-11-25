/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber

object BlockingCallMonitor {
    const val CACHE = "cache"
    const val DATABASE = "database"

    private const val TAG = "PlaybackDiag"
    private const val CACHE_STALL_MS = 3_000L
    private const val CACHE_BYPASS_HOLD_MS = 90_000L
    private const val DUMP_MIN_AGE_MS = 2_000L
    private const val DUMP_FRAMES = 14
    private const val DUMP_INTERVAL_MS = 15_000L

    private class Call(
        val label: String,
        val operation: String,
        val thread: Thread,
        val startedAt: Long,
    )

    private val calls = ConcurrentHashMap<Long, Call>()
    private val ids = AtomicLong()

    @Volatile
    private var cacheBypassUntil = 0L

    @Volatile
    private var lastDumpAt = 0L

    fun <T> track(
        label: String,
        operation: String,
        block: () -> T,
    ): T {
        val id = ids.incrementAndGet()
        calls[id] = Call(label, operation, Thread.currentThread(), SystemClock.elapsedRealtime())
        try {
            return block()
        } finally {
            calls.remove(id)
        }
    }

    private fun oldest(
        label: String,
        now: Long,
        minAgeMs: Long,
    ): Call? =
        calls.values
            .filter { it.label == label && now - it.startedAt >= minAgeMs }
            .minByOrNull { it.startedAt }

    fun cacheBypassActive(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val stuck = oldest(CACHE, now, CACHE_STALL_MS)
        if (stuck != null) {
            if (now >= cacheBypassUntil) {
                Timber.tag(TAG).w(
                    "Cache call %s stuck for %dms on %s; playback bypasses the caches for %ds\n%s",
                    stuck.operation,
                    now - stuck.startedAt,
                    stuck.thread.name,
                    CACHE_BYPASS_HOLD_MS / 1000,
                    stackOf(stuck.thread),
                )
            }
            cacheBypassUntil = now + CACHE_BYPASS_HOLD_MS
        }
        return now < cacheBypassUntil
    }

    fun dumpPlaybackThreads(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDumpAt < DUMP_INTERVAL_MS) return
        lastDumpAt = now
        val report = StringBuilder("Stall diagnostics (").append(reason).append(')')
        val pending = calls.values.filter { now - it.startedAt >= DUMP_MIN_AGE_MS }.sortedBy { it.startedAt }
        if (pending.isEmpty()) report.append("\nNo cache or database call has been waiting more than 2s")
        pending.forEach { call ->
            report
                .append("\n[")
                .append(call.label)
                .append(' ')
                .append(call.operation)
                .append(" waiting ")
                .append(now - call.startedAt)
                .append("ms on ")
                .append(call.thread.name)
                .append(" state=")
                .append(call.thread.state)
                .append("]\n")
                .append(stackOf(call.thread))
        }
        runCatching {
            Thread
                .getAllStackTraces()
                .filterKeys { it.name.startsWith("ExoPlayer:Loader") || it.name.startsWith("ExoPlayer:Playback") }
                .forEach { (thread, frames) ->
                    report
                        .append("\n[")
                        .append(thread.name)
                        .append(" state=")
                        .append(thread.state)
                        .append("]\n")
                        .append(format(frames))
                }
        }
        Timber.tag(TAG).w(report.toString())
    }

    private fun stackOf(thread: Thread): String = runCatching { format(thread.stackTrace) }.getOrDefault("")

    private fun format(frames: Array<StackTraceElement>): String =
        frames.take(DUMP_FRAMES).joinToString("\n") { "    at $it" }
}
