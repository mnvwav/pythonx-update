package com.mnvstudio.x11.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.display.Action
import com.mnvstudio.x11.display.AppProfiles
import com.mnvstudio.x11.display.ExtraKeys
import com.mnvstudio.x11.display.InputConfig
import com.mnvstudio.x11.display.Shortcuts
import com.mnvstudio.x11.server.Keyboard
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.OnAccent
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.ShapeS
import com.mnvstudio.x11.ui.SliderRow
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.Warn

@Composable
fun InputScreen(back: () -> Unit) {
    val context = LocalContext.current
    val p = Prefs.of(context)
    var tab by remember { mutableStateOf("Gestures") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Input", "Touch, mouse, keyboard and controllers", onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                Chips(listOf("Gestures", "Pointer", "Keys", "Devices", "Per app", "Test"), tab) { tab = it }
                when (tab) {
                    "Gestures" -> Gestures(p)
                    "Pointer" -> Pointer(p)
                    "Keys" -> Keys(p)
                    "Devices" -> Devices(p)
                    "Per app" -> PerApp(p)
                    else -> Tests()
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActionPicker(title: String, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box {
            Text(Action.of(value).label, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            DropdownMenu(open, { open = false }, containerColor = Card) {
                Action.entries.forEach { a -> DropdownMenuItem(text = { Text(a.label, color = Ink) }, onClick = { open = false; onPick(a.name) }) }
            }
        }
    }
}

@Composable
private fun Gestures(p: Prefs) {
    SectionTitle("Multi-finger gestures")
    SurfaceCard(padding = 16.dp) {
        ActionPicker("Two-finger tap", p.gestureTap2) { p.gestureTap2 = it }
        ActionPicker("Three-finger tap", p.gestureTap3) { p.gestureTap3 = it }
        ActionPicker("Four-finger tap", p.gestureTap4) { p.gestureTap4 = it }
        ActionPicker("Three-finger swipe up", p.gestureSwipeUp) { p.gestureSwipeUp = it }
        ActionPicker("Three-finger swipe down", p.gestureSwipeDown) { p.gestureSwipeDown = it }
        Text("Two-finger drag always scrolls and pinch always zooms.", color = Faint, fontSize = 12.sp)
    }
    SectionTitle("Timing")
    SurfaceCard(padding = 16.dp) {
        SliderRow("Long press to drag", "${p.longPressMs} ms", p.longPressMs.toFloat(), 200f..1000f, steps = 15) { p.longPressMs = it.toInt() }
        SliderRow("Movement before a drag", "${p.dragSlop} dp", p.dragSlop.toFloat(), 2f..24f, steps = 10) { p.dragSlop = it.toInt() }
        SliderRow("Double-click gap", "${p.doubleClickMs} ms", p.doubleClickMs.toFloat(), 40f..300f, steps = 12) { p.doubleClickMs = it.toInt() }
        Text("The gap between the two clicks a \"double click\" gesture sends. Linux apps usually accept up to about 400 ms.",
            color = Faint, fontSize = 12.sp, lineHeight = 17.sp)
    }
    SectionTitle("Ignoring accidental touches")
    SurfaceCard(padding = 16.dp) {
        SliderRow("Edge rejection", if (p.edgeReject == 0) "off" else "${p.edgeReject} dp", p.edgeReject.toFloat(), 0f..48f, steps = 11) { p.edgeReject = it.toInt() }
        Text("Ignore touches that start this close to the screen edge (gesture navigation, grip).", color = Faint, fontSize = 12.sp)
        ToggleRow("Palm rejection", "Ignore very large contacts like a resting palm", p.palmReject) { p.palmReject = it }
    }
}

@Composable
private fun Pointer(p: Prefs) {
    SectionTitle("Movement")
    SurfaceCard(padding = 16.dp) {
        SliderRow("Pointer speed", "%.1f×".format(p.pointerSpeed), p.pointerSpeed, 0.5f..3f) { p.pointerSpeed = it }
        SliderRow("Acceleration", if (p.mouseAccel == 0f) "off" else "%.1f".format(p.mouseAccel), p.mouseAccel, 0f..2f, steps = 19) { p.mouseAccel = it }
        Text("With acceleration, fast swipes cross the screen and slow ones stay precise.", color = Faint, fontSize = 12.sp)
    }
    SectionTitle("Look")
    SurfaceCard(padding = 16.dp) {
        ToggleRow("Show pointer", "In trackpad mode", p.showPointer) { p.showPointer = it }
        SliderRow("Pointer size", "%.1f×".format(p.cursorScale), p.cursorScale, 0.6f..3f) { p.cursorScale = it }
        ToggleRow("Pointer trail", "Fading dots behind the pointer, easier to follow", p.pointerTrail) { p.pointerTrail = it }
    }
}

/** Extra keys layout: tap a key to remove it, add from the palette, move with the arrows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Keys(p: Prefs) {
    val keys = ExtraKeys.parse(p.keyLayout)
    var selected by remember { mutableStateOf(-1) }
    fun save(list: List<String>) { p.keyLayout = list.joinToString(" ") }
    SectionTitle("Extra keys bar")
    SurfaceCard(padding = 14.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.forEachIndexed { i, k ->
                Text(k, color = if (i == selected) OnAccent else Ink, fontFamily = Mono, fontSize = 13.sp,
                    modifier = Modifier.clip(ShapeS).background(if (i == selected) Accent else CardHigh).border(1.dp, Line, ShapeS)
                        .clickable { selected = if (selected == i) -1 else i }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
        if (selected in keys.indices) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(keys[selected], color = Ink, fontFamily = Mono, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", tint = Ink, modifier = Modifier.clickable {
                if (selected > 0) { save(keys.toMutableList().apply { add(selected - 1, removeAt(selected)) }); selected-- } })
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", tint = Ink, modifier = Modifier.clickable {
                if (selected < keys.lastIndex) { save(keys.toMutableList().apply { add(selected + 1, removeAt(selected)) }); selected++ } })
            Icon(Icons.Filled.Close, "Remove", tint = Label, modifier = Modifier.clickable { save(keys.filterIndexed { j, _ -> j != selected }); selected = -1 })
        }
        Text("Add a key", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        val palette = listOf("ESC", "TAB", "CTRL", "ALT", "SHIFT", "SUPER", "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN", "INS", "DEL",
            "BKSP", "ENTER", "FN", "KBD", "RCLICK", "DRAG", "|", "~", "/", "-", "_", "*") + (1..12).map { "F$it" }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.forEach { k ->
                Text("+ $k", color = Accent, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.clip(ShapeS).border(1.dp, Line, ShapeS)
                    .clickable { save(keys + k) }.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
        Text("Reset to default", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp).clickable { p.keyLayout = Prefs.DEFAULT_KEYS; selected = -1 })
    }
    SectionTitle("Shortcuts")
    SurfaceCard(padding = 16.dp) {
        ToggleRow("App shortcuts on hardware keyboards", "Ctrl+Alt+K/W/M/Q/L control X11 itself", p.appShortcuts) { p.appShortcuts = it }
        val wm = XHolder.server?.let { synchronized(it) { it.wm?.shortcuts } } ?: if (p.desktopMode) com.mnvstudio.x11.server.WindowManager(com.mnvstudio.x11.server.XServer(8, 8)).shortcuts else emptyList()
        (Shortcuts.APP.filter { p.appShortcuts }.map { it.keys to "${it.what} (X11)" } + wm.map { it.first to "${it.second} (window manager)" }).forEach { (k, w) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k, color = Ink, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                Text(w, color = Label, fontSize = 13.sp, modifier = Modifier.weight(0.6f))
            }
        }
        val conflicts = Shortcuts.conflicts(wm, p.appShortcuts)
        if (conflicts.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(18.dp))
                Text("Conflicts", color = Warn, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
            conflicts.forEach { (s, others) ->
                Text("${s.keys}: ${s.what} (${s.owner}) hides " + others.joinToString { "${it.what} in ${it.owner}" }, color = Label, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        } else Text("No conflicts.", color = Live, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Devices(p: Prefs) {
    SectionTitle("Stylus")
    SurfaceCard(padding = 16.dp) {
        ActionPicker("Side button", p.stylusButton) { p.stylusButton = it }
        Text("The stylus points directly, like direct touch. Pressure isn't passed to apps yet (needs the XInput extension).", color = Faint, fontSize = 12.sp)
    }
    SectionTitle("Game controller")
    SurfaceCard(padding = 16.dp) {
        ToggleRow("Left stick moves the pointer", null, p.stickMouse) { p.stickMouse = it }
        val custom = p.gamepadMap.split(',').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        InputConfig.GAMEPAD_BUTTONS.forEach { (code, name) ->
            val default = InputConfig.DEFAULT_GAMEPAD[code]?.let { c -> listOf("ENTER", "ESC", "TAB", "UP", "DOWN", "LEFT", "RIGHT").firstOrNull { ExtraKeys.keycode(it) == c } ?: "SPACE" }
                ?: if (name == "L1") "left click" else if (name == "R1") "right click" else "—"
            var text by remember(p.gamepadMap) { mutableStateOf(custom[name] ?: "") }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Ink, fontFamily = Mono, modifier = Modifier.weight(0.25f))
                OutlinedTextField(text, { t ->
                    text = t.uppercase()
                    val m = custom.toMutableMap().apply { if (text.isBlank()) remove(name) else put(name, text) }
                    p.gamepadMap = m.entries.joinToString(",") { "${it.key}=${it.value}" }
                }, Modifier.weight(0.75f), singleLine = true, shape = ShapeM, placeholder = { Text(default, color = Faint) },
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink))
            }
        }
        Text("Type a key name (ENTER, ESC, TAB, F5, UP…). Empty keeps the default shown.", color = Faint, fontSize = 12.sp)
    }
    SectionTitle("Mouse")
    SurfaceCard(padding = 16.dp) {
        Text("USB and Bluetooth mice work directly. For games and 3D apps, use \"Lock mouse\" in the display menu so the mouse moves freely; " +
            "Ctrl+Alt+L or the Back button releases it.", color = Label, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/** Per-app input: gesture overrides and an extra-keys layout for one app (by window class). */
@Composable
private fun PerApp(p: Prefs) {
    val profiles = AppProfiles.all(p)
    val open = XHolder.server?.let { s -> synchronized(s) { s.wm?.windows()?.map { it.appClass } } }.orEmpty()
    val classes = (profiles.keys + open).filter { it.isNotEmpty() }.distinct()
    var newClass by remember { mutableStateOf("") }
    Text("Different gestures or extra keys for one app, e.g. arrow-heavy keys for a game or Esc first for Vim. Apps are matched by window class.",
        color = Label, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
    classes.forEach { cls ->
        val v = profiles[cls] ?: emptyMap()
        SurfaceCard(Modifier.padding(top = 12.dp), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cls, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (v.isNotEmpty()) Text("Clear", color = Label, fontSize = 13.sp, modifier = Modifier.clickable { AppProfiles.set(p, cls, emptyMap()) })
            }
            ActionPicker("Two-finger tap", v["tap2"] ?: p.gestureTap2) { AppProfiles.set(p, cls, v + ("tap2" to it)) }
            ActionPicker("Three-finger tap", v["tap3"] ?: p.gestureTap3) { AppProfiles.set(p, cls, v + ("tap3" to it)) }
            var keys by remember(cls) { mutableStateOf(v["keys"] ?: "") }
            OutlinedTextField(keys, { keys = it.uppercase(); AppProfiles.set(p, cls, if (keys.isBlank()) v - "keys" else v + ("keys" to keys)) },
                Modifier.fillMaxWidth(), shape = ShapeM, label = { Text("Extra keys for this app (empty = default)") },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink))
        }
    }
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newClass, { newClass = it.trim() }, Modifier.weight(1f), singleLine = true, shape = ShapeM, placeholder = { Text("Window class, e.g. XTerm", color = Faint) },
            textStyle = TextStyle(fontFamily = Mono, color = Ink))
        Spacer(Modifier.size(10.dp))
        SecondaryButton("Add", enabled = newClass.isNotEmpty()) { AppProfiles.set(p, newClass, mapOf("tap2" to p.gestureTap2)); newClass = "" }
    }
}

