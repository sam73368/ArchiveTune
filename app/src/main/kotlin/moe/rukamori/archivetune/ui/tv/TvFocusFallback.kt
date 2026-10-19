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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

@Composable
fun Modifier.tvFocusScope(): Modifier {
    val holder = remember { booleanArrayOf(false) }
    DisposableEffect(holder) {
        onDispose {
            if (holder[0]) {
                holder[0] = false
                TvFocusFallback.scopeFocusCount--
            }
        }
    }
    return semantics { this[TvFocusScopeKey] = true }
        .onFocusChanged { state ->
            if (state.hasFocus != holder[0]) {
                holder[0] = state.hasFocus
                TvFocusFallback.scopeFocusCount += if (state.hasFocus) 1 else -1
            }
        }
}

val TvFocusRailKey = SemanticsPropertyKey<Boolean>("TvFocusRail")

@Composable
fun Modifier.tvFocusRail(): Modifier {
    val holder = remember { booleanArrayOf(false) }
    DisposableEffect(holder) {
        onDispose {
            if (holder[0]) {
                holder[0] = false
                TvFocusFallback.railFocusCount--
            }
        }
    }
    return semantics { this[TvFocusRailKey] = true }
        .onFocusChanged { state ->
            if (state.hasFocus != holder[0]) {
                holder[0] = state.hasFocus
                TvFocusFallback.railFocusCount += if (state.hasFocus) 1 else -1
            }
        }
}

val TvPlayerPaneKey = SemanticsPropertyKey<Boolean>("TvPlayerPane")

@Composable
fun Modifier.tvPlayerPane(): Modifier {
    DisposableEffect(Unit) {
        TvFocusFallback.playerPaneCount++
        onDispose { TvFocusFallback.playerPaneCount-- }
    }
    return semantics { this[TvPlayerPaneKey] = true }
}

val TvFocusExcludedKey = SemanticsPropertyKey<Boolean>("TvFocusExcluded")

fun Modifier.tvFocusExcluded(): Modifier = semantics { this[TvFocusExcludedKey] = true }

fun Modifier.tvNonFocusable(): Modifier = focusProperties { canFocus = false }

fun Modifier.tvBackButtonFocus(): Modifier =
    if (moe.rukamori.archivetune.utils.TvPerformance.isTelevision) focusProperties { canFocus = false } else this

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

class TvTextFieldTarget(
    val text: String,
    val masked: Boolean,
    val setText: (String) -> Boolean,
    val submit: (() -> Unit)?,
)

private class FocusCandidate(
    val node: SemanticsNode,
    val rect: Rect,
)

private class FocusSnapshot(
    val focused: FocusCandidate?,
    val focusedInScope: Boolean,
    val focusedInRail: Boolean,
    val scopeCandidates: List<FocusCandidate>,
    val focusedInPlayer: Boolean = false,
    val playerCandidates: List<FocusCandidate> = emptyList(),
    val railCandidates: List<FocusCandidate> = emptyList(),
)

object TvFocusFallback {
    var scopeFocusCount: Int = 0

    var railFocusCount: Int = 0

    var playerPaneCount: Int = 0

    val focusInScope: Boolean
        get() = scopeFocusCount > 0

    fun handleKey(
        event: KeyEvent,
        view: View,
        focusManager: FocusManager,
    ): Boolean {
        return moveWithFallback(event, view, focusManager)
    }

    fun focusedIsTextField(view: View): Boolean =
        runCatching { snapshot(view) }
            .getOrNull()
            ?.focused
            ?.node
            ?.config
            ?.getOrNull(SemanticsActions.SetText) != null

