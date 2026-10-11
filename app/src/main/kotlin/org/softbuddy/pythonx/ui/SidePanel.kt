package org.softbuddy.pythonx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A left side panel that slides over the content with a scrim. Unlike ModalNavigationDrawer it never
 * steals horizontal drags from the content while closed (the editor scrolls sideways), closes with a
 * swipe, a scrim tap or Back, and keeps its content composed only while visible.
 */
@Composable
fun SidePanel(open: Boolean, onClose: () -> Unit, width: Dp = 320.dp, content: @Composable () -> Unit) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val offset = remember { Animatable(-widthPx) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) { offset.animateTo(if (open) 0f else -widthPx, tween(if (open) 260 else 200)) }
    BackHandler(open, onClose)
    val visible = open || offset.value > -widthPx + 1f
    if (!visible) return
    val shown = 1f + offset.value / widthPx
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }.background(Color.Black.copy(alpha = 0.5f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        Box(Modifier.fillMaxHeight().width(width).graphicsLayer { translationX = offset.value }
            .shadow(16.dp).draggable(rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-widthPx, 0f)) } },
                Orientation.Horizontal, onDragStopped = { v ->
                    if (offset.value < -widthPx / 3 || v < -800f) onClose() else scope.launch { offset.animateTo(0f) }
                })) { content() }
    }
}

/** Section heading inside a side panel. */
@Composable
fun PanelHeader(text: String) =
    Text(text.uppercase(), color = Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp))

/** One tappable destination row inside a side panel. */
@Composable
fun PanelItem(icon: ImageVector, text: String, detail: String? = null, active: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clip(ShapeM)
        .background(if (active) Accent.copy(alpha = 0.14f) else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (active) Accent else Label)
        Text(text, Modifier.weight(1f).padding(start = 14.dp), color = if (active) Accent else Ink, fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        if (detail != null) Text(detail, color = Faint, fontSize = 12.sp)
    }
}
