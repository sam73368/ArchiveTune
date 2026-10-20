/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import moe.rukamori.archivetune.R

fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_LONG).show()
        false
    } catch (error: SecurityException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_LONG).show()
        false
    }

fun <I> ActivityResultLauncher<I>.launchSafely(
    context: Context,
    input: I,
): Boolean =
    try {
        launch(input)
        true
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_LONG).show()
        false
    } catch (error: SecurityException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_LONG).show()
        false
    }

@Composable
fun rememberSafeUriHandler(): UriHandler {
    val context = LocalContext.current
    return remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val parsed = runCatching { uri.toUri() }.getOrNull() ?: return
                context.startActivitySafely(Intent(Intent.ACTION_VIEW, parsed))
            }
        }
    }
}
