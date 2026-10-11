package com.mnvstudio.x11.screens

import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.OnAccent

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Metrics
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.Sessions
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.linux.LinuxApp
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Gauge
import com.mnvstudio.x11.ui.enter
import com.mnvstudio.x11.ui.pressable
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.Meter
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.Sparkline
import com.mnvstudio.x11.ui.StatusDot
import com.mnvstudio.x11.ui.Warn

private val WIDGET_NAMES = mapOf(
    "status" to "Display status", "superpowers" to "Superpowers", "resources" to "CPU, memory & storage", "running" to "Running apps",
    "favorites" to "Favorites & recent", "sessions" to "Sessions", "tools" to "Shortcuts",
)

@Composable
fun HomeScreen(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    DisposableEffect(Unit) { Metrics.start(context); onDispose { Metrics.stop() } }
    var editing by rememberSaveable { mutableStateOf(false) }
    // New widgets appear right after the status card the first time, not at the bottom.
    val order = prefs.dashboard.split(',').filter { it in WIDGET_NAMES }.let { o ->
        val missing = WIDGET_NAMES.keys.filter { it !in o }
        val at = o.indexOf("status") + 1
        o.take(at) + missing + o.drop(at)
    }

    Box(Modifier.fillMaxSize().background(Page)) {
        com.mnvstudio.x11.ui.AmbientBackground()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).animateContentSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.mnvstudio.x11.ui.LiveMark(48.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                        Text(when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Late night hacking" },
                            color = Label, fontSize = 12.sp)
                        Text("X11", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    RoundIcon(Icons.Filled.Dashboard, if (editing) "Done customizing" else "Customize dashboard", tint = if (editing) Accent else Ink) { editing = !editing }
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.AutoMirrored.Filled.MenuBook, "Help") { navigate("help") }
                }
                XHolder.error?.let { Banner(Icons.Filled.ErrorOutline, it, Bad) { navigate("diagnostics") } }
                if (Metrics.lowMemory) Banner(Icons.Filled.Memory, "The phone is low on memory. Close apps you don't need; Android may stop X11.", Warn) { navigate("processes") }
                if (Metrics.storageFreeMb in 1 until 500) Banner(Icons.Filled.SdStorage, "Only ${Metrics.storageFreeMb} MB of storage left. Clean up Linux to free space.", Warn) { navigate("maintenance") }

                if (editing) {
                    SectionTitle("Customize dashboard")
                    order.forEachIndexed { i, id -> EditRow(prefs, order, i, id) }
                    Text("Done", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp).clickable { editing = false })
                } else order.filter { it !in prefs.dashboardHidden }.forEachIndexed { i, id ->
                    Box(Modifier.enter(i, 70)) { when (id) {
                        "status" -> StatusWidget(prefs, navigate)
                        "superpowers" -> SuperpowersWidget(navigate)
                        "resources" -> ResourcesWidget(navigate)
                        "running" -> RunningWidget(navigate)
                        "favorites" -> FavoritesWidget(prefs, navigate)
                        "sessions" -> SessionsWidget(prefs, navigate)
                        "tools" -> ToolsWidget(navigate)
                    } }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** One widget in edit mode: move it up or down, or hide it. */
@Composable
private fun EditRow(prefs: Prefs, order: List<String>, i: Int, id: String) {
    val hidden = id in prefs.dashboardHidden
    fun move(d: Int) {
        val j = i + d
        if (j !in order.indices) return
        prefs.dashboard = order.toMutableList().apply { add(j, removeAt(i)) }.joinToString(",")
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(WIDGET_NAMES[id] ?: id, color = if (hidden) Faint else Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RoundIcon(Icons.Filled.KeyboardArrowUp, "Move ${WIDGET_NAMES[id]} up", tint = if (i > 0) Ink else Faint) { move(-1) }
        Spacer(Modifier.width(6.dp))
        RoundIcon(Icons.Filled.KeyboardArrowDown, "Move ${WIDGET_NAMES[id]} down", tint = if (i < order.lastIndex) Ink else Faint) { move(1) }
        Spacer(Modifier.width(6.dp))
        RoundIcon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hidden) "Show" else "Hide", tint = if (hidden) Faint else Accent) {
            prefs.dashboardHidden = if (hidden) prefs.dashboardHidden - id else prefs.dashboardHidden + id
        }
    }
}

@Composable
private fun WidgetCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(20.dp)) { content() }
}

