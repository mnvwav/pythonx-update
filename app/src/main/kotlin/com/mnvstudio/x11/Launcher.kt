package com.mnvstudio.x11

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.mnvstudio.x11.linux.LinuxApp
import com.mnvstudio.x11.linux.Linux

/** Starting apps, home-screen shortcuts and the battery exemption: things several screens and intents share. */
object Launcher {
    const val ACTION_LAUNCH = "com.mnvstudio.x11.LAUNCH"
    const val EXTRA_APP = "app"

    /** Starts the server (immediately, so the app can connect) and the Linux app on it. */
    fun open(context: Context, app: LinuxApp, log: (String) -> Unit = {}): Boolean {
        if (!XHolder.ensureStarted(context)) { log(XHolder.error ?: "The X server couldn't start"); return false }
        Linux.launch(context, app, XHolder.display, log)
        Prefs.of(context).addRecent(app.id)
        updateShortcuts(context)
        return true
    }

    /** Asks the launcher to pin a shortcut that opens [app] directly. */
    fun pinShortcut(context: Context, app: LinuxApp) {
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            Toast.makeText(context, "Your launcher doesn't support shortcuts", Toast.LENGTH_SHORT).show(); return
        }
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION_LAUNCH).putExtra(EXTRA_APP, app.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val info = ShortcutInfo.Builder(context, "app-${app.id}")
            .setShortLabel(app.name).setLongLabel("${app.name} (X11)")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent).build()
        sm.requestPinShortcut(info, null)
    }

    /**
     * Long-press the X11 icon on the home screen: the most recent Linux apps appear as shortcuts that open straight
     * into their window, like real Android apps.
     */
    fun updateShortcuts(context: Context) = runCatching {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return@runCatching
        val prefs = Prefs.of(context)
        val apps = prefs.recents.split(',').mapNotNull { com.mnvstudio.x11.linux.Catalog.byId(it) }.filter { Linux.isInstalled(context, it) }
            .take(maxOf(1, sm.maxShortcutCountPerActivity - 1).coerceAtMost(4))
        sm.dynamicShortcuts = apps.mapIndexed { i, app ->
            ShortcutInfo.Builder(context, "recent-${app.id}").setShortLabel(app.name).setLongLabel("${app.name} (Linux)")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher)).setRank(i)
                .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_LAUNCH).putExtra(EXTRA_APP, app.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
        }
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Opens the system prompt that lets the server keep running when the phone saves battery. */
    @android.annotation.SuppressLint("BatteryLife")
    fun requestBatteryExemption(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
