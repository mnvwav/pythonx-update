package org.softbuddy.pythonx.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.editor.CodeView
import org.softbuddy.pythonx.editor.EditorController
import org.softbuddy.pythonx.project.LocalHistory
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.RunConfig
import org.softbuddy.pythonx.project.isTextFile
import org.softbuddy.pythonx.project.safeName
import org.softbuddy.pythonx.python.Analyzer
import org.softbuddy.pythonx.python.Pip
import org.softbuddy.pythonx.python.Problem
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.python.Symbol
import org.softbuddy.pythonx.term.Terms
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.Bad
import org.softbuddy.pythonx.ui.CodeBg
import org.softbuddy.pythonx.ui.CodeFaint
import org.softbuddy.pythonx.ui.CodeText
import org.softbuddy.pythonx.ui.ConfirmDialog
import org.softbuddy.pythonx.ui.DividerColor
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.NameDialog
import org.softbuddy.pythonx.ui.OnAccent
import org.softbuddy.pythonx.ui.OnSpark
import org.softbuddy.pythonx.ui.Spark
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.PanelBg
import org.softbuddy.pythonx.ui.PanelHeader
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.ShapeM
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.SidePanel
import org.softbuddy.pythonx.ui.Warn
import org.softbuddy.pythonx.ui.clickableNoFocus
import java.io.File

/**
 * What Run executes for [project]: its entry file (or [file]) with the configured arguments, in that file's folder.
 * Returns null when there is nothing to run.
 */