@Composable
private fun StatusWidget(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val server = XHolder.server
    val running = server != null
    // Hero card in the brand gradient: the one thing most people come here for.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1100), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(ShapeL).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E1D1F), Color(0xFF121213))))
        .border(1.dp, com.mnvstudio.x11.ui.BrandAccent.copy(alpha = 0.22f), ShapeL).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (running) Live.copy(alpha = glow) else Color.White.copy(alpha = 0.35f)))
            Text(if (running) "Display is on" else "Display is off", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp).weight(1f))
            if (running) Text(if (XHolder.clients == 1) "1 app" else "${XHolder.clients} apps", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Text(Sessions.active(context)?.let { "Session: ${it.name}" } ?: "Ready when you are", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Row {
            for ((label, value) in listOf("Display" to ":${if (running) XHolder.display else prefs.display}",
                "Resolution" to (server?.let { "${it.width}×${it.height}" } ?: prefs.screenSize().let { "${it.first}×${it.second}" }),
                "DPI" to "${prefs.dpi()}")) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton(if (running) "Open display" else "Start display", if (running) Icons.Filled.Cast else Icons.Filled.PlayArrow, Modifier.weight(1f), solid = true) {
                if (running) navigate("display") else XHolder.ensureStarted(context)
            }
            if (running) HeroButton("Stop", Icons.Filled.Stop, Modifier.weight(0.6f), solid = false) { XService.stop(context) }
        }
    }
    Column {
        var batteryOk by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }
        if (!batteryOk) Banner(Icons.Filled.BatteryAlert, "Battery saving may stop X11 in the background. Tap to allow it to keep running.", Warn) {
            Launcher.requestBatteryExemption(context); batteryOk = true
        }
    }
}

@Composable
private fun HeroButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, solid: Boolean, onClick: () -> Unit) {
    Row(modifier.height(50.dp).clip(CircleShape).background(if (solid) com.mnvstudio.x11.ui.BrandAccent else Color.White.copy(alpha = 0.10f))
        .clickable(role = Role.Button, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val c = Color.White
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Text(text, color = c, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ResourcesWidget(navigate: (String) -> Unit) {
    val s = Metrics.latest
    val hist = Metrics.history
    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Resources", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Processes", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { navigate("processes") })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Gauge(s?.cpu ?: 0f, "CPU (X11)", Accent)
            Gauge(s?.ramUsedPct ?: 0f, "Phone RAM", if ((s?.ramUsedPct ?: 0f) > 85) Warn else Live)
            val used = if (Metrics.storageTotalMb > 0) (1f - Metrics.storageFreeMb.toFloat() / Metrics.storageTotalMb) * 100f else 0f
            Gauge(used, "Storage", if (Metrics.storageFreeMb in 1 until 500) Bad else Label)
        }
        Spacer(Modifier.height(14.dp))
        Text("CPU over the last 3 minutes", color = Faint, fontSize = 11.sp)
        Sparkline(hist.map { it.cpu }, Accent)
        Text("Phone memory in use", color = Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Sparkline(hist.map { it.ramUsedPct }, Live)
        Spacer(Modifier.height(8.dp))
        Text("X11 + Linux use ${s?.appRssMb ?: 0} MB · ${Metrics.ramAvailMb} of ${Metrics.ramTotalMb} MB RAM free · " +
            "%.1f GB storage free".format(Metrics.storageFreeMb / 1024f), color = Label, fontSize = 12.sp, fontFamily = Mono)
    }
}

@Composable
private fun RunningWidget(navigate: (String) -> Unit) {
    if (Linux.jobs.isEmpty()) return
    WidgetCard {
        Text("Running", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Linux.jobs.forEach { job ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(true, 7.dp)
                Text(job.name, color = Ink, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
                RoundIcon(Icons.Filled.Close, "Stop ${job.name}", tint = Label) { Linux.stop(job) }
            }
        }
        Text("All processes", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clickable { navigate("processes") })
    }
}

@Composable
private fun FavoritesWidget(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember(Linux.revision, Linux.state, prefs.favorites, prefs.recents) {
        if (Linux.state != Linux.State.READY) emptyList()
        else (prefs.favorites.mapNotNull { Catalog.byId(it) } + prefs.recentIds().mapNotNull { Catalog.byId(it) } +
            Catalog.apps.filter { Linux.isInstalled(context, it) }).distinct().filter { Linux.isInstalled(context, it) }.take(10)
    }
    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Library", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { navigate("apps") })
        }
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) {
            Text(if (Linux.state == Linux.State.READY) "Install apps from the library to launch them here." else "Set up Linux to install apps.",
                color = Label, fontSize = 13.sp)
        } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEach { app -> AppChip(prefs, app) { if (Launcher.open(context, app)) navigate("display") } }
        }
    }
}

