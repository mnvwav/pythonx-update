package com.mnvstudio.x11.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Metrics
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.linux.LinuxManager
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Confirm
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.Warn
import com.mnvstudio.x11.ui.copyText
import java.text.DateFormat
import java.util.Date

/** Common frame for the Linux tool screens: title, scrolling body, and the manager's busy/message line. */
@Composable
private fun ToolFrame(title: String, subtitle: String? = null, back: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
                      content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title, subtitle, onBack = back, actions = actions)
        LinuxManager.busy?.let { b ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("$b…", color = Label, fontSize = 13.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(CircleShape), color = Accent, trackColor = CardHigh)
            }
        }
        LinuxManager.message?.let { m ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(ShapeM).background(AccentSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Dismiss", tint = Label, modifier = Modifier.size(18.dp).clickable { LinuxManager.message = null })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) { content(); Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Output(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(max = 300.dp).clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
        .verticalScroll(rememberScrollState(), reverseScrolling = true).padding(12.dp)) {
        lines.forEach { Text(it, color = Label, fontFamily = Mono, fontSize = 12.sp) }
    }
}

// ---------------------------------------------------------------- processes

/** Every process of X11 and its Linux with CPU and memory, and a way to stop them. */
@Composable
fun ProcessesScreen(back: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) { Metrics.start(context); onDispose { Metrics.stop() } }
    var stopping by remember { mutableStateOf<Metrics.Proc?>(null) }
    val me = android.os.Process.myPid()
    ToolFrame("Processes", "X11 and Linux, refreshed every 2 s", back) {
        val list = Metrics.processes
        val total = list.sumOf { it.rssMb.toDouble() }
        Text("${list.size} processes · ${"%.0f".format(total)} MB · ${"%.0f".format(Metrics.latest?.cpu ?: 0f)}% CPU", color = Label, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)) {
            list.forEach { p ->
                val app = p.pid == me
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (app) "X11 (this app)" else p.cmdline.substringAfterLast("-lc ").ifEmpty { p.name }.take(60), color = Ink, fontSize = 14.sp, maxLines = 1)
                        Text("pid ${p.pid} · ${"%.1f".format(p.cpu)}% CPU · ${"%.0f".format(p.rssMb)} MB · ${p.state}", color = Faint, fontSize = 11.sp, fontFamily = Mono)
                    }
                    if (!app) RoundIcon(Icons.Filled.Close, "Stop ${p.name}", tint = Label) { stopping = p }
                }
            }
        }
        Text("Android only shows apps their own processes, so other apps aren't listed.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
    stopping?.let { p -> Confirm("Stop ${p.name}?", "Unsaved work in that program is lost.", "Stop", danger = true, onDismiss = { stopping = null }) {
        if (!Metrics.kill(p.pid)) Toast.makeText(context, "Couldn't stop it", Toast.LENGTH_SHORT).show()
    } }
}

// ---------------------------------------------------------------- packages

/** Search and install any package, see details and dependencies, and the install history. */
@Composable
fun PackagesScreen(back: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val out = remember { mutableStateListOf<String>() }
    var tab by remember { mutableStateOf("Search") }
    ToolFrame("Packages", "Alpine apk", back) {
        Chips(listOf("Search", "Installed", "History"), tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            "Search" -> {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = ShapeM,
                    placeholder = { Text("Package name", color = Faint) }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Label) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { results = null; Linux.search(context, query) { results = it } }),
                    textStyle = TextStyle(color = Ink))
                results?.forEach { (name, desc) ->
                    Row(Modifier.fillMaxWidth().clickable { selected = name }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Ink, fontFamily = Mono, fontSize = 14.sp)
                            Text(desc, color = Label, fontSize = 12.sp, maxLines = 2)
                        }
                        Icon(Icons.Filled.Info, "Details", tint = Label)
                    }
                }
            }
            "Installed" -> Linux.installedPackages(context).forEach { name ->
                Row(Modifier.fillMaxWidth().clickable { selected = name }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Ink, fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Info, "Details", tint = Label)
                }
            }
            else -> {
                val h = LinuxManager.packageHistory(context)
                if (h.isEmpty()) Text("Nothing installed or removed yet.", color = Label)
                h.forEach { (t, action, ok, pk) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(if (action == "add") "+" else "−", color = if (action == "add") Live else Bad, fontFamily = Mono, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pk, color = Ink, fontFamily = Mono, fontSize = 13.sp)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t.toLongOrNull() ?: 0)) + if (ok != "ok") " · failed" else "",
                                color = if (ok == "ok") Faint else Bad, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        selected?.let { name -> PackageDetails(name, out) { selected = null } }
        Output(out)
    }
}

