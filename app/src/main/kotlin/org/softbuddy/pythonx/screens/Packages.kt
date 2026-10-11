package org.softbuddy.pythonx.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.python.POPULAR_PACKAGES
import org.softbuddy.pythonx.python.Package
import org.softbuddy.pythonx.python.Pip
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.term.Terms
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.Bad
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.ConfirmDialog
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Good
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.OnAccent
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.ScreenBar
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.Warn
import org.softbuddy.pythonx.ui.WarnBg
import java.io.File

/** Packages installed with pip itself; hidden from the list so it shows only what the user added. */
private val TOOLING = setOf("pip", "setuptools", "wheel")

@Composable
fun PackagesScreen(prefs: Prefs, navigate: (String) -> Unit, onBack: () -> Unit, embedded: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val python = remember { Python(context.applicationContext) }
    val pip = remember { Pip(python) }
    var refresh by remember { mutableIntStateOf(0) }
    val installed = remember(refresh) { pip.installed() }
    val installedNames = remember(installed) { installed.map { pip.normalize(it.name) }.toSet() }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Package?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var outdated by remember { mutableStateOf<Map<String, Pair<String, String>>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Package?>(null) }
    var menu by remember { mutableStateOf(false) }
    BackHandler(!embedded, onBack = onBack)

    /** Runs a pip command in the shared "pip" terminal and shows it live. */
    fun runPip(command: String) {
        val ensure = if (python.pipInstalled) "" else "python3 -m ensurepip --default-pip && "
        val term = Terms.get(context, "term:pip", (prefs.termFontSize * context.resources.displayMetrics.scaledDensity).toInt())
        term.startProgram(python.home, "sh -c " + Pip.quote(ensure + command), clear = true)
        navigate("term:pip")
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        searching = true; message = null; result = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { pip.lookup(q) } }
                .onSuccess { result = it; if (it == null) message = "No package called “$q” on PyPI. Names must match exactly." }
                .onFailure { message = "Couldn't reach PyPI. Check your connection." }
            searching = false
        }
    }

    val wheelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.endsWith(".whl") } ?: "package.whl"
            val file = File(context.cacheDir, name)
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { i -> file.outputStream().use { i.copyTo(it) } } }
            runPip(pip.installCommand(file.path))
        }
    }

    val subtitle = "${installed.count { it.name.lowercase() !in TOOLING }} installed · pip ${installed.firstOrNull { it.name == "pip" }?.version ?: "not set up"}"
    val actions: @Composable () -> Unit = {
        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Ink) }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Upgrade pip") }, { menu = false; runPip(pip.upgradeCommand("pip")) })
            DropdownMenuItem({ Text("Install a .whl file") }, { menu = false; wheelPicker.launch(arrayOf("*/*")) })
            DropdownMenuItem({ Text("Open pip terminal") }, { menu = false; navigate("term:" + python.home.path) })
        }
    }
    Column(Modifier.fillMaxSize().background(PageBg).then(if (embedded) Modifier.statusBarsPadding() else Modifier.systemBarsPadding())) {
        if (embedded) Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Packages", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Faint, fontSize = 13.sp)
            }
            actions()
        } else ScreenBar("Packages", onBack, subtitle = subtitle) { actions() }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (embedded) DockClearance else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Package name on PyPI, e.g. rich") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton({ query = ""; result = null; message = null }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
            }
            message?.let { m -> item { Text(m, color = Label, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) } }
            result?.let { p ->
                item { SectionTitle("On PyPI") }
                item {
                    PackageCard(p, installed = pip.normalize(p.name) in installedNames,
                        onInstall = { runPip(pip.installCommand(p.name)) }, onRemove = { removing = p })
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Installed", Modifier.weight(1f))
                    if (installed.any { it.name.lowercase() !in TOOLING }) TextButton({
                        checking = true
                        scope.launch {
                            outdated = runCatching { withContext(Dispatchers.IO) { pip.outdated() } }.getOrElse { message = "Couldn't check for updates: ${it.message}"; null }
                            checking = false
                        }
                    }, enabled = !checking && python.pipInstalled) {
                        if (checking) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.SystemUpdateAlt, null, Modifier.size(16.dp))
                        Text(" Check for updates", fontSize = 13.sp)
                    }
                }
            }
            val mine = installed.filter { it.name.lowercase() !in TOOLING }
            outdated?.let { o ->
                val upgradable = mine.filter { o.containsKey(it.name) || o.keys.any { k -> pip.normalize(k) == pip.normalize(it.name) } }
                item {
                    Row(Modifier.fillMaxWidth().clip(ShapeS).background(if (upgradable.isEmpty()) CardBg else WarnBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (upgradable.isEmpty()) "Everything is up to date." else "${upgradable.size} update${if (upgradable.size == 1) "" else "s"} available",
                            color = if (upgradable.isEmpty()) Good else Warn, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (upgradable.isNotEmpty()) TextButton({ runPip(pip.upgradeCommand(*upgradable.map { it.name }.toTypedArray())) }) { Text("Update all") }
                    }
                }
            }
            if (mine.isEmpty()) item {
                Text("Nothing installed yet. Search above, or pick a popular package below.", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            }
            items(mine, key = { "i:" + it.name }) { p ->
                val latest = outdated?.entries?.firstOrNull { pip.normalize(it.key) == pip.normalize(p.name) }?.value?.second
                PackageCard(p, installed = true, latest = latest,
                    onInstall = { runPip(pip.upgradeCommand(p.name)) }, onRemove = { removing = p })
            }

            val popular = POPULAR_PACKAGES.filter { pip.normalize(it.name) !in installedNames }
            if (popular.isNotEmpty()) {
                item { SectionTitle("Popular, work on Android", Modifier.padding(top = 8.dp)) }
                items(popular, key = { "p:" + it.name }) { p ->
                    PackageCard(p, installed = false, onInstall = { runPip(pip.installCommand(p.name)) }, onRemove = {})
                }
            }
            item {
                Text("Packages with compiled C extensions only install when they publish Android wheels. Pure-Python packages always work.",
                    color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
            }
        }
    }
    removing?.let { p ->
        ConfirmDialog("Remove ${p.name}?", "It will be uninstalled with pip. Projects that import it will stop working until you reinstall it.",
            "Remove", danger = true, onDismiss = { removing = null }) { removing = null; runPip(pip.uninstallCommand(p.name)) }
    }
    LaunchedEffect(Unit) { refresh++ }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(start = 4.dp, top = 4.dp), color = Label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun PackageCard(p: Package, installed: Boolean, latest: String? = null, onInstall: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.version.isNotEmpty()) Text("  ${p.version}", color = Accent, fontSize = 12.sp, fontFamily = Mono)
                    if (latest != null) Text(" → $latest", color = Warn, fontSize = 12.sp, fontFamily = Mono)
                }
                if (p.summary.isNotEmpty()) Text(p.summary, color = Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            when {
                latest != null -> Button(onInstall, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Update") }
                installed -> TextButton(onRemove) { Text("Remove", color = Bad) }
                else -> FilledTonalButton(onInstall, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Install") }
            }
        }
        if (p.needsCompiler && !installed) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = Warn)
            Text("  Only ships compiled wheels for other platforms; installing may fail on Android.", color = Warn, fontSize = 12.sp)
        }
    }
}