fun runCommand(context: Context, prefs: Prefs, project: Project, file: String? = null): Pair<File, String>? {
    val target = file ?: project.entryFile() ?: return null
    val path = File(project.dir, target)
    val args = if (file == null || file == project.entryFile()) project.runConfig.args.trim() else ""
    val dev = if (prefs.devMode) "-X dev " else ""
    return path.parentFile!! to "python3 $dev${Pip.quote(path.path)}" + if (args.isNotEmpty()) " $args" else ""
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun IdeScreen(prefs: Prefs, project: Project, navigate: (String) -> Unit, onBack: () -> Unit, autoRun: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val python = remember { Python(context.applicationContext) }
    val analyzer = remember { Analyzer(context.applicationContext) }
    DisposableEffect(analyzer) { onDispose { Thread { analyzer.stop() }.start() } }

    var filesVersion by remember { mutableIntStateOf(0) }
    val files = remember(filesVersion) { project.files() }
    var current by rememberSaveable { mutableStateOf(project.mainFile()) }
    var tabs by rememberSaveable { mutableStateOf(listOfNotNull(project.mainFile())) }
    val currentFile = current?.let { File(project.dir, it) }
    val isPython = current?.endsWith(".py") == true || current?.endsWith(".pyw") == true
    val initialText = remember(current, filesVersion) { currentFile?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() } ?: "" }
    val editor = remember { EditorController() }
    var savedText by remember(current) { mutableStateOf(initialText) }
    var problems by remember(current) { mutableStateOf<List<Problem>>(emptyList()) }
    var symbols by remember(current) { mutableStateOf<List<Symbol>>(emptyList()) }
    var explorer by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var problemsOpen by remember { mutableStateOf(false) }
    var newIn by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    // The docked console: the program's terminal under the editor, resizable by dragging its handle.
    val runTerm = rememberTerm(prefs, "ide:" + project.dir.path)
    var consoleOpen by rememberSaveable { mutableStateOf(runTerm.session != null) }
    var consoleHeight by rememberSaveable { mutableFloatStateOf(300f) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val pythonReady = python.installed || Python.installProgress >= 1f

    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    fun save() {
        val f = currentFile ?: return
        val text = editor.text
        if (editor.view == null || text == savedText || !f.isFile) return
        f.writeText(text); savedText = text
    }

    LaunchedEffect(current) { current?.let { if (it !in tabs) tabs = tabs + it } }
    LaunchedEffect(filesVersion) { tabs = tabs.filter { File(project.dir, it).isFile }; if (current != null && current !in files) current = tabs.lastOrNull() ?: project.mainFile() }
    // Autosave shortly after typing stops, with a local-history snapshot every few minutes.
    LaunchedEffect(current, editor.version) {
        delay(600)
        if (prefs.autosave) withContext(Dispatchers.IO) { runCatching { saveOffMain(currentFile, editor, savedText)?.let { savedText = it } } }
        withContext(Dispatchers.IO) { runCatching { LocalHistory.snapshot(project, "Auto-save") } }
    }
    // Live analysis: syntax errors, warnings, undefined names and the outline.
    LaunchedEffect(current, editor.version, prefs.liveErrors, pythonReady) {
        if (!isPython || !pythonReady) { problems = emptyList(); symbols = emptyList(); editor.showProblems(emptyList()); return@LaunchedEffect }
        delay(450)
        val text = editor.text
        val result = withContext(Dispatchers.IO) { analyzer.analyze(current ?: "main.py", text) }
        if (editor.text != text) return@LaunchedEffect
        problems = if (prefs.liveErrors) result.problems else emptyList()
        symbols = result.symbols
        editor.showProblems(problems)
    }
    DisposableEffect(Unit) { onDispose { save() } }
    DisposableEffect(prefs.keepAwake) {
        val w = (context as? android.app.Activity)?.window
        if (prefs.keepAwake) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler {
        when {
            showSearch -> { showSearch = false; editor.stopSearch() }
            else -> { save(); onBack() }
        }
    }

    fun openFile(f: String) {
        if (!isTextFile(f)) { openExternally(context, File(project.dir, f)); return }
        if (f == current) return
        save()
        if (f !in tabs) tabs = tabs + f
        current = f
    }

    fun closeTab(f: String) {
        if (f == current) save()
        val i = tabs.indexOf(f)
        tabs = tabs - f
        if (f == current) current = tabs.getOrNull(i.coerceAtMost(tabs.lastIndex)) ?: tabs.lastOrNull()
    }

    /** Saves, snapshots and starts the program in its own terminal screen. */
    fun run(file: String? = null) {
        save()
        if (!pythonReady) { toast("Python is still being set up…"); return }
        val (cwd, command) = runCommand(context, prefs, project, file) ?: run { toast("Add a .py file to run"); return }
        Thread { runCatching { LocalHistory.snapshot(project, "Run", force = true) } }.start()
        val term = Terms.get(context, "ide:" + project.dir.path, (prefs.termFontSize * context.resources.displayMetrics.scaledDensity).toInt())
        val lesson = project.example
        term.onExit = { code, ms ->
            prefs.recordRun(code == 0, ms)
            if (code == 0 && lesson != null) prefs.markLearned(lesson)
        }
        term.startProgram(cwd, command, prefs.clearBeforeRun)
        consoleOpen = true
        term.showKeyboard()
    }
    LaunchedEffect(Unit) { if (autoRun) { delay(150); run() } }

    val errors = problems.count { it.error }
    val warnings = problems.size - errors
    val imeVisible = WindowInsets.isImeVisible

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding().imePadding()) {
            // Top bar.
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                Column(Modifier.weight(1f).clickable { explorer = true }.padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false))
                        Icon(Icons.Filled.KeyboardArrowDown, "Files", tint = Faint, modifier = Modifier.size(18.dp))
                    }
                    Text(when {
                        !pythonReady -> "Setting up Python… ${(Python.installProgress * 100).toInt()}%"
                        errors > 0 -> "$errors error${if (errors == 1) "" else "s"}" + if (warnings > 0) " · $warnings warning${if (warnings == 1) "" else "s"}" else ""
                        warnings > 0 -> "$warnings warning${if (warnings == 1) "" else "s"}"
                        else -> current ?: "No file open"
                    }, color = when { errors > 0 -> Bad; warnings > 0 -> Warn; else -> Faint }, fontSize = 12.sp, maxLines = 1)
                }
                IconButton({ editor.undo() }, enabled = editor.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (editor.canUndo) Ink else Faint) }
                IconButton({ editor.redo() }, enabled = editor.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (editor.canRedo) Ink else Faint) }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Ink) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Find and replace") }, { menu = false; showSearch = true }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                        DropdownMenuItem({ Text("Go to line") }, { menu = false; dialog = "goto" }, leadingIcon = { Icon(Icons.Filled.FormatIndentIncrease, null) })
                        if (isPython) DropdownMenuItem({ Text("Outline") }, { menu = false; dialog = "outline" }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) })
                        DropdownMenuItem({ Text("Toggle comment") }, { menu = false; editor.toggleComment() }, leadingIcon = { Text("#", fontFamily = Mono, color = Label) })
                        DropdownMenuItem({ Text("Duplicate line") }, { menu = false; editor.duplicateLine() }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                        HorizontalDivider()
                        if (isPython && current != project.entryFile()) DropdownMenuItem({ Text("Run this file") }, { menu = false; run(current) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
                        DropdownMenuItem({ Text("Run configuration") }, { menu = false; dialog = "runcfg" }, leadingIcon = { Icon(Icons.Filled.Tune, null) })
                        project.requirements?.let { req ->
                            DropdownMenuItem({ Text("Install requirements.txt") }, {
                                menu = false; save()
                                val term = Terms.get(context, "term:pip", (prefs.termFontSize * context.resources.displayMetrics.scaledDensity).toInt())
                                term.startProgram(project.dir, Pip(python).requirementsCommand(req), true)
                                navigate("term:pip")
                            }, leadingIcon = { Icon(Icons.Filled.Inventory2, null) })
                        }
                        DropdownMenuItem({ Text("Terminal here") }, { menu = false; save(); navigate("term:" + project.dir.path) }, leadingIcon = { Icon(Icons.Filled.Terminal, null) })
                        DropdownMenuItem({ Text("Local history") }, { menu = false; save(); navigate("history:" + project.dir.path) }, leadingIcon = { Icon(Icons.Filled.History, null) })
                        DropdownMenuItem({ Text("Share file") }, { menu = false; save(); currentFile?.let { shareFile(context, it) } }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                        DropdownMenuItem({ Text("Editor settings") }, { menu = false; save(); navigate("settings") }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                    }
                }
                // Run: the one accent button on the screen. Long-press runs just the open file.
                Box(Modifier.padding(start = 2.dp, end = 4.dp).clip(Pill).background(Spark)
                    .combinedClickable(onClick = { run() }, onLongClick = { if (isPython) run(current) })
                    .padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, "Run", tint = OnSpark, modifier = Modifier.size(20.dp))
                        Text("Run", color = OnSpark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            if (!pythonReady) LinearProgressIndicator({ Python.installProgress }, Modifier.fillMaxWidth().height(2.dp), color = Accent)

            if (tabs.size > 1 || (tabs.size == 1 && tabs.first() != project.entryFile())) TabStrip(tabs, current, { openFile(it) }, { closeTab(it) })
            if (isPython && symbols.isNotEmpty()) Breadcrumb(current.orEmpty(), symbols, editor.cursorLine) { dialog = "outline" }

            AnimatedVisibility(showSearch) { SearchBar(editor) { showSearch = false; editor.stopSearch() } }

            Box(Modifier.weight(1f).fillMaxWidth().background(CodeBg)) {
                if (current != null) CodeView(editor, project.dir.path + "/" + current + "#" + filesVersion, initialText, isPython, prefs, Modifier.fillMaxSize())
                else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No file open", color = CodeText, fontWeight = FontWeight.SemiBold)
                    TextButton({ newIn = ""; dialog = "newfile" }) { Text("Create a file", color = Accent) }
                }
            }

            if (problems.isNotEmpty()) ProblemsBar(problems, problemsOpen, { problemsOpen = !problemsOpen }) { p ->
                editor.goToLine(p.line, p.col - 1); problemsOpen = false
            }
            if (consoleOpen) Console(runTerm, consoleHeight, onResize = { consoleHeight = it },
                onRerun = { run() }, onExpand = { navigate("run:" + project.dir.path) }, onClose = { consoleOpen = false })
            else if (prefs.showKeyBar && imeVisible && current != null) KeyBar(editor, prefs.tabSize)
        }

        SidePanel(explorer, { explorer = false }) {
            FileExplorer(project, files, current,
                onOpen = { f -> explorer = false; openFile(f) },
                onNewFile = { dir -> newIn = dir; dialog = "newfile" },
                onNewFolder = { dir -> newIn = dir; dialog = "newfolder" },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                onSetEntry = { f -> project.runConfig = project.runConfig.copy(entry = f); filesVersion++; toast("Run now starts $f") },
                entry = project.entryFile())
        }
    }

    when (dialog) {
        "newfile" -> NameDialog(if (newIn.isEmpty()) "New file" else "New file in $newIn", "", { dialog = null }, label = "File name",
            confirm = "Create") { raw ->
            val name = safeName(raw).let { if (it.contains('.')) it else "$it.py" }
            val rel = if (newIn.isEmpty()) name else "$newIn/$name"
            val f = File(project.dir, rel)
            if (f.exists()) toast("$rel already exists")
            else { f.parentFile?.mkdirs(); f.writeText(""); dialog = null; filesVersion++; explorer = false; openFile(rel) }
        }
        "newfolder" -> NameDialog("New folder", "", { dialog = null }, label = "Folder name", confirm = "Create") { raw ->
            val rel = (if (newIn.isEmpty()) "" else "$newIn/") + safeName(raw)
            if (File(project.dir, rel).mkdirs()) { dialog = null; filesVersion++ } else toast("Couldn't create $rel")
        }
        "goto" -> NameDialog("Go to line", "", { dialog = null }, label = "Line (1–${initialText.count { it == '\n' } + 1})",
            keyboard = KeyboardType.Number, confirm = "Go") { v ->
            v.toIntOrNull()?.let { editor.goToLine(it) }; dialog = null
        }
        "outline" -> OutlineDialog(symbols, editor.cursorLine, { dialog = null }) { line -> dialog = null; editor.goToLine(line) }
        "runcfg" -> RunConfigDialog(project, { dialog = null }) { cfg -> project.runConfig = cfg; dialog = null; filesVersion++ }
    }
    renameTarget?.let { target ->
        NameDialog("Rename", File(target).name, { renameTarget = null }, confirm = "Rename") { raw ->
            val src = File(project.dir, target)
            val dest = File(src.parentFile, safeName(raw))
            when {
                dest.exists() -> toast("${dest.name} already exists")
                src.renameTo(dest) -> {
                    val newRel = dest.relativeTo(project.dir).path
                    if (current == target) { current = newRel }
                    tabs = tabs.map { if (it == target) newRel else it }
                    if (project.runConfig.entry == target) project.runConfig = project.runConfig.copy(entry = newRel)
                    renameTarget = null; filesVersion++
                }
                else -> toast("Couldn't rename")
            }
        }
    }
    deleteTarget?.let { target ->
        val f = File(project.dir, target)
        ConfirmDialog("Delete ${f.name}?", if (f.isDirectory) "The folder and everything in it will be deleted. Local history keeps copies of text files."
            else "Local history keeps a copy, so you can restore it.", "Delete", danger = true, onDismiss = { deleteTarget = null }) {
            runCatching { LocalHistory.snapshot(project, "Before delete", force = true) }
            f.deleteRecursively()
            tabs = tabs.filter { !it.startsWith(target) }
            if (current?.startsWith(target) == true) current = tabs.lastOrNull()
            deleteTarget = null; filesVersion++
        }
    }
}

