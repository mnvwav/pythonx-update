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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.TopBar

/** One tool on the Linux tab. */
private class Tool(val route: String, val icon: ImageVector, val title: String, val body: String)

private val TOOLS = listOf(
    "Tools" to listOf(
        Tool("toolbox", Icons.Filled.HomeRepairService, "Toolbox", "70+ tools: files, system, network, web"),
        Tool("browser", Icons.Filled.Public, "Browser", "Light web browser with private mode"),
        Tool("downloads", Icons.Filled.Download, "Downloads", "Pause, resume, queue, multi-connection"),
        Tool("share", Icons.Filled.Share, "Share over Wi-Fi", "Send files to any device's browser"),
    ),
    "Use" to listOf(
        Tool("console", Icons.Filled.Terminal, "Terminal", "Run commands with history"),
        Tool("files", Icons.Filled.Folder, "Files", "Browse, edit, import and export"),
        Tool("processes", Icons.Filled.Memory, "Processes", "See and stop what's running"),
        Tool("packages", Icons.Filled.Inventory2, "Packages", "Search, install, history, details"),
    ),
    "Manage" to listOf(
        Tool("profiles", Icons.Filled.Layers, "Linux profiles", "Separate environments, clone, snapshots"),
        Tool("backup", Icons.Filled.Backup, "Backup & restore", "Export or import the whole environment"),
        Tool("environment", Icons.Filled.Tune, "Environment", "Variables, startup scripts, shell profile"),
        Tool("users", Icons.Filled.Person, "Users", "Run as root or a normal user"),
    ),
    "Display" to listOf(
        Tool("windows", Icons.Filled.Window, "Windows", "Switcher, desktops, per-app rules"),
        Tool("inspector", Icons.Filled.Insights, "X11 Inspector", "Clients, requests, extensions, recorder"),
        Tool("input", Icons.Filled.TouchApp, "Input & controls", "Gestures, keys, stylus, gamepad, per-app"),
    ),
    "Fix" to listOf(
        Tool("diagnostics", Icons.Filled.HealthAndSafety, "Diagnostics", "Self-test, repair and logs"),
        Tool("maintenance", Icons.Filled.Build, "Maintenance", "Integrity check, cleanup, sources"),
        Tool("dev", Icons.Filled.Code, "Developer tools", "Toolkits and Git"),
    ),
)

@Composable
fun LinuxScreen(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Linux", when (Linux.state) {
            Linux.State.READY -> "Alpine Linux · ${Linux.activeProfileName(context)}"
            Linux.State.INSTALLING -> "Setting up…"
            else -> "Not set up yet"
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 680.dp)) {
                if (Linux.state != Linux.State.READY) SetupCard()
                TOOLS.forEach { (section, tools) ->
                    SectionTitle(section)
                    tools.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { t -> ToolTile(t, Modifier.weight(1f)) { navigate(t.route) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToolTile(t: Tool, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).clickable(role = Role.Button, onClick = onClick).padding(16.dp)) {
        Box(Modifier.size(40.dp).clip(ShapeM).background(AccentSoft), contentAlignment = Alignment.Center) { Icon(t.icon, null, tint = Accent) }
        Text(t.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(t.body, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
