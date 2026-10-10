/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import moe.rukamori.archivetune.R
import timber.log.Timber

/**
 * Receives the outcome of an in-app update install ([AppUpdateInstaller.installApk]).
 *
 * When Android needs the user's confirmation (first install through this app, or a device that
 * always asks) the confirmation screen is opened, and a notification is posted as well because
 * Android 10+ silently refuses to start activities from the background.
 */
class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    } ?: return
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmIntent) }
                showInstallReadyNotification(context, confirmIntent)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.tag("UpdateInstall").w("Update install did not complete: status=%d message=%s", status, message)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun showInstallReadyNotification(
        context: Context,
        confirmIntent: Intent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "moe.rukamori.archivetune.action.UPDATE_INSTALL_RESULT"
        private const val CHANNEL_ID = "update_install_channel"
        private const val NOTIFICATION_ID = 9998
    }
}
