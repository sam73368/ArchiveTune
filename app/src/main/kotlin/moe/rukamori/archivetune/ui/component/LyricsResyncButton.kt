/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/** Where the line being sung is, compared to what the user currently sees. */
enum class LyricsResyncDirection { ABOVE, BELOW, VISIBLE }

/**
 * Where [lineIndex] (an item index of [listState]) is relative to the visible items. Read inside
 * a derived state, so it only changes when the answer changes, not on every scrolled pixel.
 */
@Composable
fun rememberLyricsResyncDirection(
    listState: LazyListState,
    lineIndex: () -> Int,
): State<LyricsResyncDirection> =
    remember(listState) {
        derivedStateOf {
            val index = lineIndex()
            val visible = listState.layoutInfo.visibleItemsInfo
            when {
                index < 0 || visible.isEmpty() -> LyricsResyncDirection.VISIBLE
                index < visible.first().index -> LyricsResyncDirection.ABOVE
                index > visible.last().index -> LyricsResyncDirection.BELOW
                else -> LyricsResyncDirection.VISIBLE
            }
        }
    }

/**
 * "Resync" pill shown while the user browses the lyrics by hand. The arrow points to the line that is
 * being sung when it is off screen.
 */
@Composable
fun LyricsResyncButton(
    visible: Boolean,
    direction: LyricsResyncDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        FilledTonalButton(onClick = onClick) {
            Icon(
                painter =
                    painterResource(
                        when (direction) {
                            LyricsResyncDirection.ABOVE -> R.drawable.arrow_upward
                            LyricsResyncDirection.BELOW -> R.drawable.arrow_downward
                            LyricsResyncDirection.VISIBLE -> R.drawable.sync
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.lyrics_resync),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
