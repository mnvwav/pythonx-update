package com.mnvstudio.x11.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Icon tiles are black like the logo, with a faint warm tint per category so groups still read apart. */
private val CategoryGradients = mapOf(
    "Desktops" to Color(0xFF2A2412), "Tools" to Color(0xFF1C2320), "Office" to Color(0xFF2A1E14), "Developer" to Color(0xFF221A26),
    "Graphics" to Color(0xFF2A1A1C), "Internet" to Color(0xFF16202A), "Classic" to Color(0xFF1E1E1E),
).mapValues { (_, c) -> listOf(c, Color(0xFF070707)) }

/** Icons drawn on those tiles. */
val TileIcon = Color(0xFFF7C948)

fun categoryGradient(category: String) = CategoryGradients[category] ?: listOf(Color(0xFF1E1E1E), Color(0xFF070707))

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
        Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(categoryGradient(app.category))).border(1.dp, TileIcon.copy(alpha = 0.18f), shape), contentAlignment = Alignment.Center) {
            Icon(appIcon(app.id), null, tint = TileIcon, modifier = Modifier.size(size * 0.52f))
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
        val file = runCatching { find(root, app)?.let { if (it.name.endsWith(".svg")) rasterize(context, it, app) else it } }.getOrNull()
        val bmp = file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
        synchronized(this) { if (bmp != null) icons[app.id] = bmp else if (file != null || isInstalled(root, app)) missing += app.id }
        return bmp
    }

    private fun isInstalled(root: File, app: LinuxApp) = File(root, "usr/bin/${app.binary}").exists()

    private val SIZES = listOf("256x256", "192x192", "128x128", "96x96", "64x64", "48x48", "32x32")

    /** Icon= from the app's .desktop file, then the largest PNG of that name in any icon theme, then an SVG converted to PNG. */
    private fun find(root: File, app: LinuxApp): File? {
        if (!isInstalled(root, app)) return null
        val desktops = File(root, "usr/share/applications").listFiles { f -> f.name.endsWith(".desktop") } ?: emptyArray()
        // Several entries can start the same program (its settings dialog, say): prefer the one named after the app
        // with the shortest command line.
        fun exec(f: File) = f.readLines().firstOrNull { it.startsWith("Exec=") }?.removePrefix("Exec=")?.trim()?.split(' ')?.filter { !it.contains('=') && it != "env" }
        val entry = desktops.filter { f -> exec(f)?.firstOrNull()?.substringAfterLast('/') == app.binary }
            .sortedWith(compareBy({ f -> if (f.nameWithoutExtension.substringAfterLast('.').equals(app.binary, true) || f.nameWithoutExtension.equals(app.binary, true)) 0 else 1 },
                { f -> exec(f)?.count { !it.startsWith("%") } ?: 9 }))
            .firstOrNull()
            ?: desktops.firstOrNull { it.nameWithoutExtension.substringAfterLast('.').equals(app.binary, true) }
        val name = entry?.readLines()?.firstOrNull { it.startsWith("Icon=") }?.removePrefix("Icon=")?.trim() ?: app.binary
        if (name.startsWith("/")) return File(root, name.drop(1)).takeIf { it.exists() }
        val base = name.removeSuffix(".png").removeSuffix(".svg")
        val themes = (File(root, "usr/share/icons").listFiles()?.filter { it.isDirectory }?.sortedBy { if (it.name == "hicolor") 0 else 1 } ?: emptyList())
        for (sz in SIZES) for (t in themes) for (sub in listOf("apps", "categories", "places", "devices")) {
            File(t, "$sz/$sub/$base.png").takeIf { it.exists() }?.let { return it }
        }
        File(root, "usr/share/pixmaps/$base.png").takeIf { it.exists() }?.let { return it }
        for (t in themes) for (dir in listOf("scalable/apps", "scalable/places", "256x256/apps", "48x48/apps")) {
            File(t, "$dir/$base.svg").takeIf { it.exists() }?.let { return it }
        }
        return File(root, "usr/share/pixmaps/$base.svg").takeIf { it.exists() }
    }

    /** Android can't draw SVG: convert it with rsvg-convert inside Linux (installed on first use), cached per app. */
    private fun rasterize(context: Context, svg: File, app: LinuxApp): File? {
        val root = Linux.rootfs(context)
        val out = File(root, "var/cache/x11-icons/${app.id}.png")
        if (out.exists() && out.lastModified() >= svg.lastModified()) return out
        val inside = "/" + svg.relativeTo(root).path
        val lock = Object(); var done = false
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Linux.capture(context, "mkdir -p /var/cache/x11-icons; command -v rsvg-convert >/dev/null || apk add -q rsvg-convert >/dev/null 2>&1; " +
                "rsvg-convert -w 128 -h 128 ${Linux.quote(inside)} -o /var/cache/x11-icons/${app.id}.png") { _, _ -> synchronized(lock) { done = true; lock.notifyAll() } }
        }
        synchronized(lock) { if (!done) lock.wait(60_000) }
        return out.takeIf { it.exists() }
    }
}
