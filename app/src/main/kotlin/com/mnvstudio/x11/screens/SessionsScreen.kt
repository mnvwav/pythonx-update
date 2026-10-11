package com.mnvstudio.x11.screens

import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.Session
import com.mnvstudio.x11.Sessions
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
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
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.copyText
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionsScreen(prefs: Prefs, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val list = Sessions.all(context)
    val active = prefs.activeSession
    var deleting by remember { mutableStateOf<Session?>(null) }
    var importErrors by remember { mutableStateOf<List<String>?>(null) }

    fun paste() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val problems = Sessions.import(context, text)
        if (problems.isEmpty()) Toast.makeText(context, "Session imported", Toast.LENGTH_SHORT).show() else importErrors = problems
    }

    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Sessions", "Saved setups you can launch in one tap") {
            RoundIcon(Icons.Filled.ContentPaste, "Import session from clipboard") { paste() }
            RoundIcon(Icons.Filled.Add, "New session") {
                val s = Sessions.fromCurrent(context, "Session ${list.size + 1}")
                Sessions.save(context, s, snapshot = false)
                navigate("session?${s.id}")
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                if (list.isEmpty()) {
                    SurfaceCard {
                        Icon(Icons.Filled.Layers, null, tint = Accent)
                        Text("No sessions yet", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text("A session remembers the display, input, Linux profile and the apps to start, so a whole setup opens in one tap. Start from one of these:",
                            color = Label, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        Sessions.templates(context).forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(ShapeM).background(CardHigh).clickable { Sessions.save(context, t, snapshot = false) }
                                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(t.summary(), color = Label, fontSize = 12.sp)
                                }
                                Icon(Icons.Filled.Add, "Add ${t.name}", tint = Accent)
                            }
                        }
                    }
                }
                list.forEach { s -> SessionCard(s, s.id == active, navigate, onDelete = { deleting = s }) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    deleting?.let { s -> Confirm("Delete \"${s.name}\"?", "Its history is deleted too. Your Linux files and apps aren't touched.", "Delete", danger = true,
        onDismiss = { deleting = null }) { Sessions.delete(context, s.id) } }
    importErrors?.let { errs ->
        AlertDialog(onDismissRequest = { importErrors = null }, containerColor = Card,
            title = { Text("Couldn't import", color = Ink) },
            text = { Text(errs.joinToString("\n") { "• $it" }, color = Label, fontSize = 14.sp) },
            confirmButton = { TextButton({ importErrors = null }) { Text("OK", color = Accent) } })
    }
}

@Composable
private fun SessionCard(s: Session, active: Boolean, navigate: (String) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(ShapeL).background(if (active) AccentSoft else Card)
        .border(1.dp, if (active) Accent else Line, ShapeL).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (active) { Spacer(Modifier.width(8.dp)); Pill("active", Live) }
                }
                Text(s.summary().ifEmpty { "Current settings" }, color = Label, fontSize = 13.sp)
            }
            Box {
                RoundIcon(Icons.Filled.MoreVert, "More for ${s.name}", tint = Label) { menu = true }
                DropdownMenu(menu, { menu = false }, containerColor = Card) {
                    DropdownMenuItem(text = { Text("Edit", color = Ink) }, leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Label) },
                        onClick = { menu = false; navigate("session?${s.id}") })
                    DropdownMenuItem(text = { Text("Duplicate", color = Ink) }, leadingIcon = { Icon(Icons.Filled.Add, null, tint = Label) },
                        onClick = { menu = false; Sessions.save(context, s.copy(id = java.util.UUID.randomUUID().toString().take(8), name = s.name + " copy"), snapshot = false) })
                    DropdownMenuItem(text = { Text("Copy as JSON", color = Ink) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = Label) },
                        onClick = { menu = false; copyText(context, s.toJson().toString(2), "Session copied") })
                    DropdownMenuItem(text = { Text("History & rollback", color = Ink) }, leadingIcon = { Icon(Icons.Filled.History, null, tint = Label) },
                        onClick = { menu = false; navigate("session-history?${s.id}") })
                    DropdownMenuItem(text = { Text("Delete", color = Bad) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Bad) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        if (s.lastStartupMs > 0) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = Faint, modifier = Modifier.size(14.dp))
            Text("Ready in ${"%.1f".format(s.lastStartupMs / 1000f)} s last time", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (active) "Open" else "Launch", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) {
            if (Sessions.launch(context, s)) navigate("display")
        }
    }
}

