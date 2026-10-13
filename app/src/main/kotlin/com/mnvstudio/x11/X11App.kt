package com.mnvstudio.x11

import android.app.Application
import android.os.Build

/** Sets up logging and records crashes, so the next launch can show what went wrong. */
class X11App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        runCatching { com.mnvstudio.x11.bridge.Bridge.ensure(this) }
        // The paper-and-ink redesign: move everyone to the red accent once (they can pick another in Settings).
        Prefs.of(this).let { p -> if (!p.brandGold) { p.accent = "Red"; p.brandGold = true } }
        AppLog.i("X11 started on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull()})")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            AppLog.e("Crash in ${thread.name}\n" + e.stackTraceToString().lines().take(25).joinToString("\n"))
            // Kept for the next launch, which offers to copy or share it.
            runCatching { CrashReport.save(this, thread, e) }
            previous?.uncaughtException(thread, e)
        }
    }
}
