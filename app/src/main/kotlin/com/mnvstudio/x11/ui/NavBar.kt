package com.mnvstudio.x11.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating navigation: a frosted pill above the content. The selected tab grows into a crimson capsule with its
 * label (springing open), others shrink to icons; a soft glow sits under the active capsule.
 */
@Composable
fun FloatingNavBar(items: List<Triple<String, String, ImageVector>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().widthIn(max = 520.dp)
            .clip(CircleShape).background(Card.copy(alpha = 0.92f)).border(1.dp, Line, CircleShape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (id, label, icon) ->
            val on = id == selected
            val bg by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "bg")
            val pop by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "pop")
            val pad by animateDpAsState(if (on) 16.dp else 12.dp, label = "pad")
            Row(
                Modifier.height(48.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Accent.copy(alpha = bg), androidx.compose.ui.graphics.lerp(Accent, Color.Black, 0.15f).copy(alpha = bg))))
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                        if (!on) { if (Haptics.enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(id) }
                    }
                    .semantics { contentDescription = label; this.selected = on }
                    .padding(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = androidx.compose.ui.graphics.lerp(Label, OnAccent, bg), modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop })
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut() + shrinkHorizontally()) {
                    Text(label, color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
