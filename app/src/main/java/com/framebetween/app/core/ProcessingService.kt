package com.framebetween.app.core
import com.framebetween.app.core.L10n

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.framebetween.app.R

/** FFmpeg 长任务期间保持进程前台，避免后台被系统回收。 */
class ProcessingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
            .ifBlank { getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ffmpeg)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(L10n.t("正在处理：") + label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ffmpeg_processing"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.framebetween.app.STOP_PROCESSING"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context, label: String) {
            val intent = Intent(context, ProcessingService::class.java)
                .putExtra(EXTRA_LABEL, label)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProcessingService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