/** Writes the editor text if it changed; returns the saved text, or null when nothing was written. */
private fun saveOffMain(file: File?, editor: EditorController, saved: String): String? {
    val f = file ?: return null
    if (editor.view == null || !f.isFile) return null
    val text = editor.text
    if (text == saved) return null
    f.writeText(text)
    return text
}

fun shareFile(context: Context, file: File) = runCatching {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (file.extension == "py") "text/x-python" else "text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${file.name}"))
}

private fun openExternally(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { Toast.makeText(context, "No app can open ${file.name}", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun TabStrip(tabs: List<String>, current: String?, onSelect: (String) -> Unit, onClose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { t ->
            val sel = t == current
            Row(Modifier.clip(ShapeS).background(if (sel) Accent.copy(alpha = 0.16f) else Color.Transparent).clickable { onSelect(t) }
                .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(File(t).name, color = if (sel) Accent else Label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                IconButton({ onClose(t) }, Modifier.size(26.dp)) { Icon(Icons.Filled.Close, "Close ${File(t).name}", Modifier.size(14.dp), tint = Faint) }
            }
        }
    }
}

/** "main.py › Game › play()" for the class/function containing the cursor. */
@Composable
private fun Breadcrumb(file: String, symbols: List<Symbol>, cursorLine: Int, onClick: () -> Unit) {
    val chain = remember(symbols, cursorLine) {
        val path = ArrayList<Symbol>()
        for (s in symbols) {
            if (s.line > cursorLine) break
            while (path.isNotEmpty() && path.last().depth >= s.depth) path.removeAt(path.lastIndex)
            path += s
        }
        path
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        Text(File(file).name, color = Faint, fontSize = 12.sp, fontFamily = Mono)
        chain.forEach { s ->
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Faint)
            Text(s.name.substringBefore('('), color = Label, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}

@Composable
private fun SearchBar(editor: EditorController, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, regex, matchCase) { editor.search(query, regex, matchCase); delay(250); tick++ }
    val count = remember(tick, editor.version) { editor.matchCount() }
    Column(Modifier.fillMaxWidth().background(PanelBg).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Find") }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 14.sp),
                supportingText = null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { editor.searchNext(); tick++ }),
                trailingIcon = { if (query.isNotEmpty()) Text(if (count == 0) "0" else "${editor.matchIndex() + 1}/$count", color = Faint, fontSize = 12.sp) })
            IconButton({ editor.searchPrevious(); tick++ }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match", tint = Ink) }
            IconButton({ editor.searchNext(); tick++ }) { Icon(Icons.Filled.KeyboardArrowDown, "Next match", tint = Ink) }
            IconButton(onClose) { Icon(Icons.Filled.Close, "Close search", tint = Ink) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(matchCase, { matchCase = it }); Text("Aa", color = Label, fontSize = 13.sp)
            Checkbox(regex, { regex = it }); Text(".*", color = Label, fontSize = 13.sp, fontFamily = Mono)
            Spacer(Modifier.weight(1f))
            TextButton({ replaceOpen = !replaceOpen }) { Text(if (replaceOpen) "Hide replace" else "Replace", color = Accent) }
        }
        if (replaceOpen) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(replacement, { replacement = it }, Modifier.weight(1f), placeholder = { Text("Replace with") }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 14.sp))
            TextButton({ editor.replace(replacement); tick++ }) { Text("One") }
            TextButton({ editor.replaceAll(replacement); tick++ }) { Text("All") }
        }
    }
}

/** Collapsed: a one-line summary. Expanded: every problem; tapping one jumps to it. */
@Composable
private fun ProblemsBar(problems: List<Problem>, open: Boolean, onToggle: () -> Unit, onJump: (Problem) -> Unit) {
    val first = problems.firstOrNull { it.error } ?: problems.first()
    Column(Modifier.fillMaxWidth().background(PanelBg)) {
        HorizontalDivider(color = DividerColor)
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (first.error) Icons.Filled.Error else Icons.Filled.Warning, null, Modifier.size(16.dp), tint = if (first.error) Bad else Warn)
            Text("  Line ${first.line}: ${friendly(first.message)}", color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (problems.size > 1) Text(" +${problems.size - 1}", color = Faint, fontSize = 12.sp)
            Icon(if (open) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, if (open) "Hide problems" else "Show problems", tint = Faint)
        }
        if (open) LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(problems) { p ->
                Row(Modifier.fillMaxWidth().clickable { onJump(p) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (p.error) Icons.Filled.Error else Icons.Filled.Warning, null, Modifier.size(16.dp).padding(top = 2.dp), tint = if (p.error) Bad else Warn)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(friendly(p.message), color = Ink, fontSize = 13.sp)
                        Text("line ${p.line}, column ${p.col}", color = Faint, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Adds a plain-language hint to the most common beginner errors. */
private fun friendly(message: String): String {
    val notDefined = Regex("'(\\w+)' is not defined").find(message)?.groupValues?.get(1)
    if (notDefined != null) {
        val guess = listOf("print", "input", "range", "len", "str", "int", "float", "list", "dict", "True", "False", "None", "return", "import")
            .firstOrNull { it != notDefined && editDistance(it.lowercase(), notDefined.lowercase()) <= 2 }
        return message + if (guess != null) ". Did you mean $guess?" else ". Check the spelling, or define it first."
    }
    return when {
        message.startsWith("expected ':'") -> "$message. Lines starting if, for, while, def and class end with a colon."
        message.startsWith("unexpected indent") -> "$message. This line is indented more than the block it belongs to."
        message.startsWith("expected an indented block") -> "$message. Indent the next line by ${4} spaces."
        message.startsWith("unterminated string") -> "$message. Add the closing quote."
        message.startsWith("'(' was never closed") || message.startsWith("'[' was never closed") -> "$message. Add the closing bracket."
        else -> message
    }
}

private fun editDistance(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]; dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return dp[b.length]
}

/** Python-friendly symbols above the keyboard. Long-press-free: everything is one tap. */
@Composable
private fun KeyBar(editor: EditorController, tabSize: Int) {
    val keys = listOf("⇥", ":", "(", ")", "[", "]", "{", "}", "\"", "'", "=", ".", ",", "_", "#", "+", "-", "*", "/", "<", ">", "!", "%", "&", "|", "\\", "@")
    Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF0D1612)), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            listOf("←" to { editor.moveLeft() }, "→" to { editor.moveRight() }, "↑" to { editor.moveUp() }, "↓" to { editor.moveDown() }).forEach { (k, action) ->
                Box(Modifier.width(40.dp).fillMaxHeight().clickableNoFocus(action), contentAlignment = Alignment.Center) {
                    Text(k, color = Color(0xFF5FD4A0), fontSize = 16.sp)
                }
            }
            keys.forEach { k ->
                Box(Modifier.width(38.dp).fillMaxHeight().clickableNoFocus { editor.insert(if (k == "⇥") " ".repeat(tabSize) else k) },
                    contentAlignment = Alignment.Center) {
                    Text(k, color = CodeText, fontFamily = Mono, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun OutlineDialog(symbols: List<Symbol>, cursorLine: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    AlertDialog(onDismiss, title = { Text("Outline") }, confirmButton = { TextButton(onDismiss) { Text("Close") } },
        text = {
            if (symbols.isEmpty()) Text("No classes or functions in this file yet.", color = Label)
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(symbols) { s ->
                    val here = s.line <= cursorLine && symbols.none { it.line in (s.line + 1)..cursorLine && it.depth <= s.depth }
                    Row(Modifier.fillMaxWidth().clip(ShapeS).background(if (here) Accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onJump(s.line) }.padding(start = (8 + s.depth * 16).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val (badge, color) = when (s.kind) { "class" -> "C" to Color(0xFFF2C66D); "method" -> "m" to Color(0xFF9C8CFF); else -> "f" to Color(0xFF5FD4A0) }
                        Text(badge, Modifier.width(18.dp), color = color, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 13.sp)
                        Text(s.name, Modifier.weight(1f), color = Ink, fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.line}", color = Faint, fontSize = 12.sp)
                    }
                }
            }
        })
}

@Composable
private fun RunConfigDialog(project: Project, onDismiss: () -> Unit, onSave: (RunConfig) -> Unit) {
    val py = remember { project.pythonFiles() }
    var entry by remember { mutableStateOf(project.entryFile().orEmpty()) }
    var args by remember { mutableStateOf(project.runConfig.args) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(onDismiss, title = { Text("Run configuration") },
        text = {
            Column {
                Text("File to run", color = Label, fontSize = 13.sp)
                Box {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(ShapeM).background(PanelBg).clickable { pick = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.ifEmpty { "No .py files" }, Modifier.weight(1f), color = Ink, fontFamily = Mono, fontSize = 14.sp)
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Faint)
                    }
                    DropdownMenu(pick, { pick = false }) { py.forEach { f -> DropdownMenuItem({ Text(f, fontFamily = Mono) }, { entry = f; pick = false }) } }
                }
                OutlinedTextField(args, { args = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Arguments") },
                    placeholder = { Text("--verbose data.csv") }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono))
                Text("Passed to sys.argv. Quote values with spaces.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton({ onSave(RunConfig(entry, args)) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** The project's folders and files as an expandable tree, with create, rename, delete and set-as-entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExplorer(
    project: Project, files: List<String>, current: String?, entry: String?,
    onOpen: (String) -> Unit, onNewFile: (String) -> Unit, onNewFolder: (String) -> Unit,
    onRename: (String) -> Unit, onDelete: (String) -> Unit, onSetEntry: (String) -> Unit,
) {
    // Folders that exist on disk, including empty ones.
    val dirs = remember(files) {
        project.dir.walkTopDown().onEnter { it == project.dir || !it.name.startsWith(".") && it.name != "__pycache__" }
            .filter { it.isDirectory && it != project.dir }.map { it.relativeTo(project.dir).path }.toSet()
    }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val entries = remember(files, dirs) {
        (files + dirs).sortedWith(compareBy<String> { it.split('/').dropLast(1).joinToString("/") }
            .thenBy { if (it in dirs) 0 else 1 }.thenBy { it.lowercase() })
    }
    // Sort as a tree: parents before children, folders before files at each level.
    val tree = remember(entries) {
        fun children(parent: String): List<String> = entries.filter { (File(it).parent ?: "") == parent }
            .sortedWith(compareBy<String> { if (it in dirs) 0 else 1 }.thenBy { File(it).name.lowercase() })
        val out = ArrayList<String>()
        fun walk(p: String) { children(p).forEach { out += it; if (it in dirs) walk(it) } }
        walk("")
        out
    }
    Column(Modifier.fillMaxSize().background(PanelBg).systemBarsPadding().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${files.size} file${if (files.size == 1) "" else "s"}", color = Faint, fontSize = 12.sp)
            }
            IconButton({ onNewFile("") }) { Icon(Icons.Filled.Add, "New file", tint = Ink) }
            IconButton({ onNewFolder("") }) { Icon(Icons.Filled.CreateNewFolder, "New folder", tint = Ink) }
        }
        PanelHeader("Files")
        LazyColumn(Modifier.weight(1f)) {
            items(tree.filter { path -> generateSequence(File(path).parent) { File(it).parent }.none { it in collapsed } }) { path ->
                val isDir = path in dirs
                val depth = path.count { it == '/' }
                val sel = path == current
                Box {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(ShapeM)
                        .background(if (sel) Accent.copy(alpha = 0.14f) else Color.Transparent)
                        .combinedClickable(onClick = {
                            if (isDir) collapsed = if (path in collapsed) collapsed - path else collapsed + path else onOpen(path)
                        }, onLongClick = { menuFor = path })
                        .padding(start = (12 + depth * 14).dp, end = 4.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when { isDir && path in collapsed -> Icons.Filled.Folder; isDir -> Icons.Filled.FolderOpen; else -> Icons.Filled.Description },
                            null, Modifier.size(18.dp), tint = when { isDir -> Color(0xFFF2C66D); path.endsWith(".py") -> Accent; else -> Label })
                        Text(File(path).name, Modifier.weight(1f).padding(start = 10.dp), color = if (sel) Accent else Ink, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (path == entry) Text("▶", color = Accent, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                        IconButton({ menuFor = path }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "Actions for ${File(path).name}", Modifier.size(16.dp), tint = Faint) }
                    }
                    DropdownMenu(menuFor == path, { menuFor = null }) {
                        if (isDir) {
                            DropdownMenuItem({ Text("New file here") }, { menuFor = null; onNewFile(path) })
                            DropdownMenuItem({ Text("New folder here") }, { menuFor = null; onNewFolder(path) })
                        } else if (path.endsWith(".py") && path != entry) DropdownMenuItem({ Text("Run this file by default") }, { menuFor = null; onSetEntry(path) })
                        DropdownMenuItem({ Text("Rename") }, { menuFor = null; onRename(path) })
                        DropdownMenuItem({ Text("Delete", color = Bad) }, { menuFor = null; onDelete(path) })
                    }
                }
            }
        }
        Text("▶ marks the file Run starts. Long-press for actions.", color = Faint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

/** The program's terminal docked under the editor: drag the handle to resize, expand for full screen. */
@Composable
private fun Console(
    term: org.softbuddy.pythonx.term.Term, heightDp: Float, onResize: (Float) -> Unit,
    onRerun: () -> Unit, onExpand: () -> Unit, onClose: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(Modifier.fillMaxWidth().height(heightDp.dp).background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0D1612))
            .draggable(androidx.compose.foundation.gestures.rememberDraggableState { d ->
                onResize((heightDp - with(density) { d.toDp().value }).coerceIn(140f, 900f))
            }, androidx.compose.foundation.gestures.Orientation.Vertical)
            .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(Pill).background(when {
                term.running -> Color(0xFF7EE2A8); (term.exitCode ?: 0) == 0 -> Color(0xFF6B8278); else -> Color(0xFFFF7A85)
            }))
            Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(Pill).background(Color(0xFF283A31)))
                Text(when {
                    term.running -> "Running"
                    term.exitCode == null -> "Console"
                    term.exitCode == 0 -> "Finished · " + formatMillis(term.finishedAt - term.startedAt)
                    else -> "Exited with code ${term.exitCode} · " + formatMillis(term.finishedAt - term.startedAt)
                }, color = CodeText, fontSize = 12.sp, fontFamily = Mono, maxLines = 1)
            }
            if (term.running) IconButton({ term.interrupt() }) { Text("^C", color = Color(0xFFFF7A85), fontFamily = Mono, fontWeight = FontWeight.Bold) }
            else IconButton(onRerun) { Icon(Icons.Filled.PlayArrow, "Run again", tint = Color(0xFFE9C46A)) }
            IconButton(onExpand) { Icon(Icons.Filled.KeyboardArrowUp, "Full screen", tint = CodeText) }
            IconButton(onClose) { Icon(Icons.Filled.Close, "Hide console", tint = CodeText) }
        }
        TerminalPane(term, Modifier.weight(1f).fillMaxWidth())
        ExtraKeys(term, compact = true)
    }
}
