/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.tv.LocalTvLayout

@Composable
internal fun ReorderHandle(
    index: Int,
    count: Int,
    tint: Color,
    dragModifier: Modifier,
    onMove: (from: Int, to: Int) -> Unit,
) {
    if (!LocalTvLayout.current) {
        Icon(
            painter = painterResource(R.drawable.drag_handle),
            contentDescription = null,
            tint = tint,
            modifier = dragModifier,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { if (index > 0) onMove(index, index - 1) },
            modifier = Modifier.size(40.dp).alpha(if (index > 0) 1f else 0.3f),
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_less),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(
            onClick = { if (index < count - 1) onMove(index, index + 1) },
            modifier = Modifier.size(40.dp).alpha(if (index < count - 1) 1f else 0.3f),
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
