/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import android.util.Log
import androidx.compose.ui.draw.alpha
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

val TvFocusBlockedKey = SemanticsPropertyKey<Boolean>("TvFocusBlocked")

fun Modifier.tvFocusBlocked(): Modifier = semantics { this[TvFocusBlockedKey] = true }

fun Modifier.tvNonFocusable(): Modifier = focusProperties { canFocus = false }

fun Modifier.tvSkipFocus(): Modifier =
    if (moe.rukamori.archivetune.utils.TvPerformance.isTelevision) focusProperties { canFocus = false } else this

fun Modifier.tvBackButtonFocus(): Modifier =
    if (moe.rukamori.archivetune.utils.TvPerformance.isTelevision) {
        focusProperties { canFocus = false }.alpha(0f)
    } else {
        this
    }

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
    val excluded: Boolean = false,
)

private class FocusSnapshot(
    val focused: FocusCandidate?,
    val focusedInScope: Boolean,
    val focusedInRail: Boolean,
    val scopeCandidates: List<FocusCandidate>,
    val focusedInPlayer: Boolean = false,
    val playerCandidates: List<FocusCandidate> = emptyList(),
    val railCandidates: List<FocusCandidate> = emptyList(),
    val rootHeight: Float = 0f,
)

private const val TopBandFraction = 0.17f

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

    fun focusContentEntry(view: View): Boolean = enterContent(view, false) || enterContent(view, true)

    private fun enterContent(
        view: View,
        includeOffscreen: Boolean,
    ): Boolean {
        val snap = runCatching { snapshot(view, includeOffscreen = includeOffscreen) }.getOrNull() ?: return false
        val entryTop = snap.rootHeight * TopBandFraction
        val usable = snap.scopeCandidates.filter { it.node.config.getOrNull(SemanticsActions.SetText) == null }
        val below = usable.filter { it.rect.top >= entryTop }
        val pool = below.ifEmpty { usable }
        val ordered = pool.sortedWith(compareBy<FocusCandidate>({ (it.rect.top / 24f).toInt() }, { it.rect.left }))
        val result = ordered.take(6).any { requestFocus(it) }
        Log.d("TvFocus", "enterContent offscreen=$includeOffscreen scope=${snap.scopeCandidates.size} usable=${usable.size} result=$result first=${ordered.firstOrNull()?.rect}")
        return result
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
            if (direction == FocusDirection.Left && railFocusCount > 0) {
                val inRail = runCatching { snapshot(view) }.getOrNull()
                val origin = inRail?.focused
                if (inRail != null && inRail.focusedInRail && origin != null) {
                    val sameRow =
                        inRail.railCandidates.filter { c ->
                            c.rect.center.x < origin.rect.center.x &&
                                kotlin.math.abs(c.rect.center.y - origin.rect.center.y) <=
                                maxOf(origin.rect.height, c.rect.height) / 2f
                        }
                    val target = sameRow.maxByOrNull { it.rect.center.x }
                    if (target != null) requestFocus(target)
                    return true
                }
            }
            val verticalMove = direction == FocusDirection.Up || direction == FocusDirection.Down
            val preMove = if (verticalMove) runCatching { snapshot(view) }.getOrNull() else null
            if (focusManager.moveFocus(direction)) {
                if (preMove != null) {
                    val start = preMove.focused
                    val post = runCatching { snapshot(view) }.getOrNull()
                    val landed = post?.focused
                    if (start != null && landed != null && post != null &&
                        !start.excluded && !landed.excluded &&
                        preMove.focusedInRail == post.focusedInRail &&
                        preMove.focusedInPlayer == post.focusedInPlayer &&
                        landed.rect != start.rect &&
                        !isForward(start.rect, landed.rect, direction)
                    ) {
                        Log.d("TvFocus", "outside wrap $direction ${start.rect} -> ${landed.rect} player=${post.focusedInPlayer}")
                        requestFocus(start)
                        return true
                    }
                }
                if (direction == FocusDirection.Right && railFocusCount > 0) {
                    val after = runCatching { snapshot(view) }.getOrNull()
                    if (after != null && after.focused == null && focusContentEntry(view)) return true
                }
                return true
            }
            if (direction != FocusDirection.Right || railFocusCount <= 0) return false
            val fromRail = runCatching { snapshot(view, includeAllContent = true) }.getOrNull() ?: return false
            val origin = fromRail.focused ?: return false
            val target = bestCandidate(origin, fromRail.scopeCandidates, direction)
            if (target != null && requestFocus(target)) return true
            return fromRail.focusedInRail && focusContentEntry(view)
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
        if (before != null && before.focusedInScope && direction == FocusDirection.Down) {
            val origin = before.focused
            if (origin != null && before.rootHeight > 0f && origin.rect.bottom <= before.rootHeight * TopBandFraction) {
                val below = bestCandidate(origin, before.scopeCandidates, direction)
                if (below != null && requestFocus(below)) return true
            }
        }
        val moved = focusManager.moveFocus(direction)
        if (before == null || !before.focusedInScope) return moved
        val start = before.focused ?: return moved
        val after = if (moved) runCatching { snapshot(view) }.getOrNull() else null
        val landed = after?.focused
        val landedScoped = landed != null && after?.focusedInScope == true
        val landedRailed = landed != null && after?.focusedInRail == true
        val landedInContent = landedScoped && !landedRailed
        val sameSpot = landed != null && landed.rect == start.rect
        if (moved && landedInContent && !sameSpot && landed != null &&
            wrappedFar(start.rect, landed.rect, direction, before.rootHeight)
        ) {
            Log.d("TvFocus", "content wrap $direction ${start.rect} -> ${landed.rect}")
            requestFocus(start)
            return true
        }
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
        val origin = before.focused
        Log.d("TvFocus", "pane $direction origin=${origin?.rect} rail=${before.focusedInRail} player=${before.focusedInPlayer} scope=${before.focusedInScope}")
        if (origin == null) return null
        if (before.focusedInRail) {
            if (direction != FocusDirection.Right || before.playerCandidates.isEmpty()) return null
            val railNeighbour = bestCandidate(origin, before.railCandidates, direction)
            if (railNeighbour != null) return if (requestFocus(railNeighbour)) true else null
            val target = nearestInRow(origin, before.playerCandidates, direction)
            Log.d("TvFocus", "rail->player from ${origin.rect} candidates=${before.playerCandidates.size} target=${target?.rect}")
            if (target == null) return null
            val ok = requestFocus(target)
            val landedAt = runCatching { snapshot(view) }.getOrNull()?.focused?.rect
            Log.d("TvFocus", "rail->player requestFocus=$ok landed=$landedAt")
            return if (ok) true else null
        }
        if (!before.focusedInPlayer) return null
        val moved = focusManager.moveFocus(direction)
        val after = if (moved) runCatching { snapshot(view) }.getOrNull() else null
        if (moved && after?.focusedInPlayer == true) {
            val landed = after?.focused
            val vertical = direction == FocusDirection.Up || direction == FocusDirection.Down
            if (vertical && landed != null && !origin.excluded && !landed.excluded &&
                !isForward(origin.rect, landed.rect, direction)
            ) {
                Log.d("TvFocus", "player wrap $direction ${origin.rect} -> ${landed.rect}")
                requestFocus(origin)
            }
            return true
        }
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

    private fun isForward(
        from: Rect,
        to: Rect,
        direction: FocusDirection,
    ): Boolean =
        when (direction) {
            FocusDirection.Up -> to.center.y < from.center.y - 1f
            FocusDirection.Down -> to.center.y > from.center.y + 1f
            FocusDirection.Left -> to.center.x < from.center.x - 1f
            else -> to.center.x > from.center.x + 1f
        }

    private fun wrappedFar(
        from: Rect,
        to: Rect,
        direction: FocusDirection,
        rootHeight: Float,
    ): Boolean {
        val limit = rootHeight * 0.4f
        return when (direction) {
            FocusDirection.Up -> to.center.y - from.center.y > limit
            FocusDirection.Down -> from.center.y - to.center.y > limit
            else -> false
        }
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
        includeOffscreen: Boolean = false,
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
            if (node.config.getOrNull(TvFocusBlockedKey) == true) return
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
                    val candidate = FocusCandidate(node, rect, excluded)
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
            if (!includeOffscreen && node !== rootNode && !nodeBounds.overlaps(rootBounds)) return
            node.children.forEach { walk(it, scoped, railed, played, excluded) }
        }
        walk(rootNode, false, false, false, false)
        return FocusSnapshot(focused, focusedInScope, focusedInRail, candidates, focusedInPlayer, playerCandidates, railCandidates, rootBounds.height)
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
