package com.mnvstudio.x11.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.linux.LinuxApp
import com.mnvstudio.x11.screens.appIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Per-category gradients for app tiles, in the logo's blue-violet family. */
private val CategoryGradients = mapOf(
    "Desktops" to listOf(Color(0xFF5B8CFF), Color(0xFF7C4DFF)),
    "Tools" to listOf(Color(0xFF2EC4B6), Color(0xFF3A86FF)),
    "Office" to listOf(Color(0xFFFF9F1C), Color(0xFFFF5D73)),
    "Developer" to listOf(Color(0xFF8338EC), Color(0xFFFF006E)),
    "Graphics" to listOf(Color(0xFFFF5D8F), Color(0xFFFFB347)),
    "Internet" to listOf(Color(0xFF00B4D8), Color(0xFF4361EE)),
    "Classic" to listOf(Color(0xFF6C757D), Color(0xFF495057)),
)

fun categoryGradient(category: String) = CategoryGradients[category] ?: listOf(Color(0xFF5B8CFF), Color(0xFF7C4DFF))

/**
 * The app's own icon once it's installed (PNG from its .desktop entry inside Alpine), otherwise a gradient tile
 * with a symbol for the app.
 */
@Composable
fun AppIcon(app: LinuxApp, size: Dp = 42.dp) {
    val context = LocalContext.current
    var real by remember(app.id, Linux.revision) { mutableStateOf(IconCache.cached(app.id)) }
    LaunchedEffect(app.id, Linux.revision) {
        if (real == null) real = withContext(Dispatchers.IO) { IconCache.load(context, app) }
    }
    val shape = RoundedCornerShape(size * 0.28f)
    val bmp = real
    if (bmp != null) {
        Box(Modifier.size(size).clip(shape).background(CardHigh), contentAlignment = Alignment.Center) {
            Image(bmp, app.name, Modifier.size(size * 0.78f))
        }
    } else {
        Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(categoryGradient(app.category))), contentAlignment = Alignment.Center) {
            Icon(appIcon(app.id), null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
        }
    }
}

private object IconCache {
    private val icons = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()

    fun cached(id: String) = synchronized(this) { icons[id] }

    fun load(context: Context, app: LinuxApp): ImageBitmap? {
        synchronized(this) { icons[app.id]?.let { return it }; if (app.id in missing) return null }
        val root = Linux.rootfs(context)
        val file = runCatching { find(root, app) }.getOrNull()
        val bmp = file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
        synchronized(this) { if (bmp != null) icons[app.id] = bmp else if (file != null || isInstalled(root, app)) missing += app.id }
        return bmp
    }

    private fun isInstalled(root: File, app: LinuxApp) = File(root, "usr/bin/${app.binary}").exists()

    /** Icon= from the app's .desktop file, then the largest PNG of that name in the usual theme folders. */
    private fun find(root: File, app: LinuxApp): File? {
        if (!isInstalled(root, app)) return null
        val desktops = File(root, "usr/share/applications").listFiles { f -> f.name.endsWith(".desktop") } ?: emptyArray()
        val entry = desktops.firstOrNull { f -> f.readLines().any { it.startsWith("Exec=") && it.removePrefix("Exec=").substringBefore(' ').substringAfterLast('/') == app.binary } }
        val name = entry?.readLines()?.firstOrNull { it.startsWith("Icon=") }?.removePrefix("Icon=")?.trim() ?: app.binary
        if (name.startsWith("/")) return File(root, name.drop(1)).takeIf { it.exists() && it.name.endsWith(".png") }
        for (theme in listOf("hicolor", "Adwaita")) for (sz in listOf("256x256", "128x128", "96x96", "64x64", "48x48", "32x32")) {
            File(root, "usr/share/icons/$theme/$sz/apps/$name.png").takeIf { it.exists() }?.let { return it }
        }
        return File(root, "usr/share/pixmaps/$name.png").takeIf { it.exists() }
    }
}
