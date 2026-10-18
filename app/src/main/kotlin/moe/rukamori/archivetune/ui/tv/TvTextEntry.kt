/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.tv

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R

@Stable
class TvTextEntryState {
    var target by mutableStateOf<TvTextFieldTarget?>(null)
}

@Composable
fun rememberTvTextEntryState(): TvTextEntryState = remember { TvTextEntryState() }

@Composable
fun Modifier.tvTextEntry(
    state: TvTextEntryState,
    onlyInFocusScope: Boolean = false,
): Modifier {
    if (!LocalTvLayout.current) return this
    val view = LocalView.current
    return onPreviewKeyEvent { event ->
        val confirmKey = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!confirmKey || state.target != null) return@onPreviewKeyEvent false
        if (onlyInFocusScope && !TvFocusFallback.focusInScope) return@onPreviewKeyEvent false
        val field = TvFocusFallback.focusedTextField(view) ?: return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) state.target = field
        true
    }
}

@Composable
fun TvTextEntryDialog(state: TvTextEntryState) {
    val target = state.target ?: return
    val view = LocalView.current
    var text by remember(target) { mutableStateOf(target.text) }
    val firstKey = remember(target) { FocusRequester() }
    LaunchedEffect(target) {
        val imm = view.context.getSystemService(InputMethodManager::class.java)
        repeat(4) {
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            delay(90)
        }
    }
    Dialog(
        onDismissRequest = { state.target = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .height(470.dp)
                    .clip(RoundedCornerShape(20.dp)),
        ) {
            TvSearchKeyboard(
                focusRequester = firstKey,
                query = text,
                onQueryChange = { text = it },
                onSearch = { value ->
                    target.setText(value)
                    state.target = null
                },
                onVoiceSearch = null,
                title = stringResource(R.string.tv_text_entry_title),
                confirmLabel = stringResource(R.string.tv_text_entry_confirm),
                confirmIcon = R.drawable.done,
                masked = target.masked,
                extendedKeys = true,
            )
        }
    }
}

@Composable
fun TvTextEntryHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalTvLayout.current) {
        content()
        return
    }
    val state = rememberTvTextEntryState()
    Box(modifier = modifier.tvTextEntry(state)) {
        content()
    }
    TvTextEntryDialog(state)
}

@Composable
fun TvTextEntryColumnHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalTvLayout.current) {
        content()
        return
    }
    val state = rememberTvTextEntryState()
    Column(modifier = modifier.fillMaxWidth().tvTextEntry(state)) {
        content()
    }
    TvTextEntryDialog(state)
}
