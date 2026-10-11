package com.mnvstudio.x11.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.CommandBlock
import com.mnvstudio.x11.ui.Cyan
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.StatusDot
import com.mnvstudio.x11.ui.SurfaceCard

@Composable
fun HomeScreen(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val server = XHolder.server
    val running = server != null
    Box(Modifier.fillMaxSize().background(Page)) {
        Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(Color(0xFF17143A), Page))))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(40.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("MNV X11", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("X11 display server", color = Label, fontSize = 12.sp)
                    }
                    RoundIcon(Icons.AutoMirrored.Filled.MenuBook, "Guide") { navigate("guide") }
                    Spacer(Modifier.size(8.dp))
                    RoundIcon(Icons.Filled.Settings, "Settings") { navigate("settings") }
                }

                // Status card.
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(ShapeL)
                        .background(Brush.linearGradient(listOf(Color(0xFF221B52), Card))).border(1.dp, Line, ShapeL).padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(running)
                        Text(if (running) "Server running" else "Server stopped", color = Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp).weight(1f))
                        if (running) Text(
                            if (XHolder.clients == 1) "1 app" else "${XHolder.clients} apps", color = Cyan, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(CircleShape).background(Cyan.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row {
                        Stat("Display", ":${if (running) XHolder.display else prefs.display}", Modifier.weight(1f))
                        val size = server?.let { "${it.width} × ${it.height}" } ?: prefs.screenSize().let { "${it.first} × ${it.second}" }
                        Stat("Resolution", size, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    if (running) {
                        PrimaryButton("Open display", Modifier.fillMaxWidth(), icon = Icons.Filled.Cast) { navigate("display") }
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Stop server", Modifier.fillMaxWidth(), icon = Icons.Filled.Stop, color = Bad) { XService.stop(context) }
                    } else {
                        PrimaryButton("Start server", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) { XService.start(context) }
                    }
                }

                XHolder.error?.let { msg ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(ShapeM).background(Bad.copy(alpha = 0.12f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Bad)
                        Text(msg, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("Launch an app")
                SurfaceCard {
                    Text("From Termux or a proot distro:", color = Label, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    CommandBlock("export DISPLAY=:${prefs.display}")
                    Spacer(Modifier.height(10.dp))
                    CommandBlock("xterm &")
                    Spacer(Modifier.height(12.dp))
                    Text("Over TCP instead: DISPLAY=127.0.0.1:${prefs.display}", color = Faint, fontSize = 12.sp, fontFamily = Mono)
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("Try these")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("xeyes", "xclock", "xcalc").forEach { app ->
                        AppChip(app, Modifier.weight(1f))
                    }
                }
                Text("Install with: apt install x11-apps (proot distro)", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Label, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, tint: Color = Ink, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(CardHigh).border(1.dp, Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AppChip(name: String, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier.clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)
            .clickable { com.mnvstudio.x11.ui.copyText(context, "$name &") }.padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, color = Ink, fontFamily = Mono, fontSize = 14.sp)
        Text("tap to copy", color = Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
