/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarkdownText

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvUpdateDialog(
    versionName: String,
    releaseNotes: String?,
    onInstall: () -> Unit,
    onChangelog: () -> Unit,
    onLater: () -> Unit,
    onNeverShow: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val installFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onLater,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(TvTokens.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(520.dp)
                        .shadow(40.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(TvTokens.Surface)
                        .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.new_update_available),
                            style = TvTokens.Title,
                            color = TvTokens.OnSurface,
                        )
                        Text(
                            text = stringResource(R.string.tv_update_subtitle, versionName),
                            style = TvTokens.Caption,
                            color = TvTokens.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                            .clip(RoundedCornerShape(4.dp)),
                ) {
                    if (!releaseNotes.isNullOrBlank()) {
                        MarkdownText(
                            markdown = releaseNotes,
                            modifier = Modifier.fillMaxWidth(),
                            style = TvTokens.Body,
                            color = TvTokens.OnSurface,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = TvTokens.Body,
                            color = TvTokens.OnSurfaceMuted,
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .focusRestorer(installFocus),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvPillButton(
                        onClick = onInstall,
                        style = TvPillStyle.Primary,
                        modifier = Modifier.focusRequester(installFocus),
                    ) { color ->
                        Text(stringResource(R.string.tv_update_install_now), style = TvTokens.BodyBold, color = color)
                    }
                    TvPillButton(onClick = onChangelog, style = TvPillStyle.Tonal) { color ->
                        Text(stringResource(R.string.tv_update_all_changes), style = TvTokens.BodyBold, color = color)
                    }
                    TvPillButton(onClick = onLater, style = TvPillStyle.Text, horizontalPadding = 16.dp) { color ->
                        Text(stringResource(R.string.tv_update_later), style = TvTokens.BodyBold, color = color)
                    }
                    TvPillButton(onClick = onNeverShow, style = TvPillStyle.Text, horizontalPadding = 16.dp) { color ->
                        Text(stringResource(R.string.update_never_show_again), style = TvTokens.BodyBold, color = color)
                    }
                }
            }

            Text(
                text = stringResource(R.string.tv_update_hint),
                style = TvTokens.Caption,
                color = TvTokens.OnSurfaceFaint,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
            )
        }
    }

    LaunchedEffect(Unit) {
        repeat(5) {
            if (runCatching { installFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
}
