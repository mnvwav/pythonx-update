package org.softbuddy.pythonx.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.term.Term
import org.softbuddy.pythonx.term.Terms
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.clickableNoFocus
import java.io.File

private val TermBg = Color(0xFF000000)
private val BarBg = Color(0xFF0E0F12)
private val KeyText = Color(0xFFE6E6E6)
private val KeyFaint = Color(0xFF8A909C)
private val KeyOn = Color(0xFF5FD4A0)
private val OkColor = Color(0xFF8FDCA0)
private val ErrColor = Color(0xFFFF8A8A)

/** The long-lived terminal for [key]; it keeps running after the screen closes, until the user stops it. */
@Composable
fun rememberTerm(prefs: Prefs, key: String): Term {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(key) { Terms.get(context, key, with(density) { prefs.termFontSize.sp.roundToPx() }) }
}

/** The terminal surface itself. */
@Composable
fun TerminalPane(term: Term, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setTerminalViewClient(term.viewClient)
                setTextSize(term.fontPx)
                setTypeface(runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, org.softbuddy.pythonx.R.font.fira_code) }.getOrNull()
                    ?: Typeface.MONOSPACE)
                isFocusable = true; isFocusableInTouchMode = true
                setBackgroundColor(android.graphics.Color.BLACK)
                term.view = this
                term.session?.let { attachSession(it) }
            }
        },
        onRelease = { if (term.view === it) term.view = null },
        modifier = modifier.background(TermBg).padding(horizontal = 4.dp),
    )
}

/**
 * A real terminal: /system/bin/sh on a pseudo-terminal, rendered by Termux's emulator, so colours, cursor
 * movement, Ctrl-C, input() and full-screen programs all work.
 *
 * [start] runs when the screen opens and no session is running yet; [rerun] restarts a finished program.
 */
@Composable
fun TerminalScreen(
    prefs: Prefs, key: String, title: String, onBack: () -> Unit,
    start: (Term) -> Unit, rerun: ((Term) -> Unit)? = null,
) {
    val context = LocalContext.current
    val term = rememberTerm(prefs, key)
    LaunchedEffect(Unit) { if (!term.running && term.session == null) start(term); term.showKeyboard() }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(TermBg).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(BarBg).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = KeyText) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(term.title.ifBlank { title }, color = KeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(when {
                    term.running -> if (term.program) "Running" else "Shell"
                    else -> "Finished"
                }, color = if (term.running) OkColor else KeyFaint, fontSize = 12.sp)
            }
            IconButton({ copy(context, term.transcript()) }) { Icon(Icons.Filled.ContentCopy, "Copy output", tint = KeyText) }
            if (term.running) IconButton({ term.stop() }) { Icon(Icons.Filled.StopCircle, "Stop", tint = ErrColor) }
            else IconButton({ (rerun ?: start)(term) }) { Icon(Icons.Filled.Refresh, "Run again", tint = KeyText) }
        }
        TerminalPane(term, Modifier.weight(1f).fillMaxWidth())
        if (!term.running && term.program && term.exitCode != null) ExitBar(term, onRerun = { (rerun ?: start)(term) }, onShell = {
            term.startShell(term.cwd)
        })
        ExtraKeys(term)
    }
}

/** Shown under a finished program: how it ended and how long it ran, with quick actions. */
@Composable
fun ExitBar(term: Term, onRerun: () -> Unit, onShell: () -> Unit) {
    val code = term.exitCode ?: 0
    val seconds = (term.finishedAt - term.startedAt).coerceAtLeast(0) / 1000.0
    val ok = code == 0
    Row(Modifier.fillMaxWidth().background(BarBg).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null, Modifier.size(16.dp), tint = if (ok) OkColor else ErrColor)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                ok -> "Finished"
                code == 130 -> "Interrupted (Ctrl-C)"
                code > 128 -> "Stopped by signal ${code - 128}"
                else -> "Exited with code $code"
            } + " · " + if (seconds < 10) "%.2fs".format(seconds) else "%.0fs".format(seconds),
            color = if (ok) OkColor else ErrColor, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.weight(1f),
        )
        TextButton(onShell) { Icon(Icons.Filled.Terminal, null, Modifier.size(16.dp), tint = KeyText); Text(" Shell", color = KeyText, fontSize = 13.sp) }
        TextButton(onRerun) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp), tint = KeyOn); Text(" Run again", color = KeyOn, fontSize = 13.sp) }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Output", text.trimEnd()))
    Toast.makeText(context, "Output copied", Toast.LENGTH_SHORT).show()
}

/** Two rows of Termux-style extra keys. CTRL and ALT latch until the next key. */
@Composable
fun ExtraKeys(term: Term, compact: Boolean = false) {
    val rows = if (compact) listOf(listOf("ESC", "TAB", "CTRL", "^C", "←", "↑", "↓", "→"))
    else listOf(listOf("ESC", "TAB", "CTRL", "ALT", "↑", "^C", "HOME"), listOf("|", "-", "/", "←", "↓", "→", "END"))
    Column(Modifier.fillMaxWidth().background(BarBg).padding(vertical = 2.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 42.dp)) {
                row.forEach { k ->
                    val on = when (k) { "CTRL" -> term.ctrl; "ALT" -> term.alt; else -> false }
                    Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = k }.clickableNoFocus {
                        when (k) {
                            "CTRL" -> term.ctrl = !term.ctrl
                            "ALT" -> term.alt = !term.alt
                            "^C" -> term.interrupt()
                            "|", "-", "/" -> term.write(k)
                            else -> term.key(when (k) {
                                "ESC" -> KeyEvent.KEYCODE_ESCAPE; "TAB" -> KeyEvent.KEYCODE_TAB
                                "↑" -> KeyEvent.KEYCODE_DPAD_UP; "↓" -> KeyEvent.KEYCODE_DPAD_DOWN
                                "←" -> KeyEvent.KEYCODE_DPAD_LEFT; "→" -> KeyEvent.KEYCODE_DPAD_RIGHT
                                "HOME" -> KeyEvent.KEYCODE_MOVE_HOME; else -> KeyEvent.KEYCODE_MOVE_END
                            })
                        }
                    }, contentAlignment = Alignment.Center) {
                        Text(k, Modifier.clip(ShapeS).background(if (on) KeyOn.copy(alpha = 0.2f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 2.dp),
                            color = when { on -> KeyOn; k == "^C" -> ErrColor; k.length == 1 -> KeyOn; else -> KeyText },
                            fontFamily = Mono, fontSize = if (compact) 12.sp else 14.sp)
                    }
                }
            }
        }
    }
}
