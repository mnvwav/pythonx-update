package org.softbuddy.pythonx.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.python.QuickResult
import org.softbuddy.pythonx.python.QuickRun
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.CodeBg
import org.softbuddy.pythonx.ui.CodeFaint
import org.softbuddy.pythonx.ui.CodePrompt
import org.softbuddy.pythonx.ui.CodeText
import org.softbuddy.pythonx.ui.DividerColor
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.OnSpark
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.ShapeM
import org.softbuddy.pythonx.ui.ShapeXL
import org.softbuddy.pythonx.ui.Spark

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    PROJECTS("Projects", Icons.Filled.FolderCopy),
    LEARN("Learn", Icons.AutoMirrored.Filled.MenuBook),
    PACKAGES("Packages", Icons.Filled.Inventory2),
}

/** Top-level screens with the floating dock; the centre button opens Quick Run from anywhere. */
@Composable
fun Shell(prefs: Prefs, tab: Tab, onTab: (Tab) -> Unit, navigate: (String) -> Unit) {
    var quickRun by remember { mutableStateOf(false) }
    var quickSeed by remember { mutableStateOf("") }
    BackHandler(tab != Tab.HOME && !quickRun) { onTab(Tab.HOME) }
    Box(Modifier.fillMaxSize().background(PageBg)) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
            when (t) {
                Tab.HOME -> HomeTab(prefs, navigate, onTab = onTab, onQuickRun = { seed -> quickSeed = seed; quickRun = true })
                Tab.PROJECTS -> ProjectsTab(navigate)
                Tab.LEARN -> LearnTab(prefs, navigate)
                Tab.PACKAGES -> PackagesScreen(prefs, navigate, onBack = { onTab(Tab.HOME) }, embedded = true)
            }
        }
        Dock(tab, onTab, Modifier.align(Alignment.BottomCenter)) { quickSeed = ""; quickRun = true }
        AnimatedVisibility(quickRun, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            QuickRunSheet(quickSeed) { quickRun = false }
        }
    }
}

/** Bottom padding that keeps scrolling content clear of the dock. */
val DockClearance = 112.dp

@Composable
private fun Dock(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier, onRun: () -> Unit) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().height(64.dp).shadow(18.dp, ShapeXL).clip(ShapeXL)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, DividerColor, ShapeXL).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DockItem(Tab.HOME, tab, onTab)
            DockItem(Tab.PROJECTS, tab, onTab)
            Spacer(Modifier.width(68.dp))
            DockItem(Tab.LEARN, tab, onTab)
            DockItem(Tab.PACKAGES, tab, onTab)
        }
        // The raised Quick Run button: the one yellow thing on every top-level screen.
        Box(Modifier.offset(y = (-18).dp).size(62.dp).shadow(12.dp, Pill).clip(Pill).background(Spark).clickable(onClick = onRun),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Quick Run", tint = OnSpark, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockItem(t: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val sel = t == current
    val color by animateColorAsState(if (sel) Accent else Faint, tween(200), label = "dock")
    Column(Modifier.weight(1f).fillMaxHeight().clip(ShapeM)
        .clickable(remember { MutableInteractionSource() }, null) { onTab(t) }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.clip(Pill).background(if (sel) Accent.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp)) {
            Icon(t.icon, t.label, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(t.label, color = color, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Results kept for the app's lifetime, so closing and reopening Quick Run keeps the session's history. */
private val QuickHistory = mutableStateListOf<QuickResult>()

/** A REPL in a sheet: one snippet at a time, results stacked like a notebook, variables kept between snippets. */
@Composable
fun QuickRunSheet(seed: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { QuickRun.get(context) }
    var code by remember { mutableStateOf(seed) }
    var running by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(QuickHistory.size) { if (QuickHistory.isNotEmpty()) list.animateScrollToItem(QuickHistory.lastIndex + 1) }
    BackHandler(onBack = onClose)

    fun submit() {
        val snippet = code.trimEnd()
        if (snippet.isBlank() || running) return
        running = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { session.run(snippet) }
            QuickHistory += r
            code = ""
            running = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).align(Alignment.BottomCenter).clip(ShapeXL)
            .clickable(remember { MutableInteractionSource() }, null) {}.background(CodeBg).imePadding().navigationBarsPadding()) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(40.dp, 4.dp).clip(Pill).background(Color(0xFF283A31)))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(">>>", color = CodePrompt, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Quick Run", color = CodeText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Variables persist · Enter adds a line", color = CodeFaint, fontSize = 12.sp, maxLines = 1)
                }
                TextButton({ session.reset(); QuickHistory.clear() }) {
                    Icon(Icons.Filled.RestartAlt, null, tint = CodeFaint, modifier = Modifier.size(18.dp)); Text(" Reset", color = CodeFaint, fontSize = 13.sp)
                }
                IconButton(onClose) { Icon(Icons.Filled.Close, "Close Quick Run", tint = CodeText) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                if (QuickHistory.isEmpty()) item {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Try:", color = CodeFaint, fontSize = 13.sp)
                        listOf("2 ** 100", "import math; math.factorial(20)", "sorted('python', reverse=True)",
                            "from datetime import date; (date(2030, 1, 1) - date.today()).days").forEach { ex ->
                            Text(ex, color = Accent, fontFamily = Mono, fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 5.dp).clickable { code = ex })
                        }
                    }
                }
                items(QuickHistory) { r -> QuickResultView(r) { code = r.code } }
                item { Spacer(Modifier.height(4.dp)) }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0D1612)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(">>>", color = CodePrompt, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp).align(Alignment.Top).padding(top = 10.dp))
                BasicTextField(code, { code = it }, Modifier.weight(1f).padding(vertical = 10.dp).focusRequester(focus),
                    textStyle = TextStyle(color = CodeText, fontFamily = Mono, fontSize = 15.sp), cursorBrush = SolidColor(CodePrompt),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(), maxLines = 8,
                    decorationBox = { inner -> if (code.isEmpty()) Text("Type Python…", color = CodeFaint, fontFamily = Mono, fontSize = 15.sp); inner() })
                Box(Modifier.padding(start = 8.dp).size(46.dp).clip(Pill).background(if (code.isBlank()) Color(0xFF283A31) else CodePrompt)
                    .clickable(enabled = code.isNotBlank() && !running) { submit() }, contentAlignment = Alignment.Center) {
                    if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF2A1F05))
                    else Icon(Icons.Filled.PlayArrow, "Run snippet", tint = if (code.isBlank()) CodeFaint else Color(0xFF2A1F05))
                }
            }
        }
    }
}

@Composable
private fun QuickResultView(r: QuickResult, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.clickable(onClick = onEdit)) {
            Text(">>> ", color = CodePrompt.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 14.sp)
            Text(highlightPython(r.code), color = CodeText, fontFamily = Mono, fontSize = 14.sp)
        }
        SelectionContainer {
            Column(Modifier.padding(start = 4.dp, top = 2.dp)) {
                if (r.out.isNotEmpty()) Text(r.out.trimEnd(), color = Color(0xFFC8D8CF), fontFamily = Mono, fontSize = 13.sp)
                r.value?.let { Text(it, color = Color(0xFF7EE2A8), fontFamily = Mono, fontSize = 13.sp) }
                r.error?.let { Text(it, color = Color(0xFFFF7A85), fontFamily = Mono, fontSize = 13.sp) }
            }
        }
        Text(formatMillis(r.millis), color = CodeFaint.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