/** Input tests: what the keyboard sends (text input compatibility) and touch latency. */
@Composable
private fun Tests() {
    val context = LocalContext.current
    SectionTitle("Text input tester")
    var text by remember { mutableStateOf("") }
    SurfaceCard(padding = 16.dp) {
        Text("Type with your keyboard app (try other languages or emoji). Each character shows the X key it becomes.", color = Label, fontSize = 13.sp)
        OutlinedTextField(text, { text = it.takeLast(40) }, Modifier.fillMaxWidth().padding(top = 10.dp), shape = ShapeM, textStyle = TextStyle(color = Ink, fontSize = 18.sp))
        val kb = remember { Keyboard() }
        val cps = text.codePoints().toArray()
        Column(Modifier.padding(top = 10.dp).clip(ShapeM).background(CodeBg).padding(10.dp)) {
            if (cps.isEmpty()) Text("—", color = Faint, fontFamily = Mono)
            cps.forEach { cp ->
                val ks = Keyboard.keysymFor(cp)
                val found = kb.find(ks)
                Text("${String(Character.toChars(cp))}  U+%04X → keysym 0x%X → ".format(cp, ks) +
                    (found?.let { "keycode ${it.first}${if (it.second) " + Shift" else ""}" } ?: "spare keycode (remapped on the fly)"),
                    color = if (found != null) Label else Accent, fontFamily = Mono, fontSize = 12.sp)
            }
        }
        Text("Characters outside the US layout are typed through spare keys that X11 remaps as needed, so any script works in apps that support it.",
            color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
    }
    SectionTitle("Touch latency")
    val samples = remember { mutableStateListOf<Float>() }
    var last by remember { mutableFloatStateOf(0f) }
    SurfaceCard(padding = 16.dp) {
        Text("Tap or drag in the box. This measures the time from your touch to the next frame on screen.", color = Label, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(160.dp).padding(top = 10.dp).clip(ShapeM).background(CardHigh).border(1.dp, Line, ShapeM)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: continue
                        val ms = (android.os.SystemClock.uptimeMillis() - c.uptimeMillis).toFloat()
                        last = ms; samples += ms; if (samples.size > 60) samples.removeAt(0)
                    }
                }
            }, contentAlignment = Alignment.Center) {
            Text(if (samples.isEmpty()) "Touch here" else "%.0f ms".format(last), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        if (samples.isNotEmpty()) {
            val avg = samples.average()
            Text("Average %.0f ms over %d samples. ".format(avg, samples.size) + when {
                avg < 20 -> "Excellent."
                avg < 40 -> "Good. 120 fps (Settings → Display) can shave a few ms."
                else -> "High: close other apps, lower Detail, or raise the frame rate limit."
            }, color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            SecondaryButton("Reset", Modifier.padding(top = 8.dp)) { samples.clear() }
        }
    }
}

