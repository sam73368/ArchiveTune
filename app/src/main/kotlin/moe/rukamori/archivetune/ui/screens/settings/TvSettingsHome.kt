/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.tv.TvTokens
import moe.rukamori.archivetune.ui.tv.tvFocusBlocked

fun interface TvSettingsGraphBuilder {
    fun build(
        builder: NavGraphBuilder,
        navController: NavHostController,
    )
}

val LocalTvSettingsGraph = compositionLocalOf<TvSettingsGraphBuilder?> { null }

private val TvHiddenSettingKeys = setOf("android_auto", "default_links", "discord_experimental")

private val TvSettingsListWidth = 340.dp
private val TvSettingsStripWidth = 96.dp
private const val TvSettingsBlankRoute = "tv_settings_blank"

private fun tvSettingsRouteBase(route: String?): String? = route?.substringBefore('?')

private sealed interface TvSettingsRow {
    data class Header(
        val title: String,
    ) : TvSettingsRow

    data class Entry(
        val item: SettingsItem,
    ) : TvSettingsRow
}

@Composable
fun TvSettingsHome(
    groups: List<SettingsGroup>,
    routeFor: (parentKey: String, scrollKey: String?) -> String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows =
        remember(groups) {
            buildList {
                groups.forEach { group ->
                    if (group.title.isNotBlank()) add(TvSettingsRow.Header(group.title))
                    group.items.filterNot { it.key in TvHiddenSettingKeys }.forEach { add(TvSettingsRow.Entry(it)) }
                }
            }
        }
    val graph = LocalTvSettingsGraph.current
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var entered by rememberSaveable { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val selected =
        rows.firstNotNullOfOrNull { row -> (row as? TvSettingsRow.Entry)?.item?.takeIf { it.key == selectedKey } }
            ?: rows.firstNotNullOfOrNull { (it as? TvSettingsRow.Entry)?.item }
    val rowRequesters =
        remember(rows) {
            rows
                .filterIsInstance<TvSettingsRow.Entry>()
                .associate { it.item.key to FocusRequester() }
        }
    val contentRequester = remember { FocusRequester() }
    val initialFocusKey = remember { selected?.key }
    val listState = rememberLazyListState()
    var contentHasFocus by remember { mutableStateOf(false) }

    var previewKey by remember { mutableStateOf(selected?.key) }
    LaunchedEffect(selected?.key) {
        if (selected?.key != previewKey) {
            delay(260)
            previewKey = selected?.key
        }
    }
    val previewItem =
        rows.firstNotNullOfOrNull { row -> (row as? TvSettingsRow.Entry)?.item?.takeIf { it.key == previewKey } }
    val previewRoute = previewItem?.let { routeFor(it.key, null) }.takeIf { graph != null }
    val rootBases =
        remember(rows) {
            rows
                .mapNotNull { (it as? TvSettingsRow.Entry)?.item?.key?.let { key -> tvSettingsRouteBase(routeFor(key, null)) } }
                .toSet()
        }

    val previewNav = rememberNavController()
    val previewEntry by previewNav.currentBackStackEntryAsState()

    LaunchedEffect(previewRoute) {
        val route = previewRoute ?: return@LaunchedEffect
        delay(30)
        runCatching {
            previewNav.navigate(route) {
                popUpTo(previewNav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun enter(item: SettingsItem) {
        if (routeFor(item.key, null) == null || graph == null) {
            item.onClick()
            return
        }
        selectedKey = item.key
        previewKey = item.key
        entering = true
    }

    LaunchedEffect(entering) {
        if (!entering) return@LaunchedEffect
        repeat(25) {
            if (contentHasFocus || runCatching { contentRequester.requestFocus() }.getOrDefault(false)) {
                entered = true
                entering = false
                return@LaunchedEffect
            }
            delay(80)
        }
        entering = false
    }

    BackHandler(enabled = entered) {
        if (previewNav.previousBackStackEntry != null) {
            previewNav.popBackStack()
        } else {
            leaving = true
        }
    }

    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        val requester = selected?.key?.let { rowRequesters[it] }
        repeat(15) {
            if (requester != null && runCatching { requester.requestFocus() }.getOrDefault(false)) {
                entered = false
                leaving = false
                return@LaunchedEffect
            }
            delay(60)
        }
        entered = false
        leaving = false
    }

    LaunchedEffect(Unit) {
        if (entered) {
            repeat(24) {
                delay(100)
                if (contentHasFocus) return@LaunchedEffect
                runCatching { contentRequester.requestFocus() }
            }
        } else {
            val rowIndex = rows.indexOfFirst { (it as? TvSettingsRow.Entry)?.item?.key == selected?.key }
            if (rowIndex > 4) runCatching { listState.scrollToItem(rowIndex + 1 - 3) }
            repeat(10) {
                delay(80)
                val requester = selected?.key?.let { rowRequesters[it] }
                if (requester != null && runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(previewEntry?.id) {
        if (!entered) return@LaunchedEffect
        repeat(12) {
            delay(110)
            if (contentHasFocus) return@LaunchedEffect
            runCatching { contentRequester.requestFocus() }
        }
    }

    val listWidth by animateDpAsState(
        targetValue = if (entered) TvSettingsStripWidth else TvSettingsListWidth,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "tvSettingsListWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(200),
        label = "tvSettingsLabelAlpha",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .width(listWidth)
                        .fillMaxHeight()
                        .background(TvTokens.RailBackground)
                        .then(
                            if (entered && !leaving) {
                                Modifier.tvFocusBlocked().focusProperties { canFocus = false }
                            } else {
                                Modifier
                            },
                        ),
                contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "tv_settings_title") {
                    Text(
                        text = stringResource(R.string.settings),
                        style = TvTokens.Display,
                        color = TvTokens.OnSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(start = 12.dp, bottom = 16.dp).alpha(labelAlpha),
                    )
                }
                rows.forEachIndexed { index, row ->
                    when (row) {
                        is TvSettingsRow.Header ->
                            item(key = "h_$index") {
                                Text(
                                    text = row.title.uppercase(),
                                    style = TvTokens.Overline,
                                    color = TvTokens.OnSurfaceFaint,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp).alpha(labelAlpha),
                                )
                            }
                        is TvSettingsRow.Entry -> {
                            val key = row.item.key
                            val requester = rowRequesters.getValue(key)
                            item(key = "e_$key") {
                                TvSettingsCategoryRow(
                                    item = row.item,
                                    selected = key == selected?.key,
                                    labelAlpha = labelAlpha,
                                    modifier = Modifier.focusRequester(requester),
                                    onFocused = {
                                        selectedKey = key
                                        if (entered) entered = false
                                        leaving = false
                                    },
                                    onEnter = { enter(row.item) },
                                )
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(totalWidth - TvSettingsStripWidth)
                            .then(
                                if (entered || entering) {
                                    Modifier
                                } else {
                                    Modifier.tvFocusBlocked().focusProperties { canFocus = false }
                                },
                            ).focusProperties {
                                exit = { direction ->
                                    if (direction == FocusDirection.Left && entered) {
                                        leaving = true
                                        FocusRequester.Cancel
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }.focusRequester(contentRequester)
                            .onFocusChanged { contentHasFocus = it.hasFocus }
                            .focusGroup(),
                ) {
                    if (previewRoute != null && graph != null) {
                        val roots = rootBases
                        NavHost(
                            navController = previewNav,
                            startDestination = TvSettingsBlankRoute,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                val from = tvSettingsRouteBase(initialState.destination.route)
                                val to = tvSettingsRouteBase(targetState.destination.route)
                                if (from == TvSettingsBlankRoute || (from in roots && to in roots)) {
                                    fadeIn(tween(180))
                                } else {
                                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240))
                                }
                            },
                            exitTransition = {
                                val from = tvSettingsRouteBase(initialState.destination.route)
                                val to = tvSettingsRouteBase(targetState.destination.route)
                                if (from == TvSettingsBlankRoute || (from in roots && to in roots)) {
                                    fadeOut(tween(120))
                                } else {
                                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(200))
                                }
                            },
                            popEnterTransition = {
                                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(240))
                            },
                            popExitTransition = {
                                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200))
                            },
                        ) {
                            composable(
                                TvSettingsBlankRoute,
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                            ) {
                                Box(Modifier.fillMaxSize().background(TvTokens.Background))
                            }
                            graph.build(this, previewNav)
                        }
                    } else if (previewItem != null) {
                        TvSettingsDetailPane(
                            item = previewItem,
                            routeFor = routeFor,
                            onNavigate = onNavigate,
                            modifier = Modifier.fillMaxSize().background(TvTokens.Background),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsLeading(
    item: SettingsItem,
    size: androidx.compose.ui.unit.Dp,
) {
    val tint = if (item.accentColor.isSpecified) item.accentColor else MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3.5f))
                .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.iconUrl != null) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
private fun TvSettingsCategoryRow(
    item: SettingsItem,
    selected: Boolean,
    labelAlpha: Float,
    onFocused: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val background =
        when {
            focused -> TvTokens.OnSurface
            selected -> TvTokens.OnSurface.copy(alpha = 0.12f)
            else -> Color.Transparent
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .drawBehind {
                    if (selected && !focused) {
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(0f, size.height * 0.22f),
                            size = Size(3.dp.toPx(), size.height * 0.56f),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }.onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onEnter()
                        true
                    } else {
                        false
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEnter,
                ).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSettingsLeading(item, 30.dp)
        Text(
            text = item.title,
            style = TvTokens.BodyBold,
            color = if (focused) TvTokens.Background else TvTokens.OnSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f).alpha(labelAlpha),
        )
        if (item.showUpdateIndicator || item.badge != null) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .alpha(labelAlpha),
            )
        }
    }
}

@Composable
private fun TvSettingsDetailPane(
    item: SettingsItem,
    routeFor: (parentKey: String, scrollKey: String?) -> String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = remember(item.key) { androidx.compose.foundation.ScrollState(0) }
    Column(
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(start = 40.dp, end = 48.dp, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvSettingsLeading(item, 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = TvTokens.Display, color = TvTokens.OnSurface)
                item.subtitle?.let {
                    Text(text = it, style = TvTokens.Body, color = TvTokens.OnSurfaceMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item.switchControl?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        if (item.children.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tv_settings_contains).uppercase(),
                style = TvTokens.Overline,
                color = TvTokens.OnSurfaceFaint,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            item.children.forEach { child ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TvTokens.Surface)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = child.title,
                        style = TvTokens.Body,
                        color = TvTokens.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tv_settings_hint),
            style = TvTokens.Caption,
            color = TvTokens.OnSurfaceFaint,
        )
    }
}

@Composable
private fun TvSettingsDetailRow(
    title: String,
    accent: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        focused -> TvTokens.OnSurface
                        accent -> TvTokens.OnSurface.copy(alpha = 0.1f)
                        else -> TvTokens.Surface
                    },
                ).onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TvTokens.BodyBold,
            color = if (focused) TvTokens.Background else TvTokens.OnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                painterResource(R.drawable.arrow_forward),
                null,
                tint = if (focused) TvTokens.Background else TvTokens.OnSurfaceMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
