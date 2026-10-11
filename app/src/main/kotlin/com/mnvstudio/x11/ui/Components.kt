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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("command", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

/** The brand mark: a display frame with a violet/cyan X, drawn so it scales crisply at any size. */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brush.linearGradient(listOf(Color(0xFF1B1640), Page)))) {
        Canvas(Modifier.size(size)) {
            val s = this.size.width
            val stroke = s * 0.075f
            drawRoundRect(Color(0x55FFFFFF), Offset(s * 0.2f, s * 0.26f), Size(s * 0.6f, s * 0.44f), CornerRadius(s * 0.06f), style = Stroke(s * 0.025f))
            drawLine(Violet, Offset(s * 0.38f, s * 0.36f), Offset(s * 0.62f, s * 0.6f), stroke, StrokeCap.Round)
            drawLine(Cyan, Offset(s * 0.62f, s * 0.36f), Offset(s * 0.38f, s * 0.6f), stroke, StrokeCap.Round)
            drawLine(Color(0x55FFFFFF), Offset(s * 0.42f, s * 0.8f), Offset(s * 0.58f, s * 0.8f), s * 0.025f, StrokeCap.Round)
        }
    }
}

/** Full-width pill button filled with the brand gradient. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).clip(CircleShape)
            .background(if (enabled) BrandGradientDeep else Brush.linearGradient(listOf(CardHigh, CardHigh)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Box(Modifier.width(10.dp)) }
        Text(text, color = if (enabled) Color.White else Faint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Quiet pill button for secondary actions. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Ink, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(CircleShape).background(CardHigh).border(1.dp, Line, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Box(Modifier.width(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(ShapeL).background(Card).border(1.dp, Line, ShapeL).padding(padding), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), color = Faint, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

/** A shell command in a code block, with a copy button. */
@Composable
fun CommandBlock(command: String, modifier: Modifier = Modifier, caption: String? = null) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        if (caption != null) Text(caption, color = Label, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
                .clickable { copyText(context, command) }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text("$ ", color = Violet, fontFamily = Mono, fontSize = 13.sp)
                Text(command, color = Ink, fontFamily = Mono, fontSize = 13.sp, softWrap = false)
            }
            Box(Modifier.size(36.dp).clip(ShapeS).background(CardHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ContentCopy, "Copy", tint = Label, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Selectable chips; the selected one is filled with the accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: String, modifier: Modifier = Modifier, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Text(
                label(o),
                Modifier.clip(CircleShape).background(if (sel) Violet else CardHigh)
                    .border(1.dp, if (sel) Violet else Line, CircleShape)
                    .clickable { onPick(o) }.padding(horizontal = 16.dp, vertical = 9.dp),
                color = if (sel) Color.White else Ink, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** A big selectable option card with icon, title and description. */
@Composable
fun OptionCard(icon: ImageVector, title: String, body: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(ShapeM).background(if (selected) Color(0xFF1E1A3A) else Card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Violet else Line, ShapeM)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(ShapeS).background(if (selected) Violet.copy(alpha = 0.2f) else CardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) Violet else Label, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Label, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Violet else Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Violet)) }
    }
}

@Composable
fun ToggleRow(title: String, body: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            if (body != null) Text(body, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = CardHigh, uncheckedBorderColor = Line))
    }
}

@Composable
fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet, inactiveTrackColor = CardHigh))
    }
}

/** A dot that pulses while [live]. */
@Composable
fun StatusDot(live: Boolean, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (live) Box(Modifier.size(size * 2.2f).clip(CircleShape).background(Good.copy(alpha = a * 0.35f)))
        Box(Modifier.size(size).clip(CircleShape).background(if (live) Good else Faint))
    }
}

/** Numbered step marker used in guides. */
@Composable
fun StepNumber(n: Int) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
