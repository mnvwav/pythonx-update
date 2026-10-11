package com.mnvstudio.x11.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun copyText(context: Context, text: String, label: String = "Copied") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("X11", text))
    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
}

/** Whether buttons give a light vibration, set from Settings. */
object Haptics { @Volatile var enabled = true }

@Composable
private fun tap(onClick: () -> Unit): () -> Unit {
    val h = LocalHapticFeedback.current
    return { if (Haptics.enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}

// ---------------------------------------------------------------- brand

/** The accent as a soft metallic gradient (gold catches light at the top-left): primary buttons and hero cards. */
@Composable
fun accentGradient(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(Accent, Color.White, 0.12f), androidx.compose.ui.graphics.lerp(Accent, Color(0xFF8A5A00), 0.35f)))

/** Brand colors: gold wordmark on a black tile. */
val BrandGradient = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
val BrandGold = Color(0xFFF7C948)

/**
 * The X11 mark: the "X11" wordmark drawn as rounded strokes. Coordinates come from the 108-unit launcher icon
 * (res/drawable/ic_launcher_foreground.xml), whose mark spans 22..84; that region is fitted to this canvas.
 * [split] (0..1) pushes the X's four arms outward and fades the "11", for the opening animation.
 */
fun DrawScope.drawToolMark(color: Color = BrandGold, split: Float = 0f) {
    val k = size.minDimension / 62f
    val ox = (size.width - 62f * k) / 2 - 22f * k
    val oy = (size.height - 62f * k) / 2 - 23f * k
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    val w = 9f * k
    val cap = androidx.compose.ui.graphics.StrokeCap.Round
    // The X as four arms from its centre, so the splash can pull them apart.
    val cx = 38f; val cy = 54f
    for ((x, y) in listOf(27f to 39f, 49f to 69f, 49f to 39f, 27f to 69f)) {
        val dx = (x - cx) * split * 1.2f; val dy = (y - cy) * split * 1.2f
        drawLine(color, p(cx + dx, cy + dy), p(x + dx, y + dy), w, cap)
    }
    val ones = color.copy(alpha = color.alpha * (1f - split))
    for (x in listOf(63f, 79f)) {
        drawLine(ones, p(x - 6f, 45f), p(x, 39f), w, cap)
        drawLine(ones, p(x, 39f), p(x, 69f), w, cap)
    }
}

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier, framed: Boolean = true) {
    Box(
        modifier.size(size).then(if (framed) Modifier.clip(RoundedCornerShape(size * 0.28f))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(BrandGradient)) else Modifier)
            .semantics { contentDescription = "X11" },
    ) {
        Canvas(Modifier.size(size)) {
            val o = if (framed) this.size.minDimension * 0.16f else 0f
            inset(o) { drawToolMark() }
        }
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Row(
        modifier.height(54.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(if (enabled) accentGradient() else androidx.compose.ui.graphics.SolidColor(CardHigh))
            .clickable(source, androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = tap(onClick)).padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) OnAccent else Faint, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, color = if (enabled) OnAccent else Faint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Ink, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.height(50.dp).clip(CircleShape).background(CardHigh).border(1.dp, Line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = tap(onClick)).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) color else Faint
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, tint: Color = Ink, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(CardHigh).border(1.dp, Line, CircleShape)
            .clickable(role = Role.Button, onClick = tap(onClick)).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Screen header: back button, title, optional subtitle and trailing actions. */
@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Label, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

// ---------------------------------------------------------------- containers

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(padding), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp), color = Faint, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

/** A shell command in a code block; tapping copies it. */
@Composable
fun CommandBlock(command: String, modifier: Modifier = Modifier, caption: String? = null) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        if (caption != null) Text(caption, color = Label, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
                .clickable(role = Role.Button) { copyText(context, command) }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .semantics { contentDescription = "Copy command: $command" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text("$ ", color = Accent, fontFamily = Mono, fontSize = 13.sp)
                Text(command, color = Ink, fontFamily = Mono, fontSize = 13.sp, softWrap = false)
            }
            Box(Modifier.size(36.dp).clip(ShapeS).background(CardHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ContentCopy, null, tint = Label, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: String, modifier: Modifier = Modifier, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Text(
                label(o),
                Modifier.clip(CircleShape).background(if (sel) Accent else CardHigh).border(1.dp, if (sel) Accent else Line, CircleShape)
                    .clickable(role = Role.RadioButton, onClick = tap { onPick(o) }).padding(horizontal = 15.dp, vertical = 9.dp),
                color = if (sel) OnAccent else Ink, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun OptionCard(icon: ImageVector, title: String, body: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(ShapeM).background(if (selected) AccentSoft else Card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Accent else Line, ShapeM)
            .clickable(role = Role.RadioButton, onClick = tap(onClick)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(ShapeS).background(CardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) Accent else Label, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Label, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Accent else Line, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Accent))
        }
    }
}

@Composable
fun ToggleRow(title: String, body: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { onChange(!value) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = if (enabled) Ink else Faint, fontSize = 15.sp)
            if (body != null) Text(body, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(value, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, uncheckedTrackColor = CardHigh, uncheckedBorderColor = Line))
    }
}

@Composable
fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = CardHigh))
    }
}

/** A dot that pulses while [live]. */
@Composable
fun StatusDot(live: Boolean, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.2f, 0.85f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    val c = if (live) Live else Faint
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (live) Box(Modifier.size(size * 2.2f).clip(CircleShape).background(c.copy(alpha = a * 0.35f)))
        Box(Modifier.size(size).clip(CircleShape).background(c))
    }
}

@Composable
fun StepNumber(n: Int) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
        Text("$n", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** A small rounded label, e.g. a status. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Confirm(title: String, body: String, confirm: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(title, color = Ink, fontWeight = FontWeight.SemiBold) },
        text = { Text(body, color = Label, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) Bad else Accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Label) } },
    )
}
