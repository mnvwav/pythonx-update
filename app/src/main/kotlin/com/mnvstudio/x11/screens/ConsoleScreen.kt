package com.mnvstudio.x11.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.ShapeS
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.copyText

/** Output and history survive leaving the screen while the app runs. */
private object ConsoleState {
    val output = mutableStateListOf<String>()
    val history = mutableStateListOf<String>()
    var running: Process? = null
}

/** Run commands in the built-in Linux, with history and copyable output. */
@Composable
fun ConsoleScreen(prefs: Prefs, navigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var cmd by rememberSaveable { mutableStateOf("") }
    var historyIndex by remember { mutableStateOf(-1) }
    var busy by remember { mutableStateOf(ConsoleState.running != null) }
    val list = rememberLazyListState()
    val out = ConsoleState.output
    LaunchedEffect(out.size) { if (out.isNotEmpty()) list.scrollToItem(out.lastIndex) }
    LaunchedEffect(Unit) { Linux.check(context) }

    fun add(line: String) { out += line; if (out.size > 2000) out.removeRange(0, 200) }
    fun run(c: String = cmd) {
        val command = c.trim()
        if (command.isEmpty() || busy) return
        if (Linux.state != Linux.State.READY) { add("Set up Linux first (Linux apps screen)."); return }
        ConsoleState.history.remove(command); ConsoleState.history += command
        historyIndex = -1
        add("$ $command"); cmd = ""
        busy = true
        // Commands ending in & keep running as apps; others are waited for here.
        val background = command.endsWith("&")
        val p = Linux.run(context, command.removeSuffix("&").trim(), XHolder.display.takeIf { XHolder.running } ?: prefs.display,
            jobName = if (background) command.removeSuffix("&").trim().substringBefore(' ') else null, onOutput = ::add) { code ->
            if (!background) { add("[exit $code]"); busy = false; ConsoleState.running = null }
        }
        if (background) { busy = false; add("[started in the background]") } else ConsoleState.running = p
    }

    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding().imePadding()) {
        TopBar("Console", "Alpine Linux · root (proot)", onBack = onBack) {
            RoundIcon(Icons.Filled.ContentCopy, "Copy output") { copyText(context, out.joinToString("\n"), "Output copied") }
            RoundIcon(Icons.Filled.DeleteSweep, "Clear") { out.clear() }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM).padding(12.dp),
            state = list,
        ) {
            if (out.isEmpty()) item {
                Text("Type a command below. Try: apk add nano · uname -a · ls /root/Shared\nEnd with & to start an app in the background, e.g. xterm &",
                    color = Faint, fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp)
            }
            items(out) { line ->
                Text(line, color = when { line.startsWith("$ ") -> Accent; line.startsWith("[exit") && line != "[exit 0]" -> Bad; else -> Label },
                    fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        if (ConsoleState.history.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = Faint)
                ConsoleState.history.takeLast(8).reversed().forEach { h ->
                    Text(h, color = Ink, fontFamily = Mono, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.clip(ShapeS).background(CardHigh).border(1.dp, Line, ShapeS)
                            .clickable { cmd = h }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        } else Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                cmd, { cmd = it }, Modifier.weight(1f), singleLine = true, shape = ShapeM,
                placeholder = { Text("command", color = Faint, fontFamily = Mono) },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { run() }),
            )
            if (busy) IconButton(onClick = { ConsoleState.running?.destroy() }) { Icon(Icons.Filled.Stop, "Stop", tint = Bad) }
            else IconButton(onClick = {
                // Up-arrow style recall: each tap goes one command further back.
                val h = ConsoleState.history
                if (h.isNotEmpty()) { historyIndex = if (historyIndex <= 0) h.lastIndex else historyIndex - 1; cmd = h[historyIndex] }
            }) { Icon(Icons.Filled.History, "Previous command", tint = Label) }
            IconButton(onClick = { navigate("display") }) { Icon(Icons.Filled.Cast, "Open display", tint = Label) }
        }
    }
}
