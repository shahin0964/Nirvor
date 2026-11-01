package com.nirvor.app.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nirvor.app.MainActivity
import com.nirvor.app.R
import com.nirvor.app.reminders.Notifications

object DownloadNotifications {
    const val CH_PROGRESS = "downloads_progress"
    const val CH_DONE = "downloads_done"
    const val ONGOING_ID = 7001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "ডাউনলোড চলছে", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "ডাউনলোড শেষ", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Opens the app on the Download Hub. */
    fun hubIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        11,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ongoing(context: Context, title: String, text: String, percent: Int): Notification =
        NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, percent.coerceIn(0, 100), percent < 0)
            .setContentIntent(hubIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun done(context: Context, id: Long, title: String, uri: Uri?, mime: String?) {
        if (!Notifications.canPost(context)) return
        val tap = if (uri != null) {
            PendingIntent.getActivity(
                context,
                (id % 100000).toInt() + 100,
                Storage.openIntent(uri, mime),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            hubIntent(context)
        }
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("ডাউনলোড শেষ")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((id % 100000).toInt() + 10_000, n)
        } catch (e: SecurityException) {
            // notifications were turned off
        }
    }

    fun failed(context: Context, id: Long, title: String, reason: String) {
        if (!Notifications.canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("ডাউনলোড হয়নি")
            .setContentText(title + " · " + reason)
            .setAutoCancel(true)
            .setContentIntent(hubIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify((id % 100000).toInt() + 10_000, n)
        } catch (e: SecurityException) {
            // notifications were turned off
        }
    }
}
