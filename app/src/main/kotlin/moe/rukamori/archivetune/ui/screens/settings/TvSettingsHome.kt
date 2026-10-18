/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.tv.TvTokens

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
                    group.items.forEach { add(TvSettingsRow.Entry(it)) }
                }
            }
        }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected =
        rows.firstNotNullOfOrNull { row -> (row as? TvSettingsRow.Entry)?.item?.takeIf { it.key == selectedKey } }
            ?: rows.firstNotNullOfOrNull { (it as? TvSettingsRow.Entry)?.item }
    val firstFocus = remember { FocusRequester() }
    val initialFocusKey = remember { selected?.key }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) {
        val rowIndex = rows.indexOfFirst { (it as? TvSettingsRow.Entry)?.item?.key == initialFocusKey }
        if (rowIndex > 4) runCatching { listState.scrollToItem(rowIndex + 1 - 3) }
        repeat(8) {
            delay(80)
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .background(TvTokens.RailBackground),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "tv_settings_title") {
                Text(
                    text = stringResource(R.string.settings),
                    style = TvTokens.Display,
                    color = TvTokens.OnSurface,
                    modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
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
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                    is TvSettingsRow.Entry -> {
                        val isFirst = row.item.key == initialFocusKey
                        item(key = "e_${row.item.key}") {
                            TvSettingsCategoryRow(
                                item = row.item,
                                selected = row.item.key == selected?.key,
                                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                                onFocused = { selectedKey = row.item.key },
                            )
                        }
                    }
                }
            }
        }
        selected?.let { item ->
            TvSettingsDetailPane(
                item = item,
                routeFor = routeFor,
                onNavigate = onNavigate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
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
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background =
        when {
            focused -> TvTokens.OnSurface
            selected -> TvTokens.OnSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = item.onClick,
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.showUpdateIndicator || item.badge != null) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
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
        TvSettingsDetailRow(
            title = stringResource(R.string.tv_settings_open_all),
            accent = true,
            onClick = item.onClick,
        )
        item.children.forEach { child ->
            TvSettingsDetailRow(
                title = child.title,
                accent = false,
                trailing = child.switchControl,
                onClick = {
                    val route = routeFor(item.key, child.scrollKey)
                    if (route != null) onNavigate(route) else item.onClick()
                },
            )
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
