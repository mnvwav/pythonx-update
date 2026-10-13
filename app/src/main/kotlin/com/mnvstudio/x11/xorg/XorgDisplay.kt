package com.mnvstudio.x11.xorg

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mnvstudio.x11.Pip
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.ui.*

/** The display for the X.Org engine: starting/failed states, the live screen, a menu and an extra-keys row. */
@Composable
fun XorgDisplay(prefs: Prefs, onBack: () -> Unit, onApps: () -> Unit) {
    val context = LocalContext.current
    when (XorgEngine.state) {
        XorgEngine.State.STARTING, XorgEngine.State.STOPPED -> Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LiveMark(96.dp)
            Text(XorgEngine.status.ifEmpty { "Starting X.Org…" }, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
            Text("The full X.Org server runs inside Linux, so every app works like on a PC.", color = Label, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
            LinearProgressIndicator(Modifier.padding(top = 20.dp).widthIn(max = 260.dp).fillMaxWidth(), color = Accent, trackColor = CardHigh)
            Spacer(Modifier.height(24.dp))
            SecondaryButton("Back", Modifier.widthIn(min = 200.dp)) { onBack() }
        }
        XorgEngine.State.FAILED -> Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Bad, modifier = Modifier.size(48.dp))
            Text("X.Org couldn't start", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(XorgEngine.status, color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            PrimaryButton("Try again", Modifier.widthIn(min = 240.dp), icon = Icons.Filled.Refresh) { XHolder.stop(); XHolder.ensureStarted(context) }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Use the built-in engine", Modifier.widthIn(min = 240.dp)) {
                XHolder.stop(); prefs.engine = Prefs.ENGINE_BUILTIN; XHolder.ensureStarted(context)
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Back", Modifier.widthIn(min = 240.dp)) { onBack() }
        }
        XorgEngine.State.RUNNING -> Running(prefs, onBack, onApps)
    }
}

@Composable
private fun Running(prefs: Prefs, onBack: () -> Unit, onApps: () -> Unit) {
    val context = LocalContext.current
    var view by remember { mutableStateOf<XorgView?>(null) }
    var menu by remember { mutableStateOf(false) }
    var mods by remember { mutableStateOf(setOf<Int>()) }
    fun keyboard() {
        val v = view ?: return
        v.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }
    DisposableEffect(Unit) { Pip.displayOpen = true; onDispose { Pip.displayOpen = false } }

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { ctx -> XorgView(ctx).also { view = it; it.onKeyboardRequest = { keyboard() } } }, Modifier.fillMaxSize(), update = { v ->
                v.directTouch = prefs.touchMode == Prefs.MODE_DIRECT
                v.pointerSpeed = prefs.pointerSpeed
                v.smooth = prefs.smoothScaling
                v.maxFps = prefs.maxFps
                v.onModsUsed = { mods = emptySet() }
            })
            if (XHolder.clients == 0 && !Pip.active) {
                Column(Modifier.align(Alignment.Center).padding(24.dp).clip(ShapeL).background(Color(0xE6161619)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("X.Org is running", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Open an app or a full desktop. Everything uses DISPLAY=:${XHolder.display}", color = Label, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    PrimaryButton("Linux apps", Modifier.widthIn(min = 200.dp), icon = Icons.Filled.Apps) { onApps() }
                }
            }
            if (!Pip.active) Box(Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopEnd)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99161619)).border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, "Display menu", tint = Color.White, modifier = Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }, containerColor = Card) {
                    @Composable fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: () -> Unit) =
                        DropdownMenuItem({ Text(text, color = Ink) }, { menu = false; action() }, leadingIcon = { Icon(icon, null, tint = Label) })
                    item(Icons.Filled.Keyboard, "Keyboard") { keyboard() }
                    item(if (prefs.touchMode == Prefs.MODE_DIRECT) Icons.Filled.Mouse else Icons.Filled.TouchApp,
                        if (prefs.touchMode == Prefs.MODE_DIRECT) "Switch to trackpad" else "Switch to direct touch") {
                        prefs.touchMode = if (prefs.touchMode == Prefs.MODE_DIRECT) Prefs.MODE_TOUCHPAD else Prefs.MODE_DIRECT
                    }
                    item(Icons.Filled.PictureInPictureAlt, "Float over other apps") {
                        val a = context as? Activity
                        if (a == null || !Pip.enter(a)) Toast.makeText(context, "Picture-in-picture isn't available on this phone", Toast.LENGTH_SHORT).show()
                    }
                    item(Icons.Filled.Keyboard, if (prefs.extraKeys) "Hide extra keys" else "Show extra keys") { prefs.extraKeys = !prefs.extraKeys }
                    item(Icons.Filled.Apps, "Linux apps") { onApps() }
                    item(Icons.Filled.RestartAlt, "Restart X.Org") { XHolder.restart(context, "from the display menu") }
                    item(Icons.Filled.Stop, "Stop X.Org") { XService.stop(context); onBack() }
                    item(Icons.AutoMirrored.Filled.ArrowBack, "Leave display") { onBack() }
                }
            }
        }
        if (prefs.extraKeys && !Pip.active) Row(Modifier.fillMaxWidth().navigationBarsPadding().background(Color(0xFF111112)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable fun k(label: String, on: Boolean = false, action: () -> Unit) =
                Box(Modifier.height(40.dp).widthIn(min = 46.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Accent else Color(0xFF232326))
                    .clickable(onClick = action).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) OnAccent else Color(0xFFECEBE8), fontSize = 13.sp, fontFamily = Mono)
                }
            fun mod(sym: Int) { val v = view ?: return; if (sym in v.pendingMods) v.pendingMods.remove(sym) else v.pendingMods.add(sym); mods = v.pendingMods.toSet() }
            k("⌨") { keyboard() }
            k("ESC") { view?.tap(0xff1b) }; k("TAB") { view?.tap(0xff09) }
            k("CTRL", 0xffe3 in mods) { mod(0xffe3) }; k("ALT", 0xffe9 in mods) { mod(0xffe9) }; k("SUPER", 0xffeb in mods) { mod(0xffeb) }
            k("←") { view?.tap(0xff51) }; k("↑") { view?.tap(0xff52) }; k("↓") { view?.tap(0xff54) }; k("→") { view?.tap(0xff53) }
            k("HOME") { view?.tap(0xff50) }; k("END") { view?.tap(0xff57) }; k("PGUP") { view?.tap(0xff55) }; k("PGDN") { view?.tap(0xff56) }
            (1..12).forEach { n -> k("F$n") { view?.tap(0xffbe + n - 1) } }
        }
    }
}
