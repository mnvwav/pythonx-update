package com.mnvstudio.x11

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.screens.AppsScreen
import com.mnvstudio.x11.screens.ConsoleScreen
import com.mnvstudio.x11.screens.DiagnosticsScreen
import com.mnvstudio.x11.screens.DisplayScreen
import com.mnvstudio.x11.screens.GuideScreen
import com.mnvstudio.x11.screens.HomeScreen
import com.mnvstudio.x11.screens.Onboarding
import com.mnvstudio.x11.screens.SettingsScreen
import com.mnvstudio.x11.ui.AppTheme
import com.mnvstudio.x11.ui.Haptics

class MainActivity : ComponentActivity() {
    /** Screen to open from a notification, shortcut or Termux. */
    private var pendingRoute by mutableStateOf<String?>(null)
    private lateinit var prefs: Prefs

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
        when (intent.action) {
            // `mnvx11` from Termux: start on the requested display, then show it or step aside.
            TermuxCommand.ACTION_START -> {
                val d = intent.getIntExtra(TermuxCommand.EXTRA_DISPLAY, prefs.display).coerceIn(0, 9)
                if (XHolder.server == null) prefs.display = d
                XHolder.ensureStarted(this)
                if (intent.getBooleanExtra(TermuxCommand.EXTRA_OPEN, false)) pendingRoute = "display" else moveTaskToBack(true)
            }
            // Home-screen shortcut for a Linux app.
            Launcher.ACTION_LAUNCH -> {
                Linux.check(this)
                val app = Catalog.byId(intent.getStringExtra(Launcher.EXTRA_APP) ?: "")
                pendingRoute = if (app != null && Linux.state == Linux.State.READY && Launcher.open(this, app)) "display" else "apps"
            }
            // Files shared from other apps go to /root/Shared in Linux.
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> importShared(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun importShared(intent: Intent) {
        Linux.check(this)
        if (Linux.state != Linux.State.READY) { Toast.makeText(this, "Set up Linux first to receive files", Toast.LENGTH_LONG).show(); pendingRoute = "apps"; return }
        val uris: List<Uri> = if (intent.action == Intent.ACTION_SEND) listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        Thread {
            val saved = uris.mapNotNull { runCatching { Linux.importFile(this, it) }.onFailure { e -> AppLog.e("Import failed", e) }.getOrNull() }
            if (uris.isEmpty() && text != null) runCatching { java.io.File(Linux.rootfs(this), "root/Shared/shared.txt").appendText(text + "\n") }
            runOnUiThread {
                val msg = when {
                    saved.size == 1 -> "Saved to ${saved[0]}"
                    saved.isNotEmpty() -> "Saved ${saved.size} files to /root/Shared"
                    uris.isEmpty() && text != null -> "Text added to /root/Shared/shared.txt"
                    else -> "Couldn't save the file"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // Android only lets apps read the clipboard while focused, so offer it to Linux apps whenever we gain focus.
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { if (prefs.clipboardSync) XHolder.pushClipboard(this) }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.clipboardSync) XHolder.pushClipboard(this)
    }

    override fun onResume() {
        super.onResume()
        getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener(clipListener)
    }

    override fun onPause() {
        getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(clipListener)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (savedInstanceState == null) handleIntent(intent)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        setContent {
            Haptics.enabled = prefs.haptics
            AppTheme(dynamic = prefs.dynamicColor, highContrast = prefs.highContrast) {
                // Routes: home, display, apps, console, diagnostics, settings, guide.
                val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf("home") }
                var onboarding by remember { mutableStateOf(!prefs.onboarded) }
                val go: (String) -> Unit = { if (stack.last() != it) stack += it }
                val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                LaunchedEffect(pendingRoute, onboarding) { pendingRoute?.let { r -> if (!onboarding) { pendingRoute = null; go(r) } } }
                BackHandler(enabled = stack.size > 1 && !onboarding) { back() }

                if (onboarding) {
                    Onboarding(prefs) { onboarding = false; XHolder.ensureStarted(this) }
                } else {
                    AnimatedContent(stack.last(), transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "route") { route ->
                        when (route) {
                            "display" -> DisplayScreen(prefs, back, onApps = { go("apps") })
                            "apps" -> AppsScreen(prefs, go, back)
                            "console" -> ConsoleScreen(prefs, go, back)
                            "diagnostics" -> DiagnosticsScreen(prefs, back)
                            "settings" -> SettingsScreen(prefs, onReplayOnboarding = { stack.clear(); stack += "home"; onboarding = true }, onBack = back)
                            "guide" -> GuideScreen(prefs, back)
                            else -> HomeScreen(prefs, go)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
