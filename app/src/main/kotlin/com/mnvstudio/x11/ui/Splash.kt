package com.mnvstudio.x11.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Opening animation: the crossed screwdriver and hammer (the logo) slide apart along their own axes,
 * "X11" rises out of the gap between them, then everything fades into the app. About 1.6 s.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(380, easing = Ease))
        delay(120)
        coroutineScope {
            listOf(
                async { split.animateTo(1f, tween(620, easing = Ease)) },
                async { delay(180); word.animateTo(1f, tween(520, easing = Ease)) },
            ).awaitAll()
        }
        delay(260)
        exit.animateTo(1f, tween(260))
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10)).alpha(1f - exit.value), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp).graphicsLayer {
            val s = 0.85f + 0.15f * appear.value
            scaleX = s; scaleY = s; alpha = appear.value
        }) {
            // Each tool travels outward along its own diagonal, so the X opens like scissors.
            val d = size.minDimension * 0.34f * split.value
            translate(-d * 0.7071f, -d * 0.7071f) { screwdriverTip(this, Color.White) }
            translate(d * 0.7071f, d * 0.7071f) { screwdriverHandle(this, Color.White) }
            translate(d * 0.7071f, -d * 0.7071f) { hammerHead(this, Color.White) }
            translate(-d * 0.7071f, d * 0.7071f) { hammerHandle(this, Color.White) }
        }
        Text(
            "X11", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 2.sp,
            modifier = Modifier.graphicsLayer {
                alpha = word.value
                translationY = (1f - word.value) * 40f
                val s = 0.8f + 0.2f * word.value
                scaleX = s; scaleY = s
            },
        )
    }
}

// The logo split into its four halves (same geometry as drawToolMark), so each can move on its own.

private fun DrawScope.bar(c: Offset, s: Float, x0: Float, x1: Float, w: Float, r: Float, color: Color) =
    drawRoundRect(color, Offset(c.x + x0 * s, c.y - w * s / 2), Size((x1 - x0) * s, w * s), CornerRadius(r * s))

private fun DrawScope.center() = Offset(size.width / 2, size.height / 2)

private fun screwdriverTip(d: DrawScope, color: Color) = with(d) {
    val c = center(); val s = size.minDimension
    rotate(45f, c) { bar(c, s, -0.40f, -0.29f, 0.11f, 0.015f, color); bar(c, s, -0.30f, 0.06f, 0.06f, 0.012f, color) }
}

private fun screwdriverHandle(d: DrawScope, color: Color) = with(d) {
    val c = center(); val s = size.minDimension
    rotate(45f, c) { bar(c, s, 0.04f, 0.09f, 0.12f, 0.012f, color); bar(c, s, 0.08f, 0.40f, 0.16f, 0.055f, color) }
}

private fun hammerHead(d: DrawScope, color: Color) = with(d) {
    val c = center(); val s = size.minDimension
    rotate(-45f, c) {
        bar(c, s, -0.08f, 0.22f, 0.07f, 0.012f, color)
        drawRoundRect(color, Offset(c.x + 0.21f * s, c.y - 0.17f * s), Size(0.13f * s, 0.34f * s), CornerRadius(0.025f * s))
        drawRoundRect(color, Offset(c.x + 0.24f * s, c.y - 0.27f * s), Size(0.07f * s, 0.11f * s), CornerRadius(0.025f * s))
    }
}

private fun hammerHandle(d: DrawScope, color: Color) = with(d) {
    val c = center(); val s = size.minDimension
    rotate(-45f, c) { bar(c, s, -0.40f, -0.06f, 0.16f, 0.055f, color) }
}
