package com.mnvstudio.x11.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Motion language, taken from the opening animation: things arrive from slightly below and settle with an expo-out
 * curve, pressed things give a little, and light travels along the brand loop. Durations stay short (≤ 450 ms) so
 * the app never feels slow.
 */

val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val EaseInOutSoft = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Tappable with a soft squish on press and a light haptic tick; use instead of clickable on cards and tiles. */
fun Modifier.pressable(enabled: Boolean = true, scaleTo: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    val haptic = LocalHapticFeedback.current
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button) {
            if (Haptics.enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        }
}

/**
 * Arrives once: fades in and rises 18 dp, [index] staggering it after earlier siblings. State survives
 * recomposition, so lists don't re-animate while scrolling or updating.
 */
fun Modifier.enter(index: Int = 0, step: Int = 45): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(450, delayMillis = (index * step).coerceAtMost(400), easing = EaseOutExpo)) }
    this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 18.dp.toPx() }
}

/** Slow, drifting crimson light behind a page: two soft blobs that wander over ~20 s. Cheap: one Canvas, no blur. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, strength: Float = 1f) {
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(21_000, easing = LinearEasing)), label = "a")
    val accent = Accent
    val light = LocalIsLight.current
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val alpha = (if (light) 0.10f else 0.16f) * strength
        val c1 = Offset(w * (0.25f + 0.18f * cos(a)), h * (0.08f + 0.06f * sin(a * 2)))
        val c2 = Offset(w * (0.85f + 0.12f * sin(a)), h * (0.22f + 0.08f * cos(a)))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color.Transparent), c1, w * 0.7f), w * 0.7f, c1)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha * 0.6f), Color.Transparent), c2, w * 0.55f), w * 0.55f, c2)
    }
}

/**
 * The logo, alive: a light sweep travels along the loop every few seconds and the core breathes. Used where the
 * brand appears inside the app (Home header, navigation rail).
 */
@Composable
fun LiveMark(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mark")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = 4200; 0f at 0; 0f at 2400; 1f at 4200 using EaseInOutSoft }), label = "sweep")
    val breathe by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1600, easing = EaseInOutSoft), RepeatMode.Reverse), label = "core")
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            drawToolMark(core = breathe, fill = 0.92f)
            if (sweep in 0.001f..0.999f) {
                val path = loopPath().apply { transform(logoMatrix(0.92f)) }
                val pm = PathMeasure().apply { setPath(path, false) }
                val len = pm.length; val a = len * sweep
                val seg = Path().also { pm.getSegment(maxOf(0f, a - len * 0.09f), minOf(len, a + len * 0.09f), it, true) }
                val k = this.size.minDimension / 68f * 0.92f
                drawPath(seg, Color.White.copy(alpha = 0.6f * sin(sweep * Math.PI.toFloat())), style = Stroke(3f * k, cap = StrokeCap.Round))
            }
        }
    }
}

/** Number that rolls to its new value instead of jumping. */
@Composable
fun animatedInt(target: Int): Int = animateIntAsState(target, tween(600, easing = EaseOutExpo), label = "count").value

/** [AmbientBackground] as a modifier: draws the drifting glow behind whatever this element contains. */
fun Modifier.ambient(strength: Float = 0.7f): Modifier = composed {
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(23_000, easing = LinearEasing)), label = "a")
    val accent = Accent
    val light = LocalIsLight.current
    this.drawBehind {
        val w = size.width; val h = size.height
        val alpha = (if (light) 0.09f else 0.14f) * strength
        val c1 = Offset(w * (0.2f + 0.2f * cos(a)), h * (0.06f + 0.05f * sin(a * 2)))
        val c2 = Offset(w * (0.9f + 0.1f * sin(a)), h * (0.18f + 0.07f * cos(a)))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color.Transparent), c1, w * 0.75f), w * 0.75f, c1)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha * 0.6f), Color.Transparent), c2, w * 0.6f), w * 0.6f, c2)
    }
}
