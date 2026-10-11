package org.softbuddy.pythonx.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.project.LocalHistory
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.lineDiff
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.CodeBg
import org.softbuddy.pythonx.ui.CodeFaint
import org.softbuddy.pythonx.ui.CodeText
import org.softbuddy.pythonx.ui.ConfirmDialog
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.ScreenBar
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ago
import java.text.DateFormat
import java.util.Date

private val Removed = Color(0xFFFF8A8A)
private val Added = Color(0xFF8FDCA0)

/** Every automatic snapshot of a project; open one to compare it with the current files and restore it. */
@Composable
fun HistoryScreen(project: Project, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val snaps by produceState(emptyList<Long>(), version) { value = withContext(Dispatchers.IO) { LocalHistory.snapshots(project) } }
    var selected by remember { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    BackHandler { if (selected != null) selected = null else onBack() }

    Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding()) {
        val at = selected
        ScreenBar(if (at != null) fmt.format(Date(at)) else "Local history", { if (at != null) selected = null else onBack() }, subtitle = project.name) {
            if (at != null) TextButton({ confirm = at }) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Restore") }
        }
        if (at == null) LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Snapshots are saved automatically every few minutes while you edit, and every time you press Run. The last 60 are kept.",
                    color = Label, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (snaps.isEmpty()) item { Text("No snapshots yet. Edit a file and they'll appear here.", color = Faint) }
            items(snaps) { s ->
                val changed by produceState<Int?>(null, s, version) {
                    value = withContext(Dispatchers.IO) {
                        val then = LocalHistory.read(project, s); val now = LocalHistory.current(project)
                        (then.keys + now.keys).count { then[it] != now[it] }
                    }
                }
                Row(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).clickable { selected = s }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(fmt.format(Date(s)), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(LocalHistory.label(project, s).ifEmpty { "Snapshot" } + " · " + ago(s) + " · " + when (changed) {
                            null -> "…"; 0 -> "same as now"; 1 -> "1 file differs"; else -> "$changed files differ"
                        }, color = Faint, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Faint)
                }
            }
        } else {
            val diff by produceState<List<Pair<String, List<String>>>?>(null, at) {
                value = withContext(Dispatchers.IO) {
                    val then = LocalHistory.read(project, at); val now = LocalHistory.current(project)
                    (then.keys + now.keys).sorted().filter { then[it] != now[it] }.map { f ->
                        val status = when { f !in now -> " (deleted since)"; f !in then -> " (added since)"; else -> "" }
                        (f + status) to lineDiff(then[f].orEmpty(), now[f].orEmpty())
                    }
                }
            }
            Text("Red lines are only in the snapshot; green lines are only in your current files.", color = Label, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            val d = diff
            when {
                d == null -> Text("Comparing…", color = Faint, modifier = Modifier.padding(16.dp))
                d.isEmpty() -> Text("No differences from the current files.", color = Faint, modifier = Modifier.padding(16.dp))
                else -> SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize().background(CodeBg).horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(12.dp)) {
                        d.forEach { (file, lines) ->
                            item { Text(file, color = Accent, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                            items(lines) { l ->
                                Text(buildAnnotatedString {
                                    when {
                                        l.startsWith("- ") -> withStyle(SpanStyle(color = Removed, background = Removed.copy(alpha = 0.08f))) { append("-" + l.drop(2)) }
                                        l.startsWith("+ ") -> withStyle(SpanStyle(color = Added, background = Added.copy(alpha = 0.08f))) { append("+" + l.drop(2)) }
                                        else -> withStyle(SpanStyle(color = CodeFaint)) { append(" " + l.drop(2)) }
                                    }
                                }, fontFamily = Mono, fontSize = 12.sp, softWrap = false, color = CodeText)
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { at ->
        ConfirmDialog("Restore this snapshot?", "Your current files are saved as a snapshot first, so you can undo this.", "Restore",
            onDismiss = { confirm = null }) {
            scope.launch { withContext(Dispatchers.IO) { LocalHistory.restore(project, at) }; confirm = null; selected = null; version++ }
        }
    }
}
