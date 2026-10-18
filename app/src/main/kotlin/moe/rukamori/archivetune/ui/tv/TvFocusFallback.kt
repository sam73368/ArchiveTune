/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import android.view.View
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.utils.isTelevision

val TvFocusScopeKey = SemanticsPropertyKey<Boolean>("TvFocusScope")

fun Modifier.tvFocusScope(): Modifier = semantics { this[TvFocusScopeKey] = true }

val TvFocusRailKey = SemanticsPropertyKey<Boolean>("TvFocusRail")

fun Modifier.tvFocusRail(): Modifier = semantics { this[TvFocusRailKey] = true }

fun Modifier.tvNonFocusable(): Modifier = focusProperties { canFocus = false }

@Composable
fun Modifier.tvMenuFocusTrap(): Modifier {
    val context = LocalContext.current
    val tv = remember(context) { context.isTelevision() }
    if (!tv) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        repeat(10) {
            delay(70)
            val moved = runCatching { requester.requestFocus() }.getOrNull() as Any?
            if (moved == true) return@LaunchedEffect
        }
    }
    return this
        .focusRequester(requester)
        .focusProperties { exit = { FocusRequester.Cancel } }
        .focusGroup()
}

private class FocusCandidate(
    val node: SemanticsNode,
    val rect: Rect,
)

private class FocusSnapshot(
    val focused: FocusCandidate?,
    val focusedInScope: Boolean,
    val focusedInRail: Boolean,
    val scopeCandidates: List<FocusCandidate>,
)

object TvFocusFallback {
    fun handleKey(
        event: KeyEvent,
        view: View,
        focusManager: FocusManager,
        onTextFieldFocused: () -> Unit = {},
    ): Boolean {
        val handled = moveWithFallback(event, view, focusManager)
        if (handled) {
            val landed = runCatching { snapshot(view) }.getOrNull()?.focused
            if (landed != null && landed.node.config.getOrNull(SemanticsActions.SetText) != null) {
                onTextFieldFocused()
            }
        }
        return handled
    }

    fun focusedIsTextField(view: View): Boolean =
        runCatching { snapshot(view) }
            .getOrNull()
            ?.focused
            ?.node
            ?.config
            ?.getOrNull(SemanticsActions.SetText) != null

    private fun moveWithFallback(
        event: KeyEvent,
        view: View,
        focusManager: FocusManager,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val direction =
            when (event.key) {
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                else -> return false
            }
        val before = runCatching { snapshot(view) }.getOrNull()
        val moved = focusManager.moveFocus(direction)
        if (before == null || !before.focusedInScope) return moved
        val start = before.focused ?: return moved
        val vertical = direction == FocusDirection.Up || direction == FocusDirection.Down
        if (moved) {
            if (!vertical) return true
            val after = runCatching { snapshot(view) }.getOrNull() ?: return true
            if (after.focused == null || after.focusedInScope || !after.focusedInRail) return true
            val alternative = bestCandidate(start, after.scopeCandidates, direction)
            requestFocus(alternative ?: start)
            return true
        }
        val alternative = bestCandidate(start, before.scopeCandidates, direction) ?: return false
        return requestFocus(alternative)
    }

    private fun requestFocus(candidate: FocusCandidate): Boolean =
        runCatching {
            candidate.node.config
                .getOrNull(SemanticsActions.RequestFocus)
                ?.action
                ?.invoke() == true
        }.getOrDefault(false)

    private fun snapshot(view: View): FocusSnapshot? {
        val owner = (view as? ViewRootForTest)?.semanticsOwner ?: return null
        var focused: FocusCandidate? = null
        var focusedInScope = false
        var focusedInRail = false
        val candidates = ArrayList<FocusCandidate>()

        fun walk(
            node: SemanticsNode,
            inScope: Boolean,
            inRail: Boolean,
        ) {
            val scoped = inScope || node.config.getOrNull(TvFocusScopeKey) == true
            val railed = inRail || node.config.getOrNull(TvFocusRailKey) == true
            val config = node.config
            if (config.getOrNull(SemanticsActions.RequestFocus) != null &&
                config.getOrNull(SemanticsProperties.Disabled) == null
            ) {
                val rect = node.boundsInRoot
                if (rect.width > 1f && rect.height > 1f) {
                    val candidate = FocusCandidate(node, rect)
                    if (config.getOrNull(SemanticsProperties.Focused) == true) {
                        focused = candidate
                        focusedInScope = scoped
                        focusedInRail = railed
                    } else if (scoped) {
                        candidates += candidate
                    }
                }
            }
            node.children.forEach { walk(it, scoped, railed) }
        }
        walk(owner.unmergedRootSemanticsNode, false, false)
        return FocusSnapshot(focused, focusedInScope, focusedInRail, candidates)
    }

    private fun bestCandidate(
        from: FocusCandidate,
        candidates: List<FocusCandidate>,
        direction: FocusDirection,
    ): FocusCandidate? {
        val src = from.rect
        val tolerance = 2f
        val inDirection =
            candidates.filter { c ->
                val r = c.rect
                if (r.contains(src.center) || src.contains(r.center)) return@filter false
                when (direction) {
                    FocusDirection.Down -> r.top >= src.bottom - tolerance
                    FocusDirection.Up -> r.bottom <= src.top + tolerance
                    FocusDirection.Right -> r.left >= src.right - tolerance
                    FocusDirection.Left -> r.right <= src.left + tolerance
                    else -> false
                }
            }
        if (inDirection.isEmpty()) return null
        val vertical = direction == FocusDirection.Up || direction == FocusDirection.Down
        val inBeam =
            inDirection.filter { c ->
                val r = c.rect
                if (vertical) r.right > src.left && r.left < src.right else r.bottom > src.top && r.top < src.bottom
            }
        val pool = inBeam.ifEmpty { inDirection }
        return pool.minByOrNull { c ->
            val r = c.rect
            val major =
                when (direction) {
                    FocusDirection.Down -> r.top - src.bottom
                    FocusDirection.Up -> src.top - r.bottom
                    FocusDirection.Right -> r.left - src.right
                    else -> src.left - r.right
                }.coerceAtLeast(0f)
            val minor =
                if (vertical) {
                    kotlin.math.abs(r.center.x - src.center.x)
                } else {
                    kotlin.math.abs(r.center.y - src.center.y)
                }
            13f * major * major + minor * minor
        }
    }
}