@Composable
private fun AppChip(prefs: Prefs, app: LinuxApp, onClick: () -> Unit) {
    Column(Modifier.width(88.dp).pressable(onClick = onClick).clip(ShapeM).background(CardHigh).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        com.mnvstudio.x11.ui.AppIcon(app, 44.dp)
        Text(app.name, color = Ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        if (app.id in prefs.favorites) Text("★", color = Warn, fontSize = 10.sp)
    }
}

@Composable
private fun SessionsWidget(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val list = Sessions.all(context)
    if (list.isEmpty()) return
    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Manage", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { navigate("sessions") })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { s ->
                val active = Sessions.active(context)?.id == s.id
                Column(Modifier.width(150.dp).clip(ShapeM).background(if (active) AccentSoft else CardHigh)
                    .clickable { if (Sessions.launch(context, s)) navigate("display") }.padding(12.dp)) {
                    Text(s.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(s.summary(), color = Label, fontSize = 11.sp, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ToolsWidget(navigate: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        NavCard(Icons.Filled.Apps, "App library", "Install and open Linux apps") { navigate("apps") }
        Spacer(Modifier.height(10.dp))
        NavCard(Icons.Filled.Terminal, "Terminal", "Run commands in Linux") { navigate("console") }
        Spacer(Modifier.height(10.dp))
        NavCard(Icons.Filled.HealthAndSafety, "Diagnostics", "Test, repair and logs") { navigate("diagnostics") }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Label, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

@Composable
fun Banner(icon: ImageVector, text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(ShapeM).background(color.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Text(text, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
fun NavCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).clickable(role = Role.Button, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(ShapeM).background(CardHigh), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Label, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Faint)
    }
}



/**
 * What makes this more than an X server: a full desktop in one tap, the phone's files inside Linux, and Linux
 * programs that can reach the phone (notifications, links, clipboard, speech…).
 */
@Composable
private fun SuperpowersWidget(navigate: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val xfce = remember { com.mnvstudio.x11.linux.Catalog.byId("xfce4") }
    var desktopInstalled by remember(Linux.revision) { mutableStateOf(xfce != null && Linux.isInstalled(context, xfce)) }
    var installing by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf(com.mnvstudio.x11.bridge.PhoneStorage.granted(context)) }
    // Re-check while visible (e.g. after returning from Android's permission page).
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); phone = com.mnvstudio.x11.bridge.PhoneStorage.granted(context) } }
    var demo by remember { mutableStateOf(false) }
    val ready = Linux.state == Linux.State.READY

    WidgetCard {
        Text("Superpowers", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Things a phone shouldn't be able to do.", color = Label, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Power(Icons.Filled.DesktopWindows, "Full Linux desktop",
            when { installing != null -> installing!!; desktopInstalled -> "XFCE with panel, menu and file manager · tap to open"; else -> "One tap installs XFCE (about 150 MB) and opens it" },
            busy = installing != null, enabled = ready) {
            if (xfce == null) return@Power
            if (desktopInstalled) { if (com.mnvstudio.x11.Launcher.open(context, xfce)) navigate("display") }
            else {
                installing = "Installing… this takes a few minutes"
                Linux.installPackages(context, xfce.packages, onOutput = { l -> if (l.startsWith("(")) installing = l.take(60) }) { ok ->
                    installing = null; desktopInstalled = ok
                    if (ok && com.mnvstudio.x11.Launcher.open(context, xfce)) navigate("display")
                    else if (!ok) android.widget.Toast.makeText(context, "Install failed: see Diagnostics → Log", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        Power(Icons.Filled.PhoneAndroid, "Phone files in Linux",
            if (phone) "Your Downloads, photos and documents are at ~/Phone" else "Let Linux apps open and save files on your phone",
            on = phone) { if (!phone && activity != null) com.mnvstudio.x11.bridge.PhoneStorage.request(activity) }
        Power(Icons.Filled.NotificationsActive, "Linux talks to your phone",
            "notify-send, links, clipboard, vibration and speech reach Android · tap to try", enabled = ready) { demo = true }
    }
    if (demo) AlertDialog(onDismissRequest = { demo = false }, containerColor = Card,
        title = { Text("Linux → Android", color = Ink) },
        text = {
            Column {
                Text("Linux programs (and you, in the terminal) can use the phone with the android command. Try one:", color = Label, fontSize = 13.sp)
                listOf(
                    "Notification" to "notify-send 'Hello from Linux' 'This came from Alpine'",
                    "Vibrate" to "android vibrate 400",
                    "Speak" to "android say 'Linux is running on my phone'",
                    "Open a link" to "xdg-open https://alpinelinux.org",
                    "Battery" to "android battery && android toast \"$(android battery | head -1)\"",
                ).forEach { (label, cmd) ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(ShapeM).background(CardHigh)
                        .clickable { Linux.run(context, cmd, XHolder.display) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(cmd, color = Faint, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
                        }
                        Icon(Icons.Filled.PlayArrow, "Run", tint = Accent)
                    }
                }
                Text("More: android help", color = Faint, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton({ demo = false }) { Text("Done") } })
}

@Composable
private fun Power(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, on: Boolean = false, busy: Boolean = false,
                  enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).pressable(enabled = enabled && !busy, scaleTo = 0.98f, onClick = onClick).clip(ShapeM)
        .background(CardHigh).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(ShapeM).background(com.mnvstudio.x11.ui.accentGradient()), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = OnAccent, strokeWidth = 2.dp)
            else Icon(icon, null, tint = OnAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = if (enabled) Ink else Faint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (on) Icon(Icons.Filled.CheckCircle, "On", tint = Live) else Icon(Icons.Filled.ChevronRight, null, tint = Faint)
    }
}
