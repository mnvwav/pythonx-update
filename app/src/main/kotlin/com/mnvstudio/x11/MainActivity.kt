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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.screens.LinuxScreen
import com.mnvstudio.x11.screens.SessionsScreen
import com.mnvstudio.x11.screens.ToolScreen
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.LocalIsLight
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.OnAccent
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SplashScreen
import com.mnvstudio.x11.ui.AppTheme
import com.mnvstudio.x11.ui.Haptics

class MainActivity : ComponentActivity() {
    /** Screen to open from a notification or shortcut. */
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home button or app switch while the display is open: keep it floating (Settings → Display).
        if (Pip.displayOpen && prefs.autoPip && XHolder.clients > 0) Pip.enter(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Pip.active = isInPictureInPictureMode
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
        prefs = Prefs.of(this)
        if (savedInstanceState == null) handleIntent(intent)
        // Session restoration: the display was running when the app was last closed (or killed), so bring it back.
        if (savedInstanceState == null && prefs.onboarded && prefs.restoreSession && prefs.wasRunning && XHolder.server == null) {
            val s = Sessions.active(this)
            if (s != null) Sessions.launch(this, s) else XHolder.ensureStarted(this)
            AppLog.i("Restored the previous session")
        }
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(dark, dark)
        setContent {
            Haptics.enabled = prefs.haptics
            AppTheme(prefs.themeMode, prefs.accent, prefs.dynamicColor, prefs.highContrast) {
                // Status bar icons follow the theme.
                val light = LocalIsLight.current
                LaunchedEffect(light) {
                    val style = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) else dark
                    enableEdgeToEdge(style, style)
                }
                // App text size on top of the system font scale.
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * prefs.textScale)) {
                    var splash by rememberSaveable { mutableStateOf(savedInstanceState == null && prefs.showSplash) }
                    Box(Modifier.fillMaxSize()) {
                        AppContent()
                        if (splash) SplashScreen { splash = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        // Navigation: one of the tabs, with full-screen routes pushed on top (display, terminal, files…).
        var tab by rememberSaveable { mutableStateOf("home") }
        val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf<String>() }
        var onboarding by remember { mutableStateOf(!prefs.onboarded) }
        val go: (String) -> Unit = { r -> if (r in TABS) { stack.clear(); tab = r } else if (stack.lastOrNull() != r) stack += r }
        val back: () -> Unit = {
            when {
                stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
                tab != "home" -> tab = "home"
                else -> finish()
            }
        }
        LaunchedEffect(pendingRoute, onboarding) { pendingRoute?.let { r -> if (!onboarding) { pendingRoute = null; go(r) } } }
        BackHandler(enabled = !onboarding && (stack.isNotEmpty() || tab != "home")) { back() }

        if (onboarding) {
            Onboarding(prefs) { onboarding = false; XHolder.ensureStarted(this) }
            return
        }
        val pushed = stack.lastOrNull()
        // Depth-aware transitions: pushing slides the new screen in from the right while the old one sinks back;
        // going back reverses it. Switching tabs is a soft crossfade with a hint of scale.
        val depth = stack.size
        AnimatedContent(Pair(pushed ?: "tab:$tab", depth), transitionSpec = {
            val forward = targetState.second > initialState.second
            val backward = targetState.second < initialState.second
            val ease = com.mnvstudio.x11.ui.EaseOutExpo
            when {
                forward -> (slideInHorizontally(tween(420, easing = ease)) { it / 3 } + fadeIn(tween(260)) + scaleIn(tween(420, easing = ease), 0.96f)) togetherWith
                    (fadeOut(tween(220)) + scaleOut(tween(420, easing = ease), 0.94f))
                backward -> (fadeIn(tween(260)) + scaleIn(tween(420, easing = ease), 0.94f)) togetherWith
                    (slideOutHorizontally(tween(360, easing = ease)) { it / 3 } + fadeOut(tween(220)) + scaleOut(tween(360, easing = ease), 0.96f))
                else -> (fadeIn(tween(240)) + scaleIn(tween(320, easing = ease), 0.985f)) togetherWith fadeOut(tween(160))
            }
        }, contentKey = { it.first }, label = "route") { (route, _) ->
            if (route.startsWith("tab:")) Tabs(route.removePrefix("tab:"), go) { t -> tab = t }
            else Box(Modifier.fillMaxSize().background(Page).then(if (route == "display") Modifier else Modifier.systemBarsPadding())) {
                Pushed(route, go, back)
            }
        }
    }

    @Composable
    private fun Tabs(tab: String, go: (String) -> Unit, select: (String) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Page)) {
            val rail = prefs.navLayout == "rail" || (prefs.navLayout == "auto" && maxWidth >= 600.dp)
            val content: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    when (tab) {
                        "apps" -> AppsScreen(prefs, go)
                        "linux" -> LinuxScreen(prefs, go)
                        "sessions" -> SessionsScreen(prefs, go)
                        "settings" -> SettingsScreen(prefs, go, onReplayOnboarding = { prefs.onboarded = false; recreate() })
                        else -> HomeScreen(prefs, go)
                    }
                }
            }
            if (rail) Row(Modifier.fillMaxSize().systemBarsPadding()) {
                NavigationRail(containerColor = Card, header = { com.mnvstudio.x11.ui.LiveMark(44.dp, Modifier.padding(vertical = 12.dp)) }) {
                    Spacer(Modifier.weight(1f))
                    NAV.forEach { (id, label, icon) ->
                        NavigationRailItem(tab == id, { select(id) }, icon = { Icon(icon, null) }, label = { Text(label) },
                            colors = NavigationRailItemDefaults.colors(selectedIconColor = OnAccent, indicatorColor = Accent, unselectedIconColor = Label, unselectedTextColor = Label, selectedTextColor = Ink))
                    }
                    Spacer(Modifier.weight(1f))
                }
                content(Modifier.weight(1f).fillMaxSize())
            } else Box(Modifier.fillMaxSize()) {
                // Room for the floating bar so the last items stay reachable; a short fade blends content into it.
                content(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 64.dp))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Page.copy(alpha = 0.92f)))))
                com.mnvstudio.x11.ui.FloatingNavBar(NAV, tab, select, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }

    @Composable
    private fun Pushed(route: String, go: (String) -> Unit, back: () -> Unit) {
        val arg = route.substringAfter('?', "")
        when (route.substringBefore('?')) {
            "display" -> DisplayScreen(prefs, back, onApps = { go("apps") })
            "console" -> ConsoleScreen(prefs, go, back)
            "diagnostics" -> DiagnosticsScreen(prefs, back)
            "help" -> GuideScreen(prefs, back)
            else -> ToolScreen(route.substringBefore('?'), arg, prefs, go, back)
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        val TABS = setOf("home", "apps", "linux", "sessions", "settings")
        private val NAV = listOf(
            Triple("home", "Home", Icons.Filled.Home), Triple("apps", "Apps", Icons.Filled.Apps), Triple("linux", "Linux", Icons.Filled.Terminal),
            Triple("sessions", "Sessions", Icons.Filled.Layers), Triple("settings", "Settings", Icons.Filled.Settings),
        )
    }
}
