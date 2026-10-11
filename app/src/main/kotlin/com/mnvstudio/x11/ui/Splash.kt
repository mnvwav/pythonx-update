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
 * Opening animation: the X in the logo splits into four arms that fly apart while the pointer fades,
 * "X11" rises in their place, then everything fades into the app. About 1.6 s.
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
            // The X's four arms fly apart and the pointer fades, leaving room for the name.
            drawToolMark(Color.White, split = split.value)
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

