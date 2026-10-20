/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import moe.rukamori.archivetune.BuildConfig
import org.json.JSONObject

enum class BackupPlatform(
    val id: String,
) {
    MOBILE("mobile"),
    TV("tv"),
    ;

    companion object {
        const val INFO_ENTRY_NAME = "archivetune_backup_info.json"
        const val TV_FILE_EXTENSION = "tvbackup"
        const val MOBILE_FILE_EXTENSION = "backup"
        private const val FORMAT_ID = "archivetune-backup"
        private const val FORMAT_VERSION = 1

        fun current(context: Context): BackupPlatform = if (isTelevision(context)) TV else MOBILE

        fun isTelevision(context: Context): Boolean {
            if (BuildConfig.DEVICE == "tv") return true
            val uiModeTelevision =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                    Configuration.UI_MODE_TYPE_TELEVISION
            val packageManager = context.packageManager
            return uiModeTelevision ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        }

        fun encodeInfo(platform: BackupPlatform): String =
            JSONObject()
                .put("format", FORMAT_ID)
                .put("version", FORMAT_VERSION)
                .put("platform", platform.id)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdAt", System.currentTimeMillis())
                .toString()

        fun decodeInfo(payload: String?): BackupPlatform =
            payload
                ?.let { text ->
                    runCatching { JSONObject(text).optString("platform") }.getOrNull()
                }?.let { id -> entries.firstOrNull { it.id == id } }
                ?: MOBILE
    }
}
