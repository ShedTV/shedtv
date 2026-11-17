package com.matchday.tv.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps ShedTV running (and the box awake) while something is recording, even after Home.
 * Android requires a visible notification for this.
 */
class RecordingService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!needed()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val titles = RecorderManager.activeTitles()
        val text = when {
            titles.size == 1 -> "Recording: ${titles[0]}"
            titles.size > 1 -> "Recording ${titles.size} programmes"
            else -> com.matchday.tv.download.DownloadManager.activeTitle()?.let { "Downloading: $it" } ?: "Downloads waiting"
        }
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShedTV:recording").apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }
        }
        if (wifi == null) {
            @Suppress("DEPRECATION")
            wifi = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ShedTV:recording").apply { setReferenceCounted(false); acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wake?.release() }
        runCatching { wifi?.release() }
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recordings", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("ShedTV").setContentText(text).setSmallIcon(android.R.drawable.presence_video_online).setOngoing(true).build()
    }

    companion object {
        private const val ID = 4401
        private const val CHANNEL = "recordings"

        private fun needed() = RecorderManager.activeCount() > 0 || com.matchday.tv.download.DownloadManager.busy

        /** Start, refresh or stop the service to match what's recording or downloading. */
        fun update(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
            if (needed()) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