/** Metadata, dependencies and what installing would change (including conflicts), with install/remove. */
@Composable
private fun PackageDetails(name: String, out: MutableList<String>, close: () -> Unit) {
    val context = LocalContext.current
    var info by remember(name) { mutableStateOf<List<String>?>(null) }
    var plan by remember(name) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(name) {
        Linux.capture(context, "apk info -a ${Linux.quote(name)} 2>/dev/null | head -60") { _, l -> info = l }
        Linux.capture(context, "apk add --simulate ${Linux.quote(name)} 2>&1 | tail -25") { _, l -> plan = l }
    }
    val installed = name in Linux.installedPackages(context)
    SurfaceCard(Modifier.padding(top = 16.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Ink, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, "Close", tint = Label, modifier = Modifier.clickable(onClick = close))
        }
        Text("Details", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text((info ?: listOf("Loading…")).filter { it.isNotBlank() }.joinToString("\n").take(2500), color = Label, fontFamily = Mono, fontSize = 11.sp)
        Text("Installing would…", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        val p = plan ?: listOf("Checking…")
        val conflict = p.any { it.contains("conflict", true) || it.contains("unable to select", true) || it.startsWith("ERROR") }
        Text(p.joinToString("\n"), color = if (conflict) Bad else Label, fontFamily = Mono, fontSize = 11.sp)
        if (conflict) Text("apk reports a dependency problem; installing will fail until it's resolved.", color = Bad, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        if (installed) SecondaryButton("Remove", Modifier.fillMaxWidth(), color = Bad) {
            Linux.removePackages(context, listOf(name), { out += it }) { ok -> out += if (ok) "$name removed" else "Couldn't remove $name" }
        } else PrimaryButton("Install", Modifier.fillMaxWidth(), icon = Icons.Filled.Download) {
            Linux.installPackages(context, listOf(name), { out += it }) { ok -> out += if (ok) "$name installed" else "Couldn't install $name" }
        }
    }
}

// ---------------------------------------------------------------- profiles, snapshots, backup

@Composable
fun ProfilesScreen(back: () -> Unit) {
    val context = LocalContext.current
    val prefs = Prefs.of(context)
    val profiles = remember(Linux.revision) { Linux.profiles(context) }
    val sizes = remember(Linux.revision) { mutableStateListOf<Pair<String, Long>>() }
    LaunchedEffect(Linux.revision) {
        Thread { val s = profiles.map { it to LinuxManager.profileSize(context, it) }; android.os.Handler(android.os.Looper.getMainLooper()).post { sizes.clear(); sizes.addAll(s) } }.start()
    }
    var newName by remember { mutableStateOf("") }
    var cloning by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var snapLabel by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf<Long?>(null) }
    ToolFrame("Linux profiles", "Separate environments, each with its own apps and files", back) {
        profiles.forEach { name ->
            val active = name == prefs.linuxProfile
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(ShapeM).background(if (active) AccentSoft else Card).border(1.dp, if (active) Accent else Line, ShapeM)
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (active) { Spacer(Modifier.size(8.dp)); Pill("active", Live) }
                    }
                    Text(sizes.firstOrNull { it.first == name }?.let { formatSize(it.second) } ?: "…", color = Faint, fontSize = 12.sp)
                }
                if (!active) Text("Use", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { LinuxManager.switchTo(context, name) }.padding(8.dp))
                Text("Clone", color = Label, modifier = Modifier.clickable { cloning = name }.padding(8.dp))
                if (!active) Icon(Icons.Filled.Delete, "Delete $name", tint = Label, modifier = Modifier.clickable { deleting = name }.padding(8.dp))
            }
        }
        SectionTitle("New profile")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.weight(1f), singleLine = true, shape = ShapeM,
                placeholder = { Text("e.g. work", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, color = Ink))
            Spacer(Modifier.size(10.dp))
            SecondaryButton("Create", icon = Icons.Filled.Add, enabled = LinuxManager.validName(newName)) { LinuxManager.create(context, newName); newName = "" }
        }
        Text("Creates an empty profile and downloads a fresh Alpine into it.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        SectionTitle("Snapshots of \"${prefs.linuxProfile}\"")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(snapLabel, { snapLabel = it }, Modifier.weight(1f), singleLine = true, shape = ShapeM,
                placeholder = { Text("Label, e.g. before upgrade", color = Faint) }, textStyle = TextStyle(color = Ink))
            Spacer(Modifier.size(10.dp))
            SecondaryButton("Take", icon = Icons.Filled.PhotoCamera, enabled = Linux.state == Linux.State.READY) { LinuxManager.snapshot(context, snapLabel); snapLabel = "" }
        }
        val snaps = remember(Linux.revision) { LinuxManager.snapshots(context) }
        if (snaps.isEmpty()) Text("No snapshots yet. Take one before big changes so you can roll back.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        snaps.forEach { (t, label, size) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Ink, fontSize = 14.sp)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t)) + " · " + formatSize(size), color = Faint, fontSize = 12.sp)
                }
                Icon(Icons.Filled.Restore, "Restore $label", tint = Accent, modifier = Modifier.clickable { restoring = t }.padding(8.dp))
                Icon(Icons.Filled.Delete, "Delete $label", tint = Label, modifier = Modifier.clickable { LinuxManager.deleteSnapshot(context, t) }.padding(8.dp))
            }
        }
    }
    cloning?.let { from ->
        var to by remember { mutableStateOf("$from-copy") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { cloning = null }, containerColor = Card, title = { Text("Clone \"$from\"", color = Ink) },
            text = { OutlinedTextField(to, { to = it.lowercase() }, singleLine = true, label = { Text("New profile name") }, textStyle = TextStyle(fontFamily = Mono, color = Ink)) },
            confirmButton = { androidx.compose.material3.TextButton({ LinuxManager.clone(context, from, to); cloning = null }) { Text("Clone", color = Accent) } },
            dismissButton = { androidx.compose.material3.TextButton({ cloning = null }) { Text("Cancel", color = Label) } })
    }
    deleting?.let { n -> Confirm("Delete profile \"$n\"?", "Every app and file in it, and its snapshots, are deleted.", "Delete", danger = true,
        onDismiss = { deleting = null }) { LinuxManager.deleteProfile(context, n) } }
    restoring?.let { t -> Confirm("Restore this snapshot?", "The profile goes back to how it was then; later changes are lost (take a snapshot first to keep them).",
        "Restore", danger = true, onDismiss = { restoring = null }) { LinuxManager.restoreSnapshot(context, t) } }
}

