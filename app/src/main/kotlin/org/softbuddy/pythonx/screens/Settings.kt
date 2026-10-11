package org.softbuddy.pythonx.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.term.Terms
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Inset
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.OnAccent
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.ScreenBar
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ShapeM
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.formatBytes
import java.io.File

private val LocalQuery = compositionLocalOf { "" }

private fun matches(q: String, vararg text: String) = q.isBlank() || text.any { it.contains(q.trim(), ignoreCase = true) }

private fun sizeOf(f: File) = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
fun SettingsScreen(prefs: Prefs, navigate: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val python = remember { Python(ctx.applicationContext) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var runtimeSize by remember { mutableLongStateOf(0L) }
    var packagesSize by remember { mutableLongStateOf(0L) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            packagesSize = sizeOf(python.sitePackages)
            runtimeSize = sizeOf(python.home) - packagesSize
            cacheSize = sizeOf(File(ctx.cacheDir, "pycache")) + sizeOf(File(python.home, ".cache"))
        }
    }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "3.0" }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding()) {
        ScreenBar("Settings", onBack)
        CompositionLocalProvider(LocalQuery provides query) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 4.dp), singleLine = true,
                    placeholder = { Text("Search settings") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = ShapeM,
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Clear search") } })

                Group(Icons.Filled.Person, "Profile") {
                    Row("Your name", "Used in greetings") {
                        OutlinedTextField(prefs.name, { prefs.name = it.take(30) }, Modifier.padding(start = 8.dp).fillMaxWidth(0.55f), singleLine = true, shape = ShapeS)
                    }
                    Choice("Experience", "Sets checking and run defaults", listOf("Beginner", "Intermediate", "Advanced"), prefs.level) { prefs.level = it; prefs.applyProfile() }
                    Choice("Goal", "Tunes the home screen", Prefs.GOALS, prefs.goal) { prefs.goal = it }
                }

                Group(Icons.Filled.Palette, "Appearance") {
                    Switch("Dark theme", "Code surfaces stay dark in both themes", prefs.dark) { prefs.dark = it }
                    Slider("Editor font size", "${prefs.fontSize} sp", prefs.fontSize, 10..24) { prefs.fontSize = it }
                    Switch("Line numbers", null, prefs.lineNumbers) { prefs.lineNumbers = it }
                    Switch("Highlight current line", "Band behind the line with the cursor", prefs.lineHighlight) { prefs.lineHighlight = it }
                    Switch("Show whitespace", "Dots for leading spaces, useful for indentation errors", prefs.showWhitespace) { prefs.showWhitespace = it }
                }

                Group(Icons.Filled.Edit, "Editor") {
                    Choice("Indent size", "Spaces inserted by the ⇥ key and auto-indent (PEP 8 uses 4)", listOf("2", "4", "8"), prefs.tabSize.toString()) { prefs.tabSize = it.toInt() }
                    Switch("Word wrap", "Wrap long lines instead of scrolling sideways", prefs.wordWrap) { prefs.wordWrap = it }
                    Switch("Auto-close brackets", "Typing ( inserts ) after the cursor", prefs.autoClose) { prefs.autoClose = it }
                    Switch("Autocomplete", "Keywords, built-ins and names from the file", prefs.autocomplete) { prefs.autocomplete = it }
                    Switch("Live error checking", "Syntax errors, undefined names and unused imports as you type", prefs.liveErrors) { prefs.liveErrors = it }
                    Switch("Auto-save", "Saves a moment after you stop typing", prefs.autosave) { prefs.autosave = it }
                    Switch("Python key bar", "Arrows and : ( ) [ ] { } \" # = above the keyboard", prefs.showKeyBar) { prefs.showKeyBar = it }
                    Switch("Keep screen on", "While a project is open", prefs.keepAwake) { prefs.keepAwake = it }
                }

                Group(Icons.Filled.PlayArrow, "Run") {
                    Switch("Clear terminal before each run", null, prefs.clearBeforeRun) { prefs.clearBeforeRun = it }
                    Switch("Development mode (-X dev)", "Extra runtime warnings: resource leaks, deprecated APIs, asyncio debugging", prefs.devMode) { prefs.devMode = it }
                }

                Group(Icons.Filled.Terminal, "Terminal") {
                    Slider("Terminal font size", "${prefs.termFontSize} sp · pinch in the terminal to zoom", prefs.termFontSize, 8..24) { prefs.termFontSize = it }
                    Switch("Keep running in background", "Programs continue with a notification until you stop them", prefs.termBackground) {
                        prefs.termBackground = it; Terms.changed(ctx)
                    }
                    val running = Terms.running().size
                    Action("Stop all running programs", if (running == 0) "None running" else "$running running", enabled = running > 0) { Terms.stopAll() }
                }

                Group(Icons.Filled.Memory, "Python runtime") {
                    Row("CPython ${Python.VERSION}", "Official python.org build · ${formatBytes(runtimeSize)} standard library") {}
                    Action("Installed packages", "${formatBytes(packagesSize)} · manage", enabled = true) { navigate("packages") }
                    Action("Clear caches", "${formatBytes(cacheSize)} of bytecode and pip downloads", enabled = cacheSize > 0 && busy == null) {
                        scope.launch {
                            withContext(Dispatchers.IO) { File(ctx.cacheDir, "pycache").deleteRecursively(); File(python.home, ".cache").deleteRecursively() }
                            tick++; toast("Caches cleared")
                        }
                    }
                    Action(if (busy == "repair") "Repairing…" else "Repair Python", "Reinstalls the standard library; keeps your packages", enabled = busy == null) { confirm = "repair" }
                    Action("Reset Python environment", "Removes every installed package and starts fresh", enabled = busy == null, danger = true) { confirm = "reset" }
                }

                Group(Icons.Filled.Info, "About") {
                    Row("PythonX", "Version $version · Python ${Python.VERSION} · Sora editor · Termux terminal") {}
                    Action("Show onboarding again", null, enabled = true) { prefs.resetOnboarding() }
                    Action("Reset all settings", "Editor, run and terminal back to defaults", enabled = true, danger = true) { confirm = "settings" }
                }
                if (query.isNotBlank()) Text("Showing settings matching “$query”", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    confirm?.let { what ->
        val (title, text, button) = when (what) {
            "repair" -> Triple("Repair Python?", "The standard library is unpacked again. Installed packages and your projects are kept.", "Repair")
            "reset" -> Triple("Reset Python environment?", "All installed packages are removed. Your projects are kept.", "Reset")
            else -> Triple("Reset all settings?", "Editor, run and terminal settings go back to defaults. Your projects and name are kept.", "Reset")
        }
        AlertDialog({ confirm = null }, title = { Text(title) }, text = { Text(text) },
            confirmButton = {
                TextButton({
                    confirm = null
                    when (what) {
                        "settings" -> prefs.resetSettings()
                        else -> {
                            busy = what
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (what == "reset") python.home.deleteRecursively()
                                        else File(python.home, ".version").delete()
                                        python.install()
                                        if (what == "reset") python.ensurePip()
                                    }.isSuccess
                                }
                                busy = null; tick++
                                toast(if (ok) "Python is ready" else "Something went wrong; try again")
                            }
                        }
                    }
                }) { Text(button, color = if (what == "repair") Accent else MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

/** Collects the rows of one group so a search can hide the whole card when nothing in it matches. */
private class GroupScope { var any by mutableStateOf(false) }
private val LocalGroup = compositionLocalOf { GroupScope() }

@Composable
private fun Group(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    val q = LocalQuery.current
    val scope = remember(q) { GroupScope() }
    Column(Modifier.padding(top = 18.dp)) {
        if (q.isBlank() || matches(q, title) || scope.any) androidx.compose.foundation.layout.Row(Modifier.padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = Accent)
            Text(title, Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, color = Accent, fontSize = 14.sp)
        }
        CompositionLocalProvider(LocalGroup provides scope, LocalQuery provides if (matches(q, title)) "" else q) {
            Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg), content = content)
        }
    }
}

@Composable
private fun visible(title: String, sub: String?): Boolean {
    val ok = matches(LocalQuery.current, title, sub.orEmpty())
    val group = LocalGroup.current
    if (ok) SideEffect { group.any = true }
    return ok
}

@Composable
private fun Row(title: String, sub: String?, trailing: @Composable () -> Unit) {
    if (!visible(title, sub)) return
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
        }
        trailing()
    }
}

@Composable
private fun Switch(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    if (!visible(title, sub)) return
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
        }
        androidx.compose.material3.Switch(value, onChange)
    }
}

@Composable
private fun Slider(title: String, valueText: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    if (!visible(title, valueText)) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
            Text(valueText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.material3.Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, sub: String?, options: List<String>, value: String, onPick: (String) -> Unit) {
    if (!visible(title, sub.orEmpty() + options.joinToString(" "))) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Ink, fontSize = 15.sp)
        if (sub != null) Text(sub, color = Label, fontSize = 12.sp)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val sel = o == value
                Text(o, Modifier.clip(Pill).background(if (sel) Accent else Inset).clickable { onPick(o) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (sel) OnAccent else Ink, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else null)
            }
        }
    }
}

@Composable
private fun Action(title: String, sub: String?, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    if (!visible(title, sub)) return
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable(enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = when { !enabled -> Faint; danger -> MaterialTheme.colorScheme.error; else -> Ink }, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Label, fontSize = 12.sp)
        }
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Faint)
    }
}
