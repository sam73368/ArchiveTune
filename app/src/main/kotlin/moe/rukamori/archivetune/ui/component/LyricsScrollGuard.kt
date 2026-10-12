/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps a lyrics list where the user left it.
 *
 * The karaoke view of the Accompanist library scrolls its list to the sung line on every new line,
 * with no way to turn that off. Its scrolls run at the default priority, so holding the list with a
 * user-input priority scroll makes them fail (the library ignores the failure). A real touch from
 * the user has the same priority and still takes over at once.
 *
 * - [flingAndHold] runs the user's fling itself (at user priority, so the library cannot cut it short)
 *   and keeps holding the list afterwards;
 * - [holdIfIdle] starts holding when nothing is scrolling;
 * - the hold ends by itself when [holdWanted] becomes false, or with [release].
 */
@Stable
class LyricsScrollGuard(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val holdWanted: State<Boolean>,
) {
    var flingBehavior: FlingBehavior? = null

    private var holdJob: Job? = null

    val isHolding: Boolean
        get() = holdJob?.isActive == true

    /** @return true when the fling was taken over (the caller then consumes it). */
    fun flingAndHold(velocity: Float): Boolean {
        val behavior = flingBehavior ?: return false
        holdJob?.cancel()
        holdJob =
            scope.launch {
                try {
                    listState.scroll(MutatePriority.UserInput) {
                        with(behavior) { performFling(velocity) }
                        awaitHoldEnd()
                    }
                } catch (_: CancellationException) {
                    // A new touch took the list over, or the hold was released.
                }
            }
        return true
    }

    fun holdIfIdle() {
        if (isHolding || listState.isScrollInProgress || !holdWanted.value) return
        holdJob =
            scope.launch {
                try {
                    listState.scroll(MutatePriority.UserInput) { awaitHoldEnd() }
                } catch (_: CancellationException) {
                }
            }
    }

    suspend fun awaitHold() {
        holdJob?.join()
    }

    suspend fun release() {
        holdJob?.cancelAndJoin()
        holdJob = null
    }

    private suspend fun awaitHoldEnd() {
        snapshotFlow { holdWanted.value }.first { !it }
    }
}
