package com.mnvstudio.x11

import android.app.Application
import android.os.Build

/** Sets up logging and records crashes, so the next launch can show what went wrong. */
class X11App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("X11 started on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull()})")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            AppLog.e("Crash in ${thread.name}\n" + e.stackTraceToString().lines().take(25).joinToString("\n"))
            previous?.uncaughtException(thread, e)
        }
    }
}
