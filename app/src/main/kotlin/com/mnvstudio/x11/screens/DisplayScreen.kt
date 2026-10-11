package com.mnvstudio.x11.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.display.ExtraKeysBar
import com.mnvstudio.x11.display.Sticky
import com.mnvstudio.x11.display.XDisplayView
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SecondaryButton

/** Full-screen view of the X display, with a floating menu and an optional extra-keys bar. */
@Composable
fun DisplayScreen(prefs: Prefs, onBack: () -> Unit) {
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
            Text("The X server isn't running", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Start it to see your apps here.", color = Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            PrimaryButton("Start server", Modifier.widthIn(min = 220.dp), icon = Icons.Filled.PlayArrow) { XService.start(context) }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Back", Modifier.widthIn(min = 220.dp)) { onBack() }
        }
        return
    }

    val sticky = remember { Sticky() }
    var menu by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<XDisplayView?>(null) }

    fun showKeyboard() {
        val v = view ?: return
        v.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    v.cursorScale = prefs.cursorScale
                    v.keepScreenOn = prefs.keepScreenOn
                    v.stickyMods = { sticky.active() }
                    v.onStickyUsed = { sticky.used() }
                },
                onRelease = { it.server = null },
            )
            // Floating menu button.
            Box(Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopEnd)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99141826)).border(1.dp, Color(0x55FFFFFF), CircleShape)
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
                    MenuItem(Icons.Filled.ZoomOutMap, "Reset zoom") { menu = false; view?.resetZoom() }
                    MenuItem(Icons.Filled.Keyboard, if (prefs.extraKeys) "Hide extra keys" else "Show extra keys") {
                        menu = false; prefs.extraKeys = !prefs.extraKeys
                    }
                    MenuItem(Icons.AutoMirrored.Filled.ArrowBack, "Leave display") { menu = false; onBack() }
                }
            }
        }
        if (prefs.extraKeys) {
            Box(Modifier.navigationBarsPadding()) {
                ExtraKeysBar(prefs.keyLayout, sticky, view = { view }, onKeyboard = { showKeyboard() }, onRightClick = { server.click(3) })
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Ink, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = Label) },
        onClick = onClick,
    )
}
