package com.mnvstudio.x11.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A line graph of 0–100 values with a soft fill, newest on the right. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, max: Float = 100f) {
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        if (values.size < 2) return@Canvas
        val step = size.width / (com.mnvstudio.x11.Metrics.HISTORY - 1)
        val x0 = size.width - step * (values.size - 1)
        fun y(v: Float) = size.height - (v / max).coerceIn(0f, 1f) * size.height
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x0, y(v)) else lineTo(x0 + i * step, y(v)) } }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(x0, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A ring showing a 0–100 fraction with the value in the middle. */
@Composable
fun Gauge(percent: Float, label: String, color: Color, size: Dp = 76.dp, center: String = "${percent.toInt()}%") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { contentDescription = "$label $center" }) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val track = Line
            Canvas(Modifier.size(size)) {
                val w = 7.dp.toPx()
                val s = Size(this.size.width - w, this.size.height - w)
                val o = Offset(w / 2, w / 2)
                drawArc(track, 135f, 270f, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * (percent / 100f).coerceIn(0f, 1f), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(center, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = Label, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A thin horizontal bar for a fraction (e.g. storage used). */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(ShapeS).background(CardHigh).border(1.dp, Line, ShapeS)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(ShapeS).background(color))
    }
}