    fun focusedTextField(view: View): TvTextFieldTarget? {
        val node = runCatching { snapshot(view) }.getOrNull()?.focused?.node ?: return null
        val config = node.config
        val setText = config.getOrNull(SemanticsActions.SetText)?.action ?: return null
        return TvTextFieldTarget(
            text = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
            masked = config.getOrNull(SemanticsProperties.Password) != null,
            setText = { value -> runCatching { setText(androidx.compose.ui.text.AnnotatedString(value)) }.getOrDefault(false) },
            submit = config.getOrNull(SemanticsActions.OnImeAction)?.action?.let { action -> { runCatching { action() }; Unit } },
        )
    }

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
        movePlayerPane(direction, view, focusManager)?.let { return it }
        if (!focusInScope) {
            if (focusManager.moveFocus(direction)) return true
            if (direction != FocusDirection.Right || railFocusCount <= 0) return false
            val fromRail = runCatching { snapshot(view, includeAllContent = true) }.getOrNull() ?: return false
            val origin = fromRail.focused ?: return false
            val target = bestCandidate(origin, fromRail.scopeCandidates, direction) ?: return false
            return requestFocus(target)
        }
        val vertical = direction == FocusDirection.Up || direction == FocusDirection.Down
        if (!vertical) {
            if (focusManager.moveFocus(direction)) return true
            val stuck = runCatching { snapshot(view) }.getOrNull() ?: return false
            if (!stuck.focusedInScope) return false
            val from = stuck.focused ?: return false
            val alternative = bestCandidate(from, stuck.scopeCandidates, direction) ?: return false
            return requestFocus(alternative)
        }
        val before = runCatching { snapshot(view) }.getOrNull()
        val moved = focusManager.moveFocus(direction)
        if (before == null || !before.focusedInScope) return moved
        val start = before.focused ?: return moved
        val after = if (moved) runCatching { snapshot(view) }.getOrNull() else null
        val landed = after?.focused
        val landedScoped = landed != null && after?.focusedInScope == true
        val landedRailed = landed != null && after?.focusedInRail == true
        val landedInContent = landedScoped && !landedRailed
        val sameSpot = landed != null && landed.rect == start.rect
        if (moved && landedInContent && !sameSpot) return true
        if (moved && landed != null && !landedScoped && !landedRailed) return true
        val pool = after?.scopeCandidates ?: before.scopeCandidates
        val alternative = bestCandidate(start, pool, direction)
        if (alternative != null && requestFocus(alternative)) return true
        if (moved && !landedInContent && railFocusCount > 0) {
            requestFocus(start)
            return true
        }
        return moved
    }

    private fun movePlayerPane(
        direction: FocusDirection,
        view: View,
        focusManager: FocusManager,
    ): Boolean? {
        if (playerPaneCount <= 0) return null
        val before = runCatching { snapshot(view) }.getOrNull() ?: return null
        val origin = before.focused ?: return null
        if (before.focusedInRail) {
            if (direction != FocusDirection.Right || before.playerCandidates.isEmpty()) return null
            val target = nearestInRow(origin, before.playerCandidates, direction) ?: return null
            return if (requestFocus(target)) true else null
        }
        if (!before.focusedInPlayer) return null
        val moved = focusManager.moveFocus(direction)
        val after = if (moved) runCatching { snapshot(view) }.getOrNull() else null
        if (moved && after?.focusedInPlayer == true) return true
        if (direction == FocusDirection.Left) {
            val rail = nearestInRow(origin, before.railCandidates, direction)
            if (rail != null && requestFocus(rail)) return true
            if (moved && after?.focusedInRail == true) return true
        }
        if (!moved) return false
        val pool = after?.playerCandidates ?: before.playerCandidates
        val alternative = bestCandidate(origin, pool, direction)
        if (alternative == null || !requestFocus(alternative)) requestFocus(origin)
        return true
    }

    private fun nearestInRow(
        origin: FocusCandidate,
        candidates: List<FocusCandidate>,
        direction: FocusDirection,
    ): FocusCandidate? {
        if (candidates.isEmpty()) return null
        val center = origin.rect.center
        val sideways =
            candidates.filter { c ->
                if (direction == FocusDirection.Right) c.rect.center.x > center.x else c.rect.center.x < center.x
            }.ifEmpty { candidates }
        val sameRow =
            sideways.filter { c ->
                kotlin.math.abs(c.rect.center.y - center.y) <= maxOf(origin.rect.height, c.rect.height) / 2f
            }
        if (sameRow.isNotEmpty()) {
            return sameRow.minByOrNull { c -> kotlin.math.abs(c.rect.center.x - center.x) }
        }
        return sideways.minByOrNull { c ->
            val dx = c.rect.center.x - center.x
            val dy = c.rect.center.y - center.y
            dx * dx + dy * dy
        }
    }

    private fun requestFocus(candidate: FocusCandidate): Boolean =
        runCatching {
            candidate.node.config
                .getOrNull(SemanticsActions.RequestFocus)
                ?.action
                ?.invoke() == true
        }.getOrDefault(false)

    private fun snapshot(
        view: View,
        includeAllContent: Boolean = false,
    ): FocusSnapshot? {
        val owner = (view as? ViewRootForTest)?.semanticsOwner ?: return null
        val rootNode = owner.unmergedRootSemanticsNode
        val rootBounds = rootNode.boundsInRoot
        var focused: FocusCandidate? = null
        var focusedInScope = false
        var focusedInRail = false
        var focusedInPlayer = false
        val candidates = ArrayList<FocusCandidate>()
        val playerCandidates = ArrayList<FocusCandidate>()
        val railCandidates = ArrayList<FocusCandidate>()

        fun walk(
            node: SemanticsNode,
            inScope: Boolean,
            inRail: Boolean,
            inPlayer: Boolean,
            inExcluded: Boolean,
        ) {
            val scoped = inScope || node.config.getOrNull(TvFocusScopeKey) == true
            val railed = inRail || node.config.getOrNull(TvFocusRailKey) == true
            val played = inPlayer || node.config.getOrNull(TvPlayerPaneKey) == true
            val excluded = inExcluded || node.config.getOrNull(TvFocusExcludedKey) == true
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
                        focusedInPlayer = played
                    } else {
                        if (scoped || (includeAllContent && !railed)) candidates += candidate
                        if (played && !excluded) playerCandidates += candidate
                        if (railed) railCandidates += candidate
                    }
                }
            }
            val nodeBounds = node.boundsInRoot
            if (node !== rootNode && !nodeBounds.overlaps(rootBounds)) return
            node.children.forEach { walk(it, scoped, railed, played, excluded) }
        }
        walk(rootNode, false, false, false, false)
        return FocusSnapshot(focused, focusedInScope, focusedInRail, candidates, focusedInPlayer, playerCandidates, railCandidates)
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
