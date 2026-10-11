package com.mnvstudio.x11.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.server.Inspector
import com.mnvstudio.x11.server.WindowManager
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.OnAccent
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.Warn
import com.mnvstudio.x11.ui.copyText
import kotlinx.coroutines.delay

private fun wmWindows(): List<WindowManager.WindowInfo> = XHolder.server?.let { s -> synchronized(s) { s.wm?.windows() } } ?: emptyList()

/**
 * Quick app switching on the display: desktops along the top, open windows below. Tapping a window brings it forward
 * (switching desktop and un-minimizing as needed).
 */
@Composable
fun WindowSwitcher(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val s = XHolder.server ?: return
    val rev = XHolder.wmRevision
    val wm = synchronized(s) { s.wm }
    Column(modifier.widthIn(max = 520.dp).clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Windows", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, "Close", tint = Label, modifier = Modifier.clickable(onClick = onDone))
        }
        if (wm == null) {
            Text("Turn on Desktop mode in Settings to get movable windows, desktops and this switcher.", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in 0 until wm.desktops) {
                val count = remember(rev) { wmWindows().count { it.desktop == d } }
                Text("${d + 1}" + if (count > 0) " · $count" else "", color = if (d == wm.current) OnAccent else Ink, fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).background(if (d == wm.current) Accent else CardHigh)
                        .clickable { synchronized(s) { wm.switchDesktop(d) } }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        val list = remember(rev) { wmWindows() }
        if (list.isEmpty()) Text("No windows open.", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        list.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(ShapeM).background(if (w.focused) AccentSoft else CardHigh)
                .clickable { synchronized(s) { (s.resources[w.id] as? com.mnvstudio.x11.server.Window)?.let { wm.activate(it) } }; onDone() }
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.title, color = Ink, fontSize = 14.sp, maxLines = 1)
                    Text("${w.appClass.ifEmpty { "app" }} · desktop ${w.desktop + 1}" + if (w.minimized) " · minimized" else "", color = Faint, fontSize = 11.sp)
                }
                if (wm.desktops > 1) Text("→ ${(w.desktop + 1) % wm.desktops + 1}", color = Label, fontSize = 12.sp, modifier = Modifier.clip(CircleShape)
                    .clickable { synchronized(s) { (s.resources[w.id] as? com.mnvstudio.x11.server.Window)?.let { win -> wm.moveToDesktop(win, (w.desktop + 1) % wm.desktops) } } }
                    .padding(8.dp))
                Icon(Icons.Filled.Close, "Close ${w.title}", tint = Label, modifier = Modifier.clip(CircleShape)
                    .clickable { synchronized(s) { (s.resources[w.id] as? com.mnvstudio.x11.server.Window)?.let { wm.close(it) } } }.padding(8.dp))
            }
        }
        Text("Shortcuts: " + wm.shortcuts.joinToString(" · ") { "${it.first} ${it.second.lowercase()}" }, color = Faint, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp))
    }
}

