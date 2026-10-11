package com.mnvstudio.x11.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.display.ExtraKeysBar
import com.mnvstudio.x11.display.Screenshot
import com.mnvstudio.x11.display.Sticky
import com.mnvstudio.x11.display.XDisplayView
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.ShapeL

/** Full-screen view of the X display, with a floating menu and an optional extra-keys bar. */
@Composable
fun DisplayScreen(prefs: Prefs, onBack: () -> Unit, onApps: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val server = XHolder.server

    // Immersive full screen, the chosen orientation and (optionally) drawing into the notch while the display is open.
    DisposableEffect(prefs.orientation, prefs.drawInCutout) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        val oldCutout = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (prefs.drawInCutout) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        activity.requestedOrientation = when (prefs.orientation) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = oldCutout }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (server == null) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("The display is off", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Start it to see your apps here.", color = Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            PrimaryButton("Start display", Modifier.widthIn(min = 220.dp), icon = Icons.Filled.PlayArrow) { XHolder.ensureStarted(context) }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Back", Modifier.widthIn(min = 220.dp)) { onBack() }
        }
        return
    }

    val sticky = remember { Sticky() }
    var menu by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<XDisplayView?>(null) }
    var fps by remember { mutableIntStateOf(0) }
    var switcher by remember { mutableStateOf(false) }

    fun showKeyboard() {
        val v = view ?: return
        v.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }

    // Per-app input profiles follow the focused window.
    var appClass by remember { mutableStateOf("") }
    LaunchedEffect(server) {
        while (true) {
            appClass = synchronized(server) { server.wm?.windows()?.firstOrNull { it.focused }?.appClass } ?: ""
            kotlinx.coroutines.delay(1000)
        }
    }
    var mouseLocked by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Follow rotation: when the phone's shape no longer matches the display's, restart it at the new size.
            val viewLandscape = maxWidth > maxHeight
            val serverLandscape = server.width > server.height
            LaunchedEffect(viewLandscape, prefs.followRotation, prefs.orientation) {
                if (prefs.followRotation && prefs.orientation == "auto" && viewLandscape != serverLandscape) {
                    kotlinx.coroutines.delay(700)
                    XHolder.restart(context, "rotated to ${if (viewLandscape) "landscape" else "portrait"}")
                }
            }
            AndroidView(
                factory = { ctx -> XDisplayView(ctx).also { view = it } },
                modifier = Modifier.fillMaxSize(),
                update = { v ->
                    if (v.server !== server) v.server = server
                    v.directTouch = prefs.touchMode == Prefs.MODE_DIRECT
                    v.pointerSpeed = prefs.pointerSpeed
                    if (v.smooth != prefs.smoothScaling) v.smooth = prefs.smoothScaling
                    if (v.fitMode != prefs.fitMode) v.fitMode = prefs.fitMode
                    v.maxFps = prefs.maxFps
                    if (v.cursorScale != prefs.cursorScale) v.cursorScale = prefs.cursorScale
                    v.keepScreenOn = prefs.keepScreenOn
                    v.stickyMods = { sticky.active() }
                    v.onStickyUsed = { sticky.used() }
                    v.input = com.mnvstudio.x11.display.InputConfig.from(prefs, appClass)
                    v.appShortcuts = prefs.appShortcuts
                    v.onAction = { a ->
                        when (a) {
                            com.mnvstudio.x11.display.Action.KEYBOARD -> showKeyboard()
                            com.mnvstudio.x11.display.Action.SWITCHER -> switcher = true
                            com.mnvstudio.x11.display.Action.MENU -> menu = true
                            com.mnvstudio.x11.display.Action.LEAVE -> onBack()
                            else -> {}
                        }
                    }
                    v.onFps = if (prefs.perfOverlay) { f -> fps = f } else null
                },
                onRelease = { it.server = null },
            )
            // An empty screen otherwise looks broken: say so and offer the apps.
            if (XHolder.clients == 0) {
                Column(Modifier.align(Alignment.Center).padding(24.dp).clip(ShapeL).background(Color(0xE6161619)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (Linux.jobs.isEmpty()) "No apps open yet" else "Starting ${Linux.jobs.last().name}…", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Linux.lastFailure?.takeIf { Linux.jobs.isEmpty() }?.let {
                        Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(top = 10.dp).widthIn(max = 520.dp))
                    }
                    Text("Open an app from Linux apps, or run one with DISPLAY=:${XHolder.display}", color = Label, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    PrimaryButton("Linux apps", Modifier.widthIn(min = 200.dp), icon = Icons.Filled.Apps) { onApps() }
                }
            }
            if (switcher) WindowSwitcher(onDone = { switcher = false }, Modifier.align(Alignment.Center).padding(20.dp))
            if (prefs.perfOverlay) {
                Text("$fps fps · ${server.width}×${server.height} · ${XHolder.clients} apps", color = Color.White, fontFamily = Mono, fontSize = 11.sp,
                    modifier = Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopStart).clip(CircleShape)
                        .background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            // Floating menu button.
            Box(Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopEnd)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99161619)).border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Menu, "Display menu", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(menu, { menu = false }, containerColor = Card) {
                    MenuItem(Icons.Filled.Keyboard, "Keyboard") { menu = false; showKeyboard() }
                    MenuItem(if (prefs.touchMode == Prefs.MODE_DIRECT) Icons.Filled.Mouse else Icons.Filled.TouchApp,
                        if (prefs.touchMode == Prefs.MODE_DIRECT) "Switch to trackpad" else "Switch to direct touch") {
                        menu = false
                        prefs.touchMode = if (prefs.touchMode == Prefs.MODE_DIRECT) Prefs.MODE_TOUCHPAD else Prefs.MODE_DIRECT
                    }
                    MenuItem(Icons.Filled.Window, "Windows & desktops") { menu = false; switcher = true }
                    MenuItem(Icons.Filled.Mouse, if (mouseLocked) "Release mouse" else "Lock mouse") {
                        menu = false; mouseLocked = !mouseLocked; view?.capturePointer(mouseLocked)
                    }
                    MenuItem(Icons.Filled.ZoomOutMap, "Reset zoom") { menu = false; view?.resetZoom() }
                    MenuItem(Icons.Filled.PhotoCamera, "Screenshot") {
                        menu = false
                        val msg = runCatching { "Saved to " + Screenshot.save(context, server) }
                            .getOrElse { AppLog.e("Screenshot failed", it); "Couldn't save the screenshot" }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    MenuItem(Icons.Filled.Speed, if (prefs.perfOverlay) "Hide performance" else "Show performance") { menu = false; prefs.perfOverlay = !prefs.perfOverlay }
                    MenuItem(Icons.Filled.Keyboard, if (prefs.extraKeys) "Hide extra keys" else "Show extra keys") { menu = false; prefs.extraKeys = !prefs.extraKeys }
                    MenuItem(Icons.Filled.Apps, "Linux apps") { menu = false; onApps() }
                    MenuItem(Icons.AutoMirrored.Filled.ArrowBack, "Leave display") { menu = false; onBack() }
                }
            }
        }
        if (prefs.extraKeys) {
            Box(Modifier.navigationBarsPadding()) {
                ExtraKeysBar(com.mnvstudio.x11.display.AppProfiles.get(prefs, appClass)["keys"] ?: prefs.keyLayout, sticky, view = { view }, onKeyboard = { showKeyboard() }, onRightClick = { server.click(3) })
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, color = Ink, fontSize = 14.sp) }, leadingIcon = { Icon(icon, null, tint = Label) }, onClick = onClick)
}
