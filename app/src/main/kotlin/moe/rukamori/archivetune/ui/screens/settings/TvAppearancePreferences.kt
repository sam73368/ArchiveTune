/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TvArtworkAccentKey
import moe.rukamori.archivetune.constants.TvTextScaleKey
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.utils.rememberPreference

private val TvTextScales = listOf(0.9f, 1f, 1.15f, 1.3f)

@Composable
fun TvAppearancePreferences() {
    var textScale by rememberPreference(TvTextScaleKey, defaultValue = 1f)
    var artworkAccent by rememberPreference(TvArtworkAccentKey, defaultValue = false)
    val labels =
        listOf(
            stringResource(R.string.tv_text_size_small),
            stringResource(R.string.tv_text_size_normal),
            stringResource(R.string.tv_text_size_large),
            stringResource(R.string.tv_text_size_huge),
        )
    PreferenceGroup(title = stringResource(R.string.tv_settings_group)) {
        item {
            ListPreference(
                title = { Text(stringResource(R.string.tv_text_size)) },
                icon = { Icon(painterResource(R.drawable.text_fields), null) },
                selectedValue = TvTextScales.minBy { kotlin.math.abs(it - textScale) },
                values = TvTextScales,
                valueText = { labels[TvTextScales.indexOf(it)] },
                onValueSelected = { textScale = it },
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.tv_artwork_accent)) },
                description = stringResource(R.string.tv_artwork_accent_desc),
                icon = { Icon(painterResource(R.drawable.palette), null) },
                checked = artworkAccent,
                onCheckedChange = { artworkAccent = it },
            )
        }
    }
}
