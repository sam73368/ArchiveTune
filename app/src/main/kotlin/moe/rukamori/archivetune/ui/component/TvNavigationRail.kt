/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.tv.TvTokens
import moe.rukamori.archivetune.ui.tv.tvFocusRail
import androidx.compose.runtime.getValue

@Composable
fun TvNavigationRail(
    items: List<Screens>,
    selectedItemRoute: String?,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    settingsSelected: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    nowPlaying: (@Composable (FocusRequester?) -> Unit)? = null,
    onItemClick: (Screens) -> Unit,
) {
    LaunchedEffect(firstItemFocusRequester) {
        firstItemFocusRequester?.requestFocus()
    }
    val accent = MaterialTheme.colorScheme.primary
    val rightToContent =
        if (contentFocusRequester != null) {
            Modifier.focusProperties { right = contentFocusRequester }
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .tvFocusRail()
                .fillMaxHeight()
                .width(TvTokens.RailWidth)
                .background(TvTokens.RailBackground)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f,
                    )
                }.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = TvTokens.Background,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, screen ->
            val selected = selectedItemRoute == screen.route
            TvNavigationRailItem(
                iconRes = if (selected) screen.iconIdActive else screen.iconIdInactive,
                label = stringResource(screen.titleId),
                selected = selected,
                modifier =
                    Modifier
                        .then(
                            if (firstItemFocusRequester != null &&
                                (selected || (index == 0 && items.none { it.route == selectedItemRoute }))
                            ) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        ).then(rightToContent),
                onClick = { onItemClick(screen) },
            )
        }
        Spacer(Modifier.weight(1f))
        if (nowPlaying != null) {
            nowPlaying(contentFocusRequester)
        }
        if (onSettingsClick != null) {
            TvNavigationRailItem(
                iconRes = R.drawable.settings,
                label = stringResource(R.string.settings),
                selected = settingsSelected,
                modifier = rightToContent,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun TvNavigationRailItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "tvRailScale",
    )
    val containerColor by animateColorAsState(
        targetValue =
            when {
                focused -> TvTokens.OnSurface
                selected -> accent.copy(alpha = 0.16f)
                else -> Color.Transparent
            },
        label = "tvRailBg",
    )
    val contentColor by animateColorAsState(
        targetValue =
            when {
                focused -> TvTokens.Background
                selected -> accent
                else -> TvTokens.OnSurfaceMuted
            },
        label = "tvRailFg",
    )

    Box(
        modifier =
            modifier
                .scale(scale)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key(KEYCODE_DPAD_CENTER.toLong()),
                        Key(KEYCODE_ENTER.toLong()),
                        Key(KEYCODE_NUMPAD_ENTER.toLong()),
                        -> {
                            onClick()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}
