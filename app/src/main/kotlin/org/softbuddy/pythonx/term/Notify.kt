package org.softbuddy.pythonx.term

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.softbuddy.pythonx.MainActivity
import org.softbuddy.pythonx.R

/**
 * App notifications. "terminal" is the quiet ongoing one for background sessions; "events" says when something
 * you started finishes while PythonX is in the background (a program exits, a package install ends).
 */
object Notify {
    const val TERMINAL = "terminal"
    private const val EVENTS = "events"

    /** True while an activity is on screen; event notifications are skipped then, the UI already shows the result. */
    @Volatile var foreground = false

    fun channels(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(TERMINAL, "Running programs", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while a Python program or shell keeps running" },
            NotificationChannel(EVENTS, "Finished tasks", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A program exited or a package finished installing while PythonX was in the background" },
        ))
    }

    fun builder(ctx: Context, channel: String): Notification.Builder =
        Notification.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_pythonx).setColor(0xFF5FD4A0.toInt())

    /** Opens the app on [route] (e.g. "term:/path" or "run:/path"). */
    fun open(ctx: Context, route: String?, request: Int = 0): PendingIntent = PendingIntent.getActivity(ctx, request,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** A one-off "it finished" notification, only when the app isn't visible. */
    fun event(ctx: Context, id: Int, title: String, text: String, route: String?, ok: Boolean = true) {
        if (foreground || !allowed(ctx)) return
        channels(ctx)
        val n = builder(ctx, EVENTS)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, route, id))
            .setAutoCancel(true)
            .apply { if (!ok) setColor(0xFFFF8A8A.toInt()) }
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
