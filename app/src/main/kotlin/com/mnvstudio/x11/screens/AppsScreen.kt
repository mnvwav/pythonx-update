package com.mnvstudio.x11.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.linux.LinuxApp
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Cyan
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Good
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.Violet

/** The built-in Linux: set it up, install and open apps, or run a command — all without leaving the app. */
@Composable
fun AppsScreen(prefs: Prefs, navigate: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf<String?>(null) }

    fun addLog(line: String) { log += line; if (log.size > 400) log.removeAt(0) }

    /** Starts the X server if needed, launches the app and switches to the display. */
    fun open(app: LinuxApp) {
        // A just-started server needs a moment before it accepts connections.
        val wait = if (XHolder.server == null) "sleep 1; " else ""
        XService.start(context)
        addLog("$ ${app.command}")
        Linux.run(context, wait + app.command, prefs.display, ::addLog) { code -> if (code != 0) addLog("${app.name} exited ($code)") }
        // Give it a moment to connect; if it fails right away, stay here so the error is visible in Output.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (XHolder.clients > 0) navigate("display") else addLog("Waiting for ${app.name} to connect… (check the messages above)") }, 2500)
    }

    fun install(app: LinuxApp, thenOpen: Boolean) {
        busy = app.name
        addLog("Installing ${app.name}…")
        Linux.installPackages(context, app.packages, prefs.display, ::addLog) { ok ->
            busy = null
            addLog(if (ok) "${app.name} installed" else "Couldn't install ${app.name}")
            if (ok && thenOpen) open(app)
        }
    }

    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.padding(start = 14.dp)) {
                Text("Linux apps", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Built-in Alpine Linux", color = Label, fontSize = 12.sp)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (!Linux.prootAvailable(context)) {
                    SurfaceCard {
                        Text("Linux support isn't included in this build", color = Bad, fontWeight = FontWeight.SemiBold)
                        Text("proot wasn't bundled. Rebuild the app with internet access so Gradle can download it (fetchProot).",
                            color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                when (Linux.state) {
                    Linux.State.READY -> {
                        SectionTitle("Apps")
                        val rev = Linux.revision
                        Catalog.apps.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { app ->
                                    val installed = remember(rev) { Linux.isInstalled(context, app.binary) }
                                    AppTile(app, installed, busy == app.name, Modifier.weight(1f)) {
                                        if (busy != null) return@AppTile
                                        if (installed) open(app) else install(app, thenOpen = true)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        SectionTitle("Run a command")
                        CommandRunner(prefs, ::addLog, onOpenDisplay = { XService.start(context); navigate("display") })
                    }
                    Linux.State.INSTALLING -> SurfaceCard {
                        Text("Setting up Linux", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(Linux.status, color = Label, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        LinearProgressIndicator(progress = { Linux.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Violet, trackColor = CardHigh)
                    }
                    else -> SurfaceCard {
                        Box(Modifier.size(52.dp).clip(ShapeM).background(Violet.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CloudDownload, null, tint = Violet)
                        }
                        Text("Set up Linux", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                        Text("Downloads Alpine Linux (about 4 MB) into the app. Apps you install add to that. No root and no other apps needed.",
                            color = Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                        if (Linux.state == Linux.State.FAILED) Text(Linux.status, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                        PrimaryButton("Download & set up", Modifier.fillMaxWidth(), icon = Icons.Filled.CloudDownload, enabled = Linux.prootAvailable(context)) {
                            Linux.install(context)
                        }
                    }
                }
                if (log.isNotEmpty()) {
                    SectionTitle("Output")
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true).padding(12.dp)) {
                        log.forEach { Text(it, color = if (it.startsWith("$")) Cyan else Label, fontFamily = Mono, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun iconFor(app: LinuxApp): ImageVector = when (app.binary) {
    "xterm" -> Icons.Filled.Terminal; "xcalc" -> Icons.Filled.Calculate; "xclock" -> Icons.Filled.Schedule
    "xeyes" -> Icons.Filled.Visibility; "xedit" -> Icons.Filled.Edit; "twm" -> Icons.Filled.Window; else -> Icons.Filled.Apps
}

@Composable
private fun AppTile(app: LinuxApp, installed: Boolean, working: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(ShapeM).background(Violet.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(iconFor(app), null, tint = Violet, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                when { working -> "Installing…"; installed -> "Open"; else -> "Install" },
                color = if (installed) Good else Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(CardHigh).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(app.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(app.description, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun CommandRunner(prefs: Prefs, log: (String) -> Unit, onOpenDisplay: () -> Unit) {
    val context = LocalContext.current
    var cmd by remember { mutableStateOf("") }
    fun run() {
        if (cmd.isBlank()) return
        log("$ $cmd")
        Linux.run(context, cmd, prefs.display, log) { code -> log("[exit $code]") }
        cmd = ""
    }
    SurfaceCard(padding = 16.dp) {
        OutlinedTextField(
            cmd, { cmd = it }, Modifier.fillMaxWidth(), singleLine = true, shape = ShapeM,
            placeholder = { Text("apk add xterm   ·   xterm &", color = Faint, fontFamily = Mono, fontSize = 13.sp) },
            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { run() }),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Run", Modifier.weight(1f), icon = Icons.Filled.PlayArrow) { run() }
            com.mnvstudio.x11.ui.SecondaryButton("Display", Modifier.weight(1f)) { onOpenDisplay() }
        }
        Text("Runs inside Alpine as root (in proot). X11 apps appear on display :${prefs.display}${if (XHolder.server == null) " once the server starts" else ""}.",
            color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
