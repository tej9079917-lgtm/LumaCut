package com.example.media

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
import com.example.MainActivity

/**
 * Play-Policy compliant `dataSync` Foreground Service keeping LumaCut's sequential background
 * rendering queue alive while displaying live progress and a 1-tap Cancel action.
 */
class BackgroundRenderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        when (intent?.action) {
            ACTION_CANCEL_CURRENT -> {
                RenderQueueManager.getInstance(applicationContext).cancelActiveRender()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "LumaCut Export" }
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0)?.coerceIn(0, 100) ?: 0
                val stage = intent?.getStringExtra(EXTRA_STAGE).orEmpty().ifBlank { "Rendering MP4…" }
                val queued = intent?.getIntExtra(EXTRA_QUEUED_COUNT, 0)?.coerceAtLeast(0) ?: 0
                val notification = buildNotification(title, progress, stage, queued)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    // Gracefully continue background render even if notification permission is restricted
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(
        projectTitle: String,
        progressPct: Int,
        stage: String,
        queuedCount: Int
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, BackgroundRenderService::class.java).apply {
            action = ACTION_CANCEL_CURRENT
        }
        val cancelPending = PendingIntent.getService(
            this,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val queueSuffix = if (queuedCount > 0) " • +$queuedCount queued" else ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Rendering: $projectTitle ($progressPct%)")
            .setContentText("$stage$queueSuffix")
            .setProgress(100, progressPct, progressPct <= 1)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Background Video Rendering",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress for LumaCut background video exports"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "lumacut_render_queue"
        private const val NOTIFICATION_ID = 4401

        const val ACTION_UPDATE = "com.example.action.RENDER_UPDATE"
        const val ACTION_STOP = "com.example.action.RENDER_STOP"
        const val ACTION_CANCEL_CURRENT = "com.example.action.RENDER_CANCEL"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STAGE = "extra_stage"
        const val EXTRA_QUEUED_COUNT = "extra_queued_count"
    }
}
