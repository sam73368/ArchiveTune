/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Android TV / Google TV / Fire TV: driven with a remote control (D-pad), no touch screen. */
fun Context.isTelevision(): Boolean {
    val isTelevisionUiMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return isTelevisionUiMode ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}
