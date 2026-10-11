package org.softbuddy.pythonx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A rounded surface with an optional title row and action link. */
@Composable
fun Card(
    title: String? = null, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().clip(ShapeL).background(CardBg).padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (title != null) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (action != null) Text(action, color = Accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction))
        }
        content()
    }
}

/** A square-ish create tile: icon, title and a short caption. */
@Composable
fun Tile(icon: ImageVector, title: String, caption: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(ShapeL).background(CardBg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        Text(caption, color = Faint, fontSize = 12.sp, maxLines = 1)
    }
}

/** A navigation row with an icon, title, trailing detail and chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(detail, color = Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Faint)
    }
}

/** Top bar used by secondary screens: back arrow, title, optional subtitle and actions. */
@Composable
fun ScreenBar(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** A small coloured tag whose hue is derived from its text. */
@Composable
fun TagChip(tag: String) {
    val c = TagPalette[Math.floorMod(tag.hashCode(), TagPalette.size)]
    Text(tag, Modifier.clip(ShapeS).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = c, fontSize = 12.sp)
}

private val TagPalette = listOf(
    0xFF9C8CFF, 0xFF4ADE80, 0xFFFACC15, 0xFF60A5FA, 0xFFF472B6, 0xFFFB923C, 0xFF2DD4BF, 0xFFA3E635,
).map { Color(it) }

/** Pick-one control: a pill-shaped track of options. */
@Composable
fun Segmented(label: String, options: List<String>, value: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink)
        Row(Modifier.clip(ShapeM).background(Inset).padding(3.dp)) {
            options.forEach { o ->
                val sel = o == value
                Text(o, Modifier.clip(ShapeS).background(if (sel) Accent else Color.Transparent).clickable { onPick(o) }
                    .padding(horizontal = 12.dp, vertical = 6.dp), color = if (sel) OnAccent else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink)
            if (sub != null) Text(sub, color = Faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }

/** Single-field dialog, with the text preselected (up to the extension) and the keyboard open. */
@Composable
fun NameDialog(
    title: String, initial: String, onDismiss: () -> Unit, label: String = "Name",
    keyboard: KeyboardType = KeyboardType.Text, confirm: String = "OK", onOk: (String) -> Unit,
) {
    val end = initial.substringBeforeLast('.').length.takeIf { initial.contains('.') && it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().focusRequester(focus), label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onOk(value.text.trim()) }))
        },
        confirmButton = { TextButton({ onOk(value.text.trim()) }, enabled = value.text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** A yes/no dialog; [danger] colours the confirm button red. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = if (danger) Bad else Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when { s < 60 -> "just now"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; s < 86400 * 30 -> "${s / 86400}d ago"; else -> "${s / (86400 * 30)}mo ago" }
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}