/** Edit a session: name, profile, settings, startup apps and commands, environment and rules. */
@Composable
fun SessionEditor(id: String, prefs: Prefs, back: () -> Unit) {
    val context = LocalContext.current
    val original = remember(id) { Sessions.get(context, id) }
    if (original == null) { Text("Session not found", color = Label, modifier = Modifier.padding(24.dp)); return }
    var name by remember { mutableStateOf(original.name) }
    var profile by remember { mutableStateOf(original.linuxProfile) }
    var settings by remember { mutableStateOf(original.settings) }
    val apps = remember { original.startupApps.toMutableStateList() }
    val commands = remember { original.startupCommands.toMutableStateList() }
    val env = remember { original.env.toList().toMutableStateList() }
    var restart by remember { mutableStateOf(original.restartCrashed) }
    var onLast by remember { mutableStateOf(original.onLastAppClosed) }
    var building by remember { mutableStateOf(false) }

    fun save() {
        Sessions.save(context, original.copy(name = name.ifBlank { "Session" }, linuxProfile = profile, settings = settings, startupApps = apps.toList(),
            startupCommands = commands.toList(), env = env.filter { it.first.isNotBlank() }.toMap(), restartCrashed = restart, onLastAppClosed = onLast))
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        back()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Edit session", onBack = back) { RoundIcon(Icons.Filled.Save, "Save", tint = Accent) { save() } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = ShapeM,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp))

                SectionTitle("Display & input")
                SurfaceCard(padding = 16.dp) {
                    Text(listOf("resolution", "dpi", "touchMode", "desktopMode").joinToString(" · ") { k -> "$k: ${settings.opt(k) ?: "default"}" },
                        color = Label, fontSize = 12.sp, fontFamily = Mono)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Use my current settings", Modifier.fillMaxWidth()) { settings = prefs.toJson(Session.SETTING_KEYS) }
                    Text("Change display and input in Settings, then tap this to save them into the session.", color = Faint, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }

                SectionTitle("Linux profile")
                Chips(Linux.profiles(context).ifEmpty { listOf("default") }, profile) { profile = it }

                SectionTitle("Start these apps")
                SurfaceCard(padding = 8.dp) {
                    Catalog.apps.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { if (a.id in apps) apps.remove(a.id) else apps.add(a.id) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(a.id in apps, { if (it) apps.add(a.id) else apps.remove(a.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Label))
                            Text(a.name, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (!Linux.isInstalled(context, a)) Text("not installed", color = Faint, fontSize = 11.sp)
                        }
                    }
                }

                SectionTitle("Startup commands")
                SurfaceCard(padding = 14.dp) {
                    if (commands.isEmpty()) Text("None. Commands run in order when the session launches.", color = Label, fontSize = 13.sp)
                    commands.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(ShapeM).background(CodeBg).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c, color = Ink, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            Box(Modifier.size(40.dp).clickable { commands.removeAt(i) }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Delete, "Remove", tint = Label) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Add command", Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { building = true }
                }

                SectionTitle("Environment variables")
                EnvEditor(env)

                SectionTitle("Rules")
                SurfaceCard(padding = 16.dp) {
                    ToggleRow("Restart apps that crash", "Up to 3 times a minute", restart) { restart = it }
                    Text("When the last app closes", color = Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                    Chips(listOf("nothing", "stop"), onLast, label = { if (it == "stop") "Stop the display" else "Keep the display on" }) { onLast = it }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Save session", Modifier.fillMaxWidth(), icon = Icons.Filled.Check) { save() }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (building) CommandBuilder(onDismiss = { building = false }) { commands.add(it); building = false }
}

/** Builds a startup command: pick an app or program, add arguments and variables, and see the result. */
@Composable
private fun CommandBuilder(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var program by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var vars by remember { mutableStateOf("") }
    var background by remember { mutableStateOf(true) }
    val command = listOf(vars.trim(), program.trim(), args.trim()).filter { it.isNotEmpty() }.joinToString(" ") + if (background) " &" else ""
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Card,
        title = { Text("Startup command", color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Program", color = Label, fontSize = 13.sp)
                Chips(Catalog.apps.map { it.command.substringBefore(' ') }.distinct(), program) { program = it }
                OutlinedTextField(program, { program = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("or type one") }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, color = Ink))
                OutlinedTextField(args, { args = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Arguments") }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, color = Ink))
                OutlinedTextField(vars, { vars = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Variables, e.g. LANG=hi_IN.UTF-8") }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, color = Ink))
                ToggleRow("Run in the background", "Don't wait for it to finish", background) { background = it }
                Text("Result", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(command.ifBlank { "—" }, color = Accent, fontFamily = Mono, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ if (program.isNotBlank()) onAdd(command) }, enabled = program.isNotBlank()) { Text("Add", color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Label) } },
    )
}

/** Editable list of NAME=value pairs. */
@Composable
fun EnvEditor(env: MutableList<Pair<String, String>>) {
    SurfaceCard(padding = 14.dp) {
        if (env.isEmpty()) Text("None set.", color = Label, fontSize = 13.sp)
        env.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(k, { env[i] = it.filter { c -> c.isLetterOrDigit() || c == '_' }.uppercase() to v }, Modifier.weight(0.42f), singleLine = true,
                    placeholder = { Text("NAME", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink), shape = ShapeM)
                Text("=", color = Label, modifier = Modifier.padding(horizontal = 6.dp))
                OutlinedTextField(v, { env[i] = k to it }, Modifier.weight(0.58f), singleLine = true,
                    placeholder = { Text("value", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink), shape = ShapeM)
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { env.removeAt(i) }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Delete, "Remove $k", tint = Label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Add variable", Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { env.add("" to "") }
    }
}

/** Earlier versions of a session: what changed, and going back to one. */
@Composable
fun SessionHistoryScreen(id: String, back: () -> Unit) {
    val context = LocalContext.current
    val current = Sessions.get(context, id)
    val versions = remember(Sessions.revision.intValue) { Sessions.history(context, id) }
    var confirm by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("History", current?.name, onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                if (current == null || versions.isEmpty()) Text("No earlier versions yet. Each save keeps the previous one here.", color = Label, modifier = Modifier.padding(top = 16.dp))
                versions.forEach { (time, old) ->
                    val changes = if (current != null) Sessions.diff(old, current) else emptyList()
                    SurfaceCard(Modifier.padding(top = 12.dp), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time)), color = Ink, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f))
                            Text("Restore", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { confirm = time }.padding(6.dp))
                        }
                        if (changes.isEmpty()) Text("Same as now", color = Faint, fontSize = 12.sp)
                        changes.forEach { (k, was, now) ->
                            Text(k, color = Label, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            Text("− $was", color = Bad, fontFamily = Mono, fontSize = 12.sp)
                            Text("+ $now", color = Live, fontFamily = Mono, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    confirm?.let { t -> Confirm("Restore this version?", "The current version is kept in history, so you can come back.", "Restore",
        onDismiss = { confirm = null }) { Sessions.rollback(context, id, t) } }
}

