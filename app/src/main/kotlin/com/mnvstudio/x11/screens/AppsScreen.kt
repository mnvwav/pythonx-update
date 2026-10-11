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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.linux.LinuxApp
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
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
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.Pill
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.TopBar

fun appIcon(id: String): ImageVector = when (id) {
    "xterm" -> Icons.Filled.Terminal; "xcalc" -> Icons.Filled.Calculate; "xclock" -> Icons.Filled.Schedule
    "xeyes" -> Icons.Filled.Visibility; "xedit" -> Icons.Filled.Edit; "twm" -> Icons.Filled.Window
    "xload" -> Icons.Filled.Insights; "xlogo" -> Icons.Filled.AutoAwesome; else -> Icons.Filled.Apps
}

/** The built-in Linux: set it up, then install, open and manage apps and packages. */
@Composable
fun AppsScreen(prefs: Prefs, navigate: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("Apps") }
    fun addLog(line: String) { log += line; if (log.size > 300) log.removeAt(0) }

    fun open(app: LinuxApp) { addLog("$ ${app.command}"); if (Launcher.open(context, app, ::addLog)) navigate("display") }

    fun install(packages: List<String>, label: String, then: () -> Unit = {}) {
        busy = label
        addLog("Installing $label…")
        Linux.installPackages(context, packages, ::addLog) { ok ->
            busy = null
            addLog(if (ok) "$label installed" else "Couldn't install $label. Diagnostics can test and repair Linux.")
            if (ok) then()
        }
    }

    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Linux apps", "Built-in Alpine Linux", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (!Linux.prootAvailable(context)) {
                    SurfaceCard {
                        Text("Linux support isn't included in this build", color = Bad, fontWeight = FontWeight.SemiBold)
                        Text("proot wasn't bundled. Rebuild with internet access so Gradle can download it (fetchProot task).",
                            color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                when (Linux.state) {
                    Linux.State.READY -> {
                        Chips(listOf("Apps", "Search", "Installed"), tab) { tab = it }
                        Spacer(Modifier.height(8.dp))
                        when (tab) {
                            "Search" -> SearchTab(busy) { name -> install(listOf(name), name) }
                            "Installed" -> InstalledTab(busy, ::addLog)
                            else -> CatalogTab(prefs, busy, onOpen = ::open, onInstall = { app -> install(app.packages, app.name) { open(app) } }, log = ::addLog)
                        }
                    }
                    Linux.State.INSTALLING -> SurfaceCard {
                        Text("Setting up Linux", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(Linux.status, color = Label, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        LinearProgressIndicator(progress = { Linux.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = CardHigh)
                    }
                    else -> SetupCard()
                }
                if (log.isNotEmpty()) {
                    SectionTitle("Output")
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true).padding(12.dp)) {
                        log.forEach { Text(it, color = if (it.startsWith("$")) Accent else Label, fontFamily = Mono, fontSize = 12.sp) }
                    }
                    Text("Something failing? Diagnostics tests and repairs Linux.", color = Accent, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable { navigate("diagnostics") })
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SetupCard() {
    val context = LocalContext.current
    SurfaceCard {
        Box(Modifier.size(52.dp).clip(ShapeM).background(AccentSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.CloudDownload, null, tint = Accent) }
        Text("Set up Linux", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text("Downloads Alpine Linux (about 4 MB) into the app. Apps you install add to that. No root and no other apps needed.",
            color = Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        if (Linux.state == Linux.State.FAILED) Text(Linux.status, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        PrimaryButton("Download & set up", Modifier.fillMaxWidth(), icon = Icons.Filled.CloudDownload, enabled = Linux.prootAvailable(context)) {
            Linux.install(context)
        }
    }
}

@Composable
private fun CatalogTab(prefs: Prefs, busy: String?, onOpen: (LinuxApp) -> Unit, onInstall: (LinuxApp) -> Unit, log: (String) -> Unit) {
    val context = LocalContext.current
    Catalog.apps.groupBy { it.category }.forEach { (category, apps) ->
        SectionTitle(category)
        Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)) {
            apps.forEachIndexed { i, app ->
                val installed = remember(Linux.revision) { Linux.isInstalled(context, app) }
                val working = busy == app.name
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = busy == null, role = Role.Button) { if (installed) onOpen(app) else onInstall(app) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(ShapeM).background(AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(appIcon(app.id), null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (app.id in prefs.autostart) { Spacer(Modifier.size(8.dp)); Pill("autostart", Live) }
                        }
                        Text(app.description, color = Label, fontSize = 12.sp)
                    }
                    Pill(when { working -> "Installing…"; installed -> "Open"; else -> "Install" }, if (installed) Live else Accent)
                    if (installed) Box {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MoreVert, "More for ${app.name}", tint = Label)
                        }
                        DropdownMenu(menu, { menu = false }, containerColor = Card) {
                            DropdownMenuItem(text = { Text("Add to home screen", color = Ink) }, leadingIcon = { Icon(Icons.Filled.PushPin, null, tint = Label) },
                                onClick = { menu = false; Launcher.pinShortcut(context, app) })
                            val auto = app.id in prefs.autostart
                            DropdownMenuItem(text = { Text(if (auto) "Don't start automatically" else "Start with the display", color = Ink) },
                                leadingIcon = { Icon(Icons.Filled.Schedule, null, tint = Label) },
                                onClick = { menu = false; prefs.autostart = if (auto) prefs.autostart - app.id else prefs.autostart + app.id })
                            DropdownMenuItem(text = { Text("Remove", color = Bad) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Bad) },
                                onClick = { menu = false; Linux.removePackages(context, app.packages, log) { log(if (it) "${app.name} removed" else "Couldn't remove ${app.name}") } })
                        }
                    }
                }
                if (i < apps.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(Line))
            }
        }
    }
    Text("These apps use the classic X11 features this server supports. Apps that need GTK or Qt (Firefox, GIMP) aren't supported yet.",
        color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}

@Composable
private fun SearchTab(busy: String?, onInstall: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var searching by remember { mutableStateOf(false) }
    fun go() { if (query.isBlank()) return; searching = true; Linux.search(context, query) { results = it; searching = false } }
    OutlinedTextField(
        query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = ShapeM,
        placeholder = { Text("Search Alpine packages, e.g. nano", color = Faint) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Label) },
        textStyle = TextStyle(color = Ink, fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go() }),
    )
    Spacer(Modifier.height(12.dp))
    when {
        searching -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = CardHigh)
        results?.isEmpty() == true -> Text("No packages found.", color = Label, fontSize = 14.sp)
        results != null -> Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)) {
            results!!.forEach { (name, desc) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Ink, fontFamily = Mono, fontSize = 14.sp)
                        Text(desc, color = Label, fontSize = 12.sp, maxLines = 2)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(CardHigh).clickable(enabled = busy == null) { onInstall(name) },
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Install $name", tint = Accent) }
                }
            }
        }
    }
}

@Composable
private fun InstalledTab(busy: String?, log: (String) -> Unit) {
    val context = LocalContext.current
    val packages = remember(Linux.revision) { Linux.installedPackages(context) }
    if (packages.isEmpty()) { Text("Nothing installed yet.", color = Label, fontSize = 14.sp); return }
    Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)) {
        packages.forEach { name ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Ink, fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (name != "alpine-baselayout" && name != "apk-tools" && name != "busybox" && name != "alpine-keys" && name != "musl-utils")
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = busy == null) {
                        Linux.removePackages(context, listOf(name), log) { log(if (it) "$name removed" else "Couldn't remove $name") }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Delete, "Remove $name", tint = Label) }
            }
        }
    }
}
