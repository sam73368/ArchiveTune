/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.LastAutoInstallVersionKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.isCanaryBuild
import timber.log.Timber

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return Result.success()
        }

        return try {
            val dataStore = applicationContext.dataStore

            val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: isCanaryBuild }.first()
            if (!isEnabled) return Result.success()

            val updateChannel =
                dataStore.data
                    .map { UpdateChannel.fromStoredName(it[UpdateChannelKey], defaultUpdateChannel) }
                    .first()

            when (updateChannel) {
                UpdateChannel.CANARY -> {
                    Updater.getLatestCanaryVersionName().onSuccess { latestVersion ->
                        if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                            val autoInstalled = tryAutoInstall(latestVersion)
                            if (!autoInstalled) {
                                UpdateNotificationManager.notifyIfNewVersion(
                                    applicationContext,
                                    latestVersion,
                                    updateChannel,
                                )
                            }
                        }
                    }
                }

                UpdateChannel.STABLE -> {
                    Updater.getLatestVersionName().onSuccess { latestVersion ->
                        if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                            UpdateNotificationManager.notifyIfNewVersion(applicationContext, latestVersion)
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Downloads and hands the newest canary build to the PackageInstaller, once per version.
     * Returns true when an install was started (the system then shows its own prompt when it
     * needs confirmation); false means the caller should fall back to a plain notification.
     */
    private suspend fun tryAutoInstall(latestVersion: String): Boolean {
        val ctx = applicationContext
        val dataStore = ctx.dataStore
        val alreadyTried = dataStore.data.map { it[LastAutoInstallVersionKey] }.first()
        if (alreadyTried == latestVersion) return false
        if (!ctx.packageManager.canRequestPackageInstalls()) return false

        val url = Updater.getLatestCanaryDownloadUrl()
        if (url.isBlank()) return false
        return try {
            dataStore.edit { it[LastAutoInstallVersionKey] = latestVersion }
            val apk = AppUpdateInstaller.download(ctx, url) { }.getOrThrow()
            withContext(Dispatchers.Main) { AppUpdateInstaller.installApk(ctx, apk) }
            true
        } catch (e: Exception) {
            Timber.w(e, "Automatic update download failed")
            false
        }
    }
}
