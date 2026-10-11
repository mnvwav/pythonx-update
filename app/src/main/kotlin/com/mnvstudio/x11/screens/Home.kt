package com.mnvstudio.x11.screens

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.HeaderGlow
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.StatusDot
import com.mnvstudio.x11.ui.Warn

@Composable
fun HomeScreen(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val server = XHolder.server
    val running = server != null
    LaunchedEffect(Unit) { Linux.check(context) }
    var batteryOk by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }

    Box(Modifier.fillMaxSize().background(Page)) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(HeaderGlow))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(44.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("X11", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Linux apps on your phone", color = Label, fontSize = 12.sp)
                    }
                    RoundIcon(Icons.AutoMirrored.Filled.MenuBook, "Help") { navigate("help") }
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.Filled.Settings, "Settings") { navigate("settings") }
                }

                // Server card.
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(running)
                        Text(if (running) "Display is on" else "Display is off", color = Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp).weight(1f))
                        if (running) Pill(if (XHolder.clients == 1) "1 app" else "${XHolder.clients} apps", Live)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Stat("Display", ":${if (running) XHolder.display else prefs.display}", Modifier.weight(1f))
                        val size = server?.let { "${it.width}×${it.height}" } ?: prefs.screenSize().let { "${it.first}×${it.second}" }
                        Stat("Resolution", size, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    if (running) {
                        PrimaryButton("Open display", Modifier.fillMaxWidth(), icon = Icons.Filled.Cast) { navigate("display") }
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Stop", Modifier.fillMaxWidth(), icon = Icons.Filled.Stop, color = Bad) { XService.stop(context) }
                    } else {
                        PrimaryButton("Start display", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) { XHolder.ensureStarted(context) }
                    }
                }

                XHolder.error?.let { msg -> Banner(Icons.Filled.ErrorOutline, msg, Bad) }
                if (!batteryOk) Banner(Icons.Filled.BatteryAlert, "Battery saving may stop X11 in the background. Tap to allow it to keep running.", Warn) {
                    Launcher.requestBatteryExemption(context); batteryOk = true
                }

                // Running Linux apps.
                if (Linux.jobs.isNotEmpty()) {
                    SectionTitle("Running")
                    Column(Modifier.fillMaxWidth().clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(vertical = 6.dp)) {
                        Linux.jobs.forEach { job ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(true, 7.dp)
                                Text(job.name, color = Ink, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
                                RoundIcon(Icons.Filled.Close, "Stop ${job.name}", tint = Label) { Linux.stop(job) }
                            }
                        }
                    }
                }

                // Quick launch: installed apps.
                val installed = remember(Linux.revision, Linux.state) {
                    if (Linux.state == Linux.State.READY) Catalog.apps.filter { Linux.isInstalled(context, it) } else emptyList()
                }
                SectionTitle("Apps")
                if (installed.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        installed.forEach { app ->
                            Column(
                                Modifier.width(96.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)
                                    .clickable(role = Role.Button) { if (Launcher.open(context, app)) navigate("display") }.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.size(40.dp).clip(ShapeM).background(AccentSoft), contentAlignment = Alignment.Center) {
                                    Icon(appIcon(app.id), null, tint = Accent, modifier = Modifier.size(22.dp))
                                }
                                Text(app.name, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                NavCard(Icons.Filled.Apps, "Linux apps", if (Linux.state == Linux.State.READY) "Install, open and manage apps" else "Set up the built-in Linux in one tap") { navigate("apps") }
                Spacer(Modifier.height(10.dp))
                NavCard(Icons.Filled.Terminal, "Console", "Run commands in Linux") { navigate("console") }
                Spacer(Modifier.height(10.dp))
                NavCard(Icons.Filled.HealthAndSafety, "Diagnostics", "Test and repair, view logs") { navigate("diagnostics") }

                SectionTitle("From Termux")
                Text("Already use Termux? Point apps at this display: DISPLAY=:${prefs.display}. See the guide for the mnvx11 command.",
                    color = Faint, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Label, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) {
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
