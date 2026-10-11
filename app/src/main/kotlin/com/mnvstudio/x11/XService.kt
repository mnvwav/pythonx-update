package com.mnvstudio.x11

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Keeps the X server alive in the background, with an ongoing notification that shows the display and a Stop action. */
class XService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            XHolder.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        channel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0), type)
        if (!XHolder.start(this, Prefs(this))) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        XHolder.onClientsChanged = { n -> getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(n)) }
        return START_STICKY
    }

    override fun onDestroy() {
        XHolder.onClientsChanged = null
        XHolder.stop()
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "X server", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the X server is running"
                setShowBadge(false)
            })
        }
    }

    private fun notification(clients: Int) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("X server running on :${XHolder.display}")
        .setContentText(if (clients == 1) "1 app connected" else "$clients apps connected")
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(route("display", 0))
        .addAction(0, "Open", route("display", 2))
        .addAction(0, "Settings", route("settings", 3))
        .addAction(0, "Stop", PendingIntent.getService(this, 1,
            Intent(this, XService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun route(route: String, code: Int) = PendingIntent.getActivity(this, code,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        private const val CHANNEL = "server"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.mnvstudio.x11.STOP"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, XService::class.java))
        fun stop(context: Context) { context.startService(Intent(context, XService::class.java).setAction(ACTION_STOP)) }
    }
}
