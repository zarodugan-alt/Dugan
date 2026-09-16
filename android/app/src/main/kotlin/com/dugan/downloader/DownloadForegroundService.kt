package com.dugan.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app process alive while downloads run, with a low-importance
 * progress notification (continued when the app is minimized).
 */
class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createChannel()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading"
                try {
                    startForegroundCompat(
                        buildNotification(count = 1, title = title, stage = null, progress = 0),
                    )
                } catch (e: Exception) {
                    // e.g. ForegroundServiceStartNotAllowedException on
                    // Android 12+ — stop instead of crashing.
                    stopSelf()
                }
            }
            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_COUNT, 1)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val stage = intent.getStringExtra(EXTRA_STAGE)
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(count = count, title = title, stage = stage, progress = progress),
                )
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        count: Int,
        title: String,
        stage: String?,
        progress: Int,
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val text = buildString {
            append(if (count > 1) "$count downloads in progress" else "Downloading")
            if (!stage.isNullOrEmpty()) append(" · $stage")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress of active downloads"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "dugan_downloads"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "app.dugan.action.START"
        private const val ACTION_UPDATE = "app.dugan.action.UPDATE"
        private const val ACTION_STOP = "app.dugan.action.STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_COUNT = "count"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_STAGE = "stage"

        fun start(context: Context, title: String) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(
            context: Context,
            count: Int,
            title: String,
            progress: Int,
            stage: String?,
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_COUNT, count)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_STAGE, stage)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // App in background — notification update is best-effort.
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }
}