/** Windows: the switcher full screen, plus per-app rules and remembered placement. */
@Composable
fun WindowsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val prefs = Prefs.of(context)
    Column(Modifier.fillMaxSize()) {
        TopBar("Windows", "Built-in window manager", onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                ToggleRow("Desktop mode", "Title bars, move, resize, snap, desktops (applies on restart)", prefs.desktopMode) { prefs.desktopMode = it }
                if (XHolder.server != null) WindowSwitcher(onDone = {}, Modifier.padding(top = 8.dp))
                SectionTitle("Per-app rules")
                val classes = (wmWindows().map { it.appClass } + prefs.appRules.split(';').mapNotNull { it.substringBefore('=').ifEmpty { null } } +
                    prefs.placements.split(';').mapNotNull { it.substringBefore('=').ifEmpty { null } }).filter { it.isNotEmpty() }.distinct()
                if (classes.isEmpty()) Text("Open apps in desktop mode to set rules for them.", color = Label, fontSize = 13.sp)
                classes.forEach { cls ->
                    val rule = prefs.appRules.split(';').firstOrNull { it.startsWith("$cls=") }?.substringAfter('=') ?: "windowed"
                    val remembered = prefs.placements.split(';').firstOrNull { it.startsWith("$cls=") }?.substringAfter('=')
                    SurfaceCard(Modifier.padding(bottom = 10.dp), padding = 14.dp) {
                        Text(cls, color = Ink, fontWeight = FontWeight.SemiBold)
                        if (remembered != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remembers position $remembered", color = Faint, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                            Text("Forget", color = Accent, fontSize = 12.sp, modifier = Modifier.clickable {
                                prefs.placements = prefs.placements.split(';').filter { it.isNotEmpty() && !it.startsWith("$cls=") }.joinToString(";")
                            }.padding(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Chips(listOf("windowed", "fullscreen"), rule, label = { it.replaceFirstChar(Char::uppercase) }) { r ->
                            prefs.appRules = (prefs.appRules.split(';').filter { it.isNotEmpty() && !it.startsWith("$cls=") } + "$cls=$r").joinToString(";")
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** X11 Inspector: who's connected and what they use, requests, extensions, and input recording. */
@Composable
fun InspectorScreen(back: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Clients") }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val s = XHolder.server
    Column(Modifier.fillMaxSize()) {
        TopBar("X11 Inspector", if (s == null) "Display is off" else "Display :${XHolder.display}", onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 760.dp)) {
                Chips(listOf("Clients", "Requests", "Extensions", "Recorder"), tab) { tab = it }
                Spacer(Modifier.height(10.dp))
                when (tab) {
                    "Clients" -> {
                        val list = remember(tick) { s?.let { Inspector.clients(it) } ?: emptyList() }
                        if (list.isEmpty()) Text("No apps connected.", color = Label)
                        list.forEach { c ->
                            SurfaceCard(Modifier.padding(bottom = 10.dp), padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.title.ifEmpty { "Client ${c.index}" }, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                                    Pill(c.peer, if (c.peer == "local") Live else Warn)
                                }
                                Text("${c.windows} windows · ${c.pixmaps} pixmaps (${formatSize(c.pixmapBytes)}) · ${c.gcs} GCs · ${c.fonts} fonts",
                                    color = Label, fontSize = 12.sp, fontFamily = Mono)
                                Text("${c.requests} requests · ${c.errors} errors", color = if (c.errors > 0) Warn else Faint, fontSize = 12.sp, fontFamily = Mono)
                                Text("Disconnect", color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).clickable {
                                    synchronized(s!!) { s.clients.firstOrNull { it.index == c.index } }?.close()
                                })
                            }
                        }
                    }
                    "Requests" -> {
                        ToggleRow("Trace requests", "Keep the last 500 requests (slightly slower)", Inspector.tracing) { Inspector.tracing = it; tick++ }
                        SectionTitle("Most used")
                        val counts = remember(tick) { Inspector.requestCounts().take(15) }
                        val max = counts.firstOrNull()?.second ?: 1
                        counts.forEach { (name, n) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = Ink, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(0.45f))
                                Box(Modifier.weight(0.4f).height(8.dp).clip(CircleShape).background(CardHigh)) {
                                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(CircleShape).background(Accent))
                                }
                                Text("$n", color = Label, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.weight(0.15f).padding(start = 6.dp))
                            }
                        }
                        if (Inspector.tracing) {
                            SectionTitle("Recent")
                            val recent = remember(tick) { Inspector.recent().takeLast(80).reversed() }
                            recent.forEach { r ->
                                Text("#${r.client} seq ${r.seq} ${Inspector.opcodeName(r.opcode)} ${r.bytes}B" + if (r.error != 0) "  error ${r.error}" else "",
                                    color = if (r.error != 0) Bad else Label, fontFamily = Mono, fontSize = 11.sp)
                            }
                        }
                    }
                    "Extensions" -> {
                        Inspector.EXTENSIONS.forEach { (name, ok, what) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(if (ok) "✓" else "–", color = if (ok) Live else Warn, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                                Column {
                                    Text(name, color = Ink, fontSize = 14.sp, fontFamily = Mono)
                                    Text(what, color = Label, fontSize = 12.sp)
                                }
                            }
                        }
                        Text("Visual: TrueColor, 24-bit depth (also 1-bit pixmaps). Apps asking for other visuals fall back to this.",
                            color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> RecorderTab(tick)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun RecorderTab(tick: Int) {
    val context = LocalContext.current
    val r = XHolder.server?.recorder
    if (r == null) { Text("Start the display to record input.", color = Label); return }
    Text("Records your taps, drags and keys on the display with their timing, and plays them back: to reproduce a problem or test an app.",
        color = Label, fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(12.dp))
    val n = remember(tick) { synchronized(r.events) { r.events.size } }
    Text(when { r.recording -> "Recording… $n events"; r.playing -> "Playing…"; else -> "$n events recorded" }, color = Ink, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (r.recording) SecondaryButton("Stop", Modifier.weight(1f), icon = Icons.Filled.Stop, color = Bad) { r.stopRecording() }
        else SecondaryButton("Record", Modifier.weight(1f), icon = Icons.Filled.FiberManualRecord, color = Bad, enabled = !r.playing) { r.startRecording() }
        if (r.playing) SecondaryButton("Stop", Modifier.weight(1f), icon = Icons.Filled.Stop) { r.stop() }
        else SecondaryButton("Play", Modifier.weight(1f), icon = Icons.Filled.PlayArrow, enabled = n > 0 && !r.recording) { r.play() }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Copy", Modifier.weight(1f), icon = Icons.Filled.ContentCopy, enabled = n > 0) { copyText(context, r.export(), "Recording copied") }
        SecondaryButton("Paste", Modifier.weight(1f), icon = Icons.Filled.ContentPaste) {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            val k = r.import(cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty())
            Toast.makeText(context, "$k events loaded", Toast.LENGTH_SHORT).show()
        }
    }
    Text("Tip: start recording here, switch to the display, do the steps, then come back and stop.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
}