/** Export the active profile to a file (optionally encrypted), or import one as a new profile. */
@Composable
fun BackupScreen(back: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("restored") }
    var importPassword by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) LinuxManager.export(context, uri, password)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) LinuxManager.import(context, uri, importName, importPassword)
    }
    ToolFrame("Backup & restore", "Profile \"${Linux.activeProfileName(context)}\"", back) {
        SectionTitle("Export")
        SurfaceCard(padding = 16.dp) {
            Text("Saves the whole profile (apps, packages, files) to a single file you can keep or move to another phone.", color = Label, fontSize = 13.sp)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, shape = ShapeM,
                label = { Text("Password (optional, encrypts the backup)") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), textStyle = TextStyle(color = Ink))
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Export backup", Modifier.fillMaxWidth(), icon = Icons.Filled.Upload, enabled = Linux.state == Linux.State.READY && LinuxManager.busy == null) {
                exporter.launch("x11-${Linux.activeProfileName(context)}-${System.currentTimeMillis() / 1000}" + if (password.isNotEmpty()) ".x11enc" else ".tar.gz")
            }
            if (password.isNotEmpty()) Text("Keep the password safe: an encrypted backup can't be opened without it.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        SectionTitle("Import")
        SurfaceCard(padding = 16.dp) {
            Text("Restores a backup (from X11, or any Alpine root filesystem .tar.gz) as a new profile.", color = Label, fontSize = 13.sp)
            OutlinedTextField(importName, { importName = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true, shape = ShapeM, label = { Text("New profile name") }, textStyle = TextStyle(fontFamily = Mono, color = Ink))
            OutlinedTextField(importPassword, { importPassword = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, shape = ShapeM,
                label = { Text("Password, if it's encrypted") }, visualTransformation = PasswordVisualTransformation(), textStyle = TextStyle(color = Ink))
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Choose a backup file", Modifier.fillMaxWidth(), icon = Icons.Filled.Download, enabled = LinuxManager.busy == null) { importer.launch(arrayOf("*/*")) }
        }
    }
}

// ---------------------------------------------------------------- environment & users

/** Profile-wide environment variables, startup script, shell profile and the user apps run as. */
@Composable
fun EnvironmentScreen(navigate: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = Prefs.of(context)
    val env = remember(Linux.revision) { Linux.userEnv(context).toList().toMutableStateList() }
    ToolFrame("Environment", "Profile \"${Linux.activeProfileName(context)}\"", back, actions = {
        RoundIcon(Icons.Filled.Save, "Save variables", tint = Accent) {
            Linux.saveUserEnv(context, env.filter { it.first.isNotBlank() }.toMap()); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }) {
        SectionTitle("Variables for every program")
        EnvEditor(env)
        Text("Saved in /etc/x11-env. Sessions can add their own on top.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        SectionTitle("Scripts")
        NavCard(Icons.Filled.Refresh, "Startup script", "/etc/x11-startup.sh runs each time the display starts") {
            val f = com.mnvstudio.x11.linux.LinuxManager.startupScript(context)
            if (!f.exists()) runCatching { f.writeText("#!/bin/sh\n# Runs when the X11 display starts. Example:\n# xsetroot -solid '#202028'\n") }
            navigate("editor?/etc/x11-startup.sh")
        }
        Spacer(Modifier.height(10.dp))
        NavCard(Icons.Filled.Refresh, "Shell profile", "~/.profile: aliases, PATH, prompt") {
            val f = java.io.File(Linux.rootfs(context), "root/.profile")
            if (!f.exists()) runCatching { f.writeText("# Read by login shells. Examples:\n# alias ll='ls -l'\n# export PATH=\"\$HOME/bin:\$PATH\"\n") }
            navigate("editor?/root/.profile")
        }
        SectionTitle("Run apps as")
        UsersCard(prefs)
    }
}

@Composable
private fun UsersCard(prefs: Prefs) {
    val context = LocalContext.current
    val users = remember(Linux.revision) { LinuxManager.users(context) }
    var newUser by remember { mutableStateOf("") }
    val out = remember { mutableStateListOf<String>() }
    SurfaceCard(padding = 16.dp) {
        Chips(listOf("root") + users, prefs.linuxUser) { prefs.linuxUser = it }
        Text("root is simplest. A normal user is safer for everyday apps; package installs always run as root.", color = Faint, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newUser, { newUser = it.lowercase().filter { c -> c.isLetterOrDigit() } }, Modifier.weight(1f), singleLine = true, shape = ShapeM,
                placeholder = { Text("new user name", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, color = Ink))
            Spacer(Modifier.size(10.dp))
            SecondaryButton("Add", icon = Icons.Filled.Add, enabled = newUser.length >= 2) {
                val u = newUser
                Linux.run(context, "adduser -D -s /bin/sh $u && echo created $u", 0, onOutput = { out += it }) { Linux.bump() }
                newUser = ""
            }
        }
        Output(out)
    }
}

// ---------------------------------------------------------------- maintenance

/** Integrity check and repair, cleanup, offline package cache, package sources and trusted keys. */
@Composable
fun MaintenanceScreen(back: () -> Unit) {
    val context = LocalContext.current
    val out = remember { mutableStateListOf<String>() }
    var sizes by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    LaunchedEffect(Linux.revision) {
        Thread { val s = LinuxManager.cleanable(context); android.os.Handler(android.os.Looper.getMainLooper()).post { sizes = s } }.start()
    }
    var repos by remember(Linux.revision) { mutableStateOf(LinuxManager.repositories(context).joinToString("\n")) }
    ToolFrame("Maintenance", "Profile \"${Linux.activeProfileName(context)}\"", back) {
        SectionTitle("Integrity")
        SurfaceCard(padding = 16.dp) {
            Text("Checks every installed file against its package and lists anything changed or missing.", color = Label, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Check", Modifier.weight(1f), icon = Icons.Filled.Search) {
                    out.clear(); out += "$ apk audit --system"
                    Linux.run(context, "apk audit --system 2>&1 | head -200; echo '[done]'", 0, onOutput = { out += it })
                }
                SecondaryButton("Repair", Modifier.weight(1f), icon = Icons.Filled.Check) {
                    out.clear(); out += "$ apk fix"
                    Linux.run(context, "apk fix 2>&1; echo '[done]'", 0, onOutput = { out += it }) { Linux.bump() }
                }
            }
            Output(out)
        }
        SectionTitle("Clean up")
        SurfaceCard(padding = 8.dp) {
            (sizes ?: emptyList()).forEach { (what, size) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(what, color = Ink, fontSize = 14.sp)
                        Text(formatSize(size), color = Faint, fontSize = 12.sp)
                    }
                    if (size > 0) Icon(Icons.Filled.CleaningServices, "Clean $what", tint = Accent, modifier = Modifier.clickable { LinuxManager.clean(context, what) }.padding(8.dp))
                }
            }
            if (sizes == null) Text("Measuring…", color = Faint, modifier = Modifier.padding(8.dp))
        }
        SectionTitle("Offline packages")
        SurfaceCard(padding = 16.dp) {
            ToggleRow("Keep downloaded packages", "Lets you reinstall packages without internet; uses more space", LinuxManager.offlineCache(context)) {
                LinuxManager.setOfflineCache(context, it)
            }
        }
        SectionTitle("Package sources")
        SurfaceCard(padding = 16.dp) {
            OutlinedTextField(repos, { repos = it }, Modifier.fillMaxWidth(), shape = ShapeM, minLines = 3,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Ink))
            Text("One per line: a mirror URL, or a local folder like /root/repo (start a line with # to turn it off). Local repositories need " +
                "signed indexes, or install with apk add --allow-untrusted.", color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Save sources", Modifier.weight(1f), icon = Icons.Filled.Save) { LinuxManager.saveRepositories(context, repos.lines()); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
                SecondaryButton("Copy", Modifier.weight(1f), icon = Icons.Filled.ContentCopy) { copyText(context, repos) }
            }
            Text("Trusted signing keys", color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            LinuxManager.trustedKeys(context).forEach { Text(it, color = Label, fontFamily = Mono, fontSize = 11.sp) }
        }
    }
}

// ---------------------------------------------------------------- developer tools

/** Installs a development toolkit and works with Git repositories in ~/projects. */
@Composable
fun DevToolsScreen(navigate: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val out = remember { mutableStateListOf<String>() }
    var url by remember { mutableStateOf("") }
    val kits = listOf(
        "Git" to listOf("git", "openssh-client"), "C/C++" to listOf("build-base", "gdb", "cmake"),
        "Python" to listOf("python3", "py3-pip"), "Node.js" to listOf("nodejs", "npm"), "Editors" to listOf("nano", "vim"),
    )
    val repos = remember(Linux.revision) { java.io.File(Linux.rootfs(context), "root/projects").listFiles()?.filter { java.io.File(it, ".git").isDirectory }?.map { it.name } ?: emptyList() }
    fun git(cmd: String) { out += "$ $cmd"; Linux.run(context, cmd + " 2>&1", 0, onOutput = { out += it }) { Linux.bump() } }
    ToolFrame("Developer tools", null, back) {
        SectionTitle("Toolkits")
        kits.forEach { (name, pk) ->
            val have = remember(Linux.revision) { pk.first().let { p -> p in Linux.installedPackages(context) } }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = Ink, fontSize = 15.sp)
                    Text(pk.joinToString(" "), color = Faint, fontFamily = Mono, fontSize = 11.sp)
                }
                if (have) Pill("installed", Live) else Text("Install", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                    Linux.installPackages(context, pk, { out += it }) { ok -> out += if (ok) "$name ready" else "$name failed" }
                }.padding(8.dp))
            }
        }
        SectionTitle("Git")
        SurfaceCard(padding = 16.dp) {
            OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, shape = ShapeM,
                placeholder = { Text("https://github.com/user/repo.git", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink))
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Clone into ~/projects", Modifier.fillMaxWidth(), icon = Icons.Filled.Download, enabled = url.startsWith("http") || url.startsWith("git@")) {
                git("mkdir -p ~/projects && cd ~/projects && git clone ${Linux.quote(url)}"); url = ""
            }
            repos.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r, color = Ink, fontFamily = Mono, modifier = Modifier.weight(1f))
                    Text("status", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { git("cd ~/projects/${Linux.quote(r)} && git status -sb && git log --oneline -5") }.padding(6.dp))
                    Text("pull", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { git("cd ~/projects/${Linux.quote(r)} && git pull") }.padding(6.dp))
                    Text("files", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { navigate("files?/root/projects/$r") }.padding(6.dp))
                }
            }
            if (repos.isEmpty()) Text("No repositories yet.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Output(out)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Open terminal", Modifier.fillMaxWidth()) { navigate("console") }
    }
}

