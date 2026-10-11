package com.mnvstudio.x11.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val Out = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)      // expo-out: fast start, long soft landing
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * Opening animation, about 2.4 s:
 *  1. Out of the dark a crimson line traces the loop, a glowing spark leading it while an ambient glow breathes in.
 *  2. The white core drops into the crossing with a spring, sending out a shockwave ring and a burst of sparks,
 *     while a sheen of light runs once along the loop.
 *  3. "X11" rises letter by letter under it, then a quiet tagline.
 *  4. The core swells into a circle that fills the screen with the app's background, revealing the app.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val core = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val letters = remember { List(3) { Animatable(0f) } }
    val tag = remember { Animatable(0f) }
    val tilt = remember { Animatable(-18f) }
    val reveal = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val page = Page

    LaunchedEffect(Unit) {
        launch { glow.animateTo(1f, tween(1400, easing = InOut)) }
        launch { tilt.animateTo(0f, tween(1300, easing = Out)) }
        draw.animateTo(1f, tween(950, easing = InOut))
        launch { core.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
        launch { wave.animateTo(1f, tween(750, easing = Out)) }
        launch { delay(250); shine.animateTo(1f, tween(800, easing = InOut)) }
        delay(120)
        letters.forEachIndexed { i, a -> launch { delay(i * 90L); a.animateTo(1f, tween(520, easing = Out)) } }
        delay(420)
        tag.animateTo(1f, tween(380, easing = Out))
        delay(380)
        reveal.animateTo(1f, tween(520, easing = InOut))
        onFinished()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Background: deep charcoal with a crimson glow that breathes in behind the mark.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF0B0B0C))
            val c = Offset(size.width / 2, size.height / 2 - 40.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(BrandAccent.copy(alpha = 0.28f * glow.value), Color.Transparent), c, size.minDimension * 0.55f), size.minDimension * 0.55f, c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(200.dp).graphicsLayer { rotationZ = tilt.value; val s = 0.9f + 0.1f * draw.value; scaleX = s; scaleY = s }) {
                val k = size.minDimension / 68f
                val path = loopPath().apply { transform(logoMatrix()) }
                val pm = PathMeasure().apply { setPath(path, false) }
                val len = pm.length
                val upto = len * draw.value
                // Soft bloom under the line (wide, faint strokes), then the line itself.
                val seg = androidx.compose.ui.graphics.Path().also { pm.getSegment(0f, upto, it, true) }
                for ((w, a) in listOf(22f to 0.06f, 14f to 0.12f)) drawPath(seg, BrandAccent.copy(alpha = a), style = Stroke(w * k, cap = StrokeCap.Round))
                drawToolMark(drawn = draw.value, core = 0f)
                // The spark at the tip while drawing.
                if (draw.value in 0.001f..0.999f) {
                    val tip = pm.getPosition(upto)
                    drawCircle(Brush.radialGradient(listOf(Color.White, BrandLight.copy(alpha = 0.6f), Color.Transparent), tip, 10f * k), 10f * k, tip)
                    drawCircle(Color.White, 2.2f * k, tip)
                }
                // A light sweep runs along the loop once it's complete.
                if (shine.value in 0.001f..0.999f) {
                    val a = len * shine.value
                    val hl = androidx.compose.ui.graphics.Path().also { pm.getSegment(maxOf(0f, a - len * 0.08f), minOf(len, a + len * 0.08f), it, true) }
                    drawPath(hl, Color.White.copy(alpha = 0.55f * kotlin.math.sin(shine.value * Math.PI.toFloat())), style = Stroke(3f * k, cap = StrokeCap.Round))
                }
                // Shockwave from the crossing as the core lands, with sparks thrown outward.
                val ctr = Offset(size.width / 2, size.height / 2)
                if (wave.value in 0.001f..0.999f) drawCircle(BrandCore.copy(alpha = 0.55f * (1f - wave.value)), (6f + 34f * wave.value) * k, ctr,
                    style = Stroke(2.5f * k * (1f - wave.value) + 0.5f))
                if (wave.value in 0.001f..0.999f) for (i in 0 until 12) {
                    val ang = i * (2 * Math.PI / 12) + 0.26
                    val r = (8f + 40f * wave.value) * k * (if (i % 2 == 0) 1f else 0.75f)
                    drawCircle((if (i % 3 == 0) BrandCore else BrandLight).copy(alpha = 1f - wave.value),
                        (1.6f - wave.value) * k, Offset(ctr.x + (kotlin.math.cos(ang) * r).toFloat(), ctr.y + (kotlin.math.sin(ang) * r).toFloat()))
                }
                if (core.value > 0f) {
                    drawCircle(Brush.radialGradient(listOf(BrandCore.copy(alpha = 0.35f), Color.Transparent), ctr, 12f * k), 12f * k * core.value, ctr)
                    drawCircle(BrandCore, 4.2f * k * core.value, ctr)
                }
            }
            Row(Modifier.padding(top = 18.dp)) {
                "X11".forEachIndexed { i, ch ->
                    val v = letters[i].value
                    Text(ch.toString(), color = Color(0xFFF2F1EE), fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 46.sp, letterSpacing = 1.sp,
                        modifier = Modifier.graphicsLayer { alpha = v; translationY = (1f - v) * 28.dp.toPx(); val s = 0.85f + 0.15f * v; scaleX = s; scaleY = s })
                }
            }
            Text("Linux, in your pocket", color = Color(0xFFA5A4A0), fontFamily = Poppins, fontSize = 14.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp).graphicsLayer { alpha = tag.value; translationY = (1f - tag.value) * 10.dp.toPx() })
        }
        // Reveal: the core grows into a circle of the app's background that covers everything.
        if (reveal.value > 0f) Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 47.dp.toPx())
            drawCircle(page, hypot(size.width, size.height) * reveal.value, c)
        }
    }
}
