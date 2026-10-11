package com.mnvstudio.x11.server

import kotlin.math.max
import kotlin.math.min

/**
 * A window manager built into the server. Instead of reparenting windows into frames (as external window managers
 * do), it draws title bars and borders in the compositor and handles pointer input on them itself. That keeps apps
 * unaware of it, and needs no extra X round-trips.
 *
 * Features: title bars with close / maximize / minimize, move by dragging the title, resize from the bottom-right
 * corner, click-to-focus and raise, edge snapping (left/right halves, top = maximize), placement memory by app class,
 * per-app fullscreen rules, virtual desktops, and an app switcher.
 *
 * Everything here runs under the server lock.
 */
class WindowManager(private val s: XServer) {
    class Managed(val window: Window) {
        var desktop = 0
        var maximized = false
        var minimized = false
        var fullscreen = false
        /** Geometry before maximizing or snapping: x, y, w, h. */
        var restore: IntArray? = null
    }

    /** Title bar look; set by the app from Settings. */
    class Style(
        var height: Int = 24,
        var activeColor: Int = 0x2A2A31, var inactiveColor: Int = 0x1A1A1F,
        var textColor: Int = 0xF3F3F5, var inactiveText: Int = 0x8A8A94,
        var accent: Int = 0x5B8CFF, var closeColor: Int = 0xE5484D,
    )

    val style = Style()
    val managed = LinkedHashMap<Window, Managed>()
    var desktops = 4
    var current = 0; private set
    var snapping = true
    /** Remembered geometry by WM_CLASS: x, y, w, h. The app persists this through [onPlacement]. */
    val placements = HashMap<String, IntArray>()
    var onPlacement: ((String, IntArray) -> Unit)? = null
    /** WM_CLASS → "fullscreen" or "windowed" (default windowed). */
    val rules = HashMap<String, String>()
    var onChange: (() -> Unit)? = null

    private val font: XFont? = Fonts.open("7x14")
    private var drag: Drag? = null
    private class Drag(val m: Managed, val resize: Boolean, val px: Int, val py: Int, val x: Int, val y: Int, val w: Int, val h: Int)

    private val atomProtocols = s.atoms.intern("WM_PROTOCOLS")
    private val atomDelete = s.atoms.intern("WM_DELETE_WINDOW")
    private val atomNetName = s.atoms.intern("_NET_WM_NAME")

    private val tb get() = style.height

    fun isManaged(w: Window) = managed.containsKey(w)
    fun hidden(w: Window): Boolean = managed[w]?.let { it.minimized || it.desktop != current } == true
    private fun decorated(m: Managed) = !m.fullscreen

    // ---------------------------------------------------------------- titles & classes

    fun title(w: Window): String {
        w.props[atomNetName]?.let { return String(it.data, Charsets.UTF_8) }
        w.props[Atoms.WM_NAME]?.let { return String(it.data, Charsets.ISO_8859_1) }
        return "Window ${Integer.toHexString(w.id)}"
    }

    /** The class part of WM_CLASS ("XTerm", "XCalc"), used for rules and remembered placement. */
    fun appClass(w: Window): String = w.props[67]?.let { p -> String(p.data, Charsets.ISO_8859_1).split('\u0000').getOrNull(1) } ?: ""

    // ---------------------------------------------------------------- managing

    /** Called just before a top-level window is mapped: decide where it goes. */
    fun manage(w: Window) {
        val m = managed.getOrPut(w) { Managed(w).also { it.desktop = current } }
        val cls = appClass(w)
        if (rules[cls] == "fullscreen") { setFullscreen(m, true); return }
        val remembered = placements[cls]
        if (remembered != null) {
            w.x = remembered[0]; w.y = remembered[1]; w.resize(min(remembered[2], s.width), min(remembered[3], s.height - tb))
        } else if (w.x == 0 && w.y == 0) {
            // Cascade new windows so they don't stack exactly.
            val n = managed.size - 1
            w.x = (24 + n * 28) % max(1, s.width / 3); w.y = tb + (16 + n * 28) % max(1, s.height / 3)
        }
        constrain(m)
    }

    fun forget(w: Window) {
        managed.remove(w)?.let { if (drag?.m?.window === w) drag = null }
        onChange?.invoke()
    }

    /** Keeps the title bar on screen and the window no bigger than the screen. */
    private fun constrain(m: Managed) {
        val w = m.window
        if (!decorated(m)) return
        if (w.width + 2 * w.border > s.width) w.resize(s.width - 2 * w.border, w.height)
        if (w.height + 2 * w.border + tb > s.height) w.resize(w.width, max(1, s.height - 2 * w.border - tb))
        w.y = w.y.coerceIn(tb, max(tb, s.height - 24))
        w.x = w.x.coerceIn(-(w.width - 48), s.width - 48)
    }

    /** A client asked to move/resize: let it, within limits. */
    fun clientConfigured(w: Window) { managed[w]?.let { constrain(it) } }

    /** The whole frame (title bar + window with border) in screen coordinates, or null if not decorated. */
    fun frame(w: Window): Rect? {
        val m = managed[w] ?: return null
        if (!decorated(m)) return null
        return Rect(w.x, w.y - tb, w.width + 2 * w.border, w.height + 2 * w.border + tb)
    }

    // ---------------------------------------------------------------- drawing

    /** Paints a window's title bar and resize grip into the framebuffer, clipped to [clip]. */
    fun drawFrame(fb: IntArray, fbw: Int, w: Window, clip: Rect) {
        val f = frame(w) ?: return
        val active = isFocused(w)
        val bar = if (active) style.activeColor else style.inactiveColor
        fun fill(x: Int, y: Int, ww: Int, hh: Int, c: Int) {
            val x0 = max(max(x, clip.x), 0); val y0 = max(max(y, clip.y), 0)
            val x1 = min(min(x + ww, clip.x + clip.w), fbw); val y1 = min(min(y + hh, clip.y + clip.h), fb.size / fbw)
            for (yy in y0 until y1) java.util.Arrays.fill(fb, yy * fbw + x0, yy * fbw + max(x0, x1), c or XServer.OPAQUE)
        }
        fill(f.x, f.y, f.w, tb, bar)
        if (active) fill(f.x, f.y, f.w, 2, style.accent)
        // Buttons from the right: close, maximize, minimize.
        val bw = tb
        val bx = f.x + f.w - bw * 3
        glyphButton(fb, fbw, clip, bx + bw * 2, f.y, bw, style.closeColor, 'x')
        glyphButton(fb, fbw, clip, bx + bw, f.y, bw, if (active) style.textColor else style.inactiveText, if (managed[w]?.maximized == true) 'r' else 'm')
        glyphButton(fb, fbw, clip, bx, f.y, bw, if (active) style.textColor else style.inactiveText, '_')
        // Title text, clipped before the buttons.
        val fnt = font ?: return
        val text = title(w)
        val maxChars = max(0, (bx - f.x - 10) / fnt.width)
        val ty = f.y + (tb - fnt.height) / 2
        var cx = f.x + 10
        for (ch in text.take(maxChars)) {
            val code = if (ch.code in 0..255) ch.code else '?'.code
            for (gy in 0 until fnt.height) for (gx in 0 until fnt.width) if (fnt.pixel(code, gx, gy)) {
                val px = cx + gx; val py = ty + gy
                if (px >= clip.x && py >= clip.y && px < clip.x + clip.w && py < clip.y + clip.h && px in 0 until fbw && py >= 0 && py < fb.size / fbw)
                    fb[py * fbw + px] = (if (active) style.textColor else style.inactiveText) or XServer.OPAQUE
            }
            cx += fnt.width
        }
        // Resize grip: three short diagonal lines in the bottom-right corner of the window.
        val gx0 = f.x + f.w; val gy0 = f.y + f.h
        for (k in 0 until 3) for (t in 0 until 6 - k * 2) {
            val px = gx0 - 2 - k * 4 - t; val py = gy0 - 2 - (5 - k * 2) + t
            if (px >= clip.x && py >= clip.y && px < clip.x + clip.w && py < clip.y + clip.h && px in 0 until fbw && py >= 0 && py < fb.size / fbw)
                fb[py * fbw + px] = style.inactiveText or XServer.OPAQUE
        }
    }

    private fun glyphButton(fb: IntArray, fbw: Int, clip: Rect, x: Int, y: Int, size: Int, color: Int, kind: Char) {
        val c = color or XServer.OPAQUE
        val cx = x + size / 2; val cy = y + size / 2; val r = size / 5
        fun dot(px: Int, py: Int) {
            if (px >= clip.x && py >= clip.y && px < clip.x + clip.w && py < clip.y + clip.h && px in 0 until fbw && py >= 0 && py < fb.size / fbw) fb[py * fbw + px] = c
        }
        when (kind) {
            'x' -> for (i in -r..r) { dot(cx + i, cy + i); dot(cx + i, cy - i); dot(cx + i + 1, cy + i); dot(cx + i + 1, cy - i) }
            '_' -> for (i in -r..r) { dot(cx + i, cy + r); dot(cx + i, cy + r - 1) }
            'm' -> for (i in -r..r) { dot(cx + i, cy - r); dot(cx + i, cy + r); dot(cx - r, cy + i); dot(cx + r, cy + i); dot(cx + i, cy - r + 1) }
            else -> for (i in -r + 2..r) { dot(cx + i, cy - r + 2); dot(cx + i, cy + r); dot(cx - r + 2, cy + i); dot(cx + r, cy + i); dot(cx + i - 2, cy - r); dot(cx - r, cy + i - 2) }
        }
    }

    // ---------------------------------------------------------------- input

    private fun isFocused(w: Window): Boolean {
        val f = s.resources[s.focus] as? Window
        return f === w || (f != null && w.isAncestorOf(f)) || (s.focus == 1 && topmost() === w)
    }

    private fun topmost(): Window? = s.root.children.lastOrNull { it.mapped && managed.containsKey(it) && !hidden(it) }

    /** The managed window whose title bar or resize grip is at (x, y), topmost first, with what was hit. */
    private fun hitFrame(x: Int, y: Int): Pair<Managed, String>? {
        for (w in s.root.children.asReversed()) {
            if (!w.mapped || hidden(w)) continue
            val m = managed[w]
            val f = if (m != null) frame(w) else null
            if (m != null && f != null) {
                if (x >= f.x && x < f.x + f.w && y >= f.y && y < f.y + tb) {
                    val fromRight = f.x + f.w - x
                    return m to when { fromRight <= tb -> "close"; fromRight <= tb * 2 -> "maximize"; fromRight <= tb * 3 -> "minimize"; else -> "title" }
                }
                if (x >= f.x + f.w - 14 && x < f.x + f.w + 6 && y >= f.y + f.h - 14 && y < f.y + f.h + 6) return m to "resize"
            }
            // Something else covers this point: stop looking underneath.
            val ox = w.absX - w.border; val oy = w.absY - w.border
            if (x >= ox && y >= oy && x < ox + w.width + 2 * w.border && y < oy + w.height + 2 * w.border) return null
        }
        return null
    }

    /** Pointer button on the screen. Returns true if the window manager handled it (title bar, buttons, grip). */
    fun button(x: Int, y: Int, b: Int, down: Boolean): Boolean {
        if (!down) {
            val d = drag ?: return false
            drag = null
            finishDrag(d, x, y)
            return true
        }
        val hit = hitFrame(x, y)
        if (hit == null) {
            // Click inside a managed window: focus and raise it, then let the app have the click.
            val under = s.windowAt(x, y)
            val top = generateSequence(under) { it.parent }.firstOrNull { it.parent === s.root }
            if (top != null && managed.containsKey(top) && b == 1) activate(top)
            return false
        }
        val (m, part) = hit
        activate(m.window)
        if (b != 1) return true
        when (part) {
            "close" -> close(m.window)
            "maximize" -> toggleMaximize(m)
            "minimize" -> minimize(m)
            "title", "resize" -> {
                val w = m.window
                drag = Drag(m, part == "resize", x, y, w.x, w.y, w.width, w.height)
            }
        }
        return true
    }

    /** Pointer motion. Returns true while dragging a window. */
    fun motion(x: Int, y: Int): Boolean {
        val d = drag ?: return false
        val w = d.m.window
        val dx = x - d.px; val dy = y - d.py
        if (d.resize) {
            setGeometry(d.m, w.x, w.y, max(48, d.w + dx), max(32, d.h + dy))
        } else {
            if (d.m.maximized && (dx * dx + dy * dy) > 64) {
                // Dragging a maximized window restores its size under the pointer.
                d.m.maximized = false
                val r = d.m.restore ?: intArrayOf(w.x, w.y, w.width / 2, w.height / 2)
                setGeometry(d.m, x - r[2] / 2, y + tb / 2, r[2], r[3])
                drag = Drag(d.m, false, x, y, w.x, w.y, w.width, w.height)
                return true
            }
            setGeometry(d.m, d.x + dx, d.y + dy, w.width, w.height)
        }
        return true
    }

    private fun finishDrag(d: Drag, x: Int, y: Int) {
        val m = d.m; val w = m.window
        if (!d.resize && snapping) {
            when {
                y <= 2 -> { maximize(m, d); return }
                x <= 2 -> { m.restore = intArrayOf(d.x, d.y, d.w, d.h); setGeometry(m, 0, tb, s.width / 2 - 2 * w.border, s.height - tb - 2 * w.border) }
                x >= s.width - 3 -> { m.restore = intArrayOf(d.x, d.y, d.w, d.h); setGeometry(m, s.width / 2, tb, s.width / 2 - 2 * w.border, s.height - tb - 2 * w.border) }
            }
        }
        remember(m)
    }

    private fun maximize(m: Managed, d: Drag? = null) {
        val w = m.window
        m.restore = d?.let { intArrayOf(it.x, it.y, it.w, it.h) } ?: intArrayOf(w.x, w.y, w.width, w.height)
        m.maximized = true
        setGeometry(m, 0, tb, s.width - 2 * w.border, s.height - tb - 2 * w.border)
    }

    fun toggleMaximize(m: Managed) {
        if (m.maximized) {
            m.maximized = false
            m.restore?.let { r -> setGeometry(m, r[0], r[1], r[2], r[3]) }
        } else maximize(m)
        remember(m)
    }

    fun setFullscreen(m: Managed, on: Boolean) {
        val w = m.window
        if (on) { m.restore = intArrayOf(w.x, w.y, w.width, w.height); m.fullscreen = true; w.border = 0; setGeometry(m, 0, 0, s.width, s.height) }
        else { m.fullscreen = false; m.restore?.let { r -> setGeometry(m, r[0], r[1], r[2], r[3]) } }
    }

    fun minimize(m: Managed) {
        m.minimized = true
        s.damage(0, 0, s.width, s.height)
        s.refocusAfterWm()
        onChange?.invoke()
    }

    private fun remember(m: Managed) {
        val cls = appClass(m.window)
        if (cls.isEmpty() || m.fullscreen || m.maximized) return
        val w = m.window
        val g = intArrayOf(w.x, w.y, w.width, w.height)
        placements[cls] = g
        onPlacement?.invoke(cls, g)
    }

    private fun setGeometry(m: Managed, x: Int, y: Int, w: Int, h: Int) {
        val v = listOf(0x1 to x, 0x2 to y, 0x4 to w, 0x8 to h)
        s.wmConfigure(m.window, v)
        constrain(m)
    }

    /** Raises, focuses and shows a window (switching to its desktop). */
    fun activate(w: Window) {
        val m = managed[w] ?: return
        if (m.desktop != current) switchDesktop(m.desktop)
        if (m.minimized) { m.minimized = false; s.damage(0, 0, s.width, s.height) }
        s.wmRaise(w)
        s.setFocus(w.id, 1)
        onChange?.invoke()
    }

    /** Asks the app to close the window (WM_DELETE_WINDOW), or disconnects it if it doesn't support that. */
    fun close(w: Window) {
        val protocols = w.props[atomProtocols]
        val supports = protocols != null && protocols.format == 32 && (0 until protocols.data.size / 4).any { i ->
            java.nio.ByteBuffer.wrap(protocols.data, i * 4, 4).order(w.owner?.order ?: java.nio.ByteOrder.LITTLE_ENDIAN).int == atomDelete
        }
        val owner = w.owner ?: return
        if (supports) s.send(owner, 33) { it.b(1, 32).i(4, w.id).i(8, atomProtocols).i(12, atomDelete).i(16, s.time) }
        else owner.close()
    }

    // ---------------------------------------------------------------- desktops & switching

    fun switchDesktop(d: Int) {
        if (d !in 0 until desktops || d == current) return
        current = d
        s.damage(0, 0, s.width, s.height)
        s.refocusAfterWm()
        onChange?.invoke()
    }

    fun moveToDesktop(w: Window, d: Int) {
        val m = managed[w] ?: return
        m.desktop = d.coerceIn(0, desktops - 1)
        s.damage(0, 0, s.width, s.height)
        onChange?.invoke()
    }

    /** Windows for the app switcher: id, title, desktop, minimized, focused. */
    fun windows(): List<WindowInfo> = managed.values.filter { it.window.mapped }.map {
        WindowInfo(it.window.id, title(it.window), appClass(it.window), it.desktop, it.minimized, isFocused(it.window))
    }

    data class WindowInfo(val id: Int, val title: String, val appClass: String, val desktop: Int, val minimized: Boolean, val focused: Boolean)

    /** Cycles focus to the next window on this desktop (Alt+Tab). */
    fun cycle() {
        val list = s.root.children.filter { it.mapped && managed[it]?.let { m -> m.desktop == current } == true }
        if (list.size < 2) { list.firstOrNull()?.let { activate(it) }; return }
        activate(list.first())
    }

    /**
     * Keyboard shortcuts: Super+1…4 desktop, Super+Up maximize, Super+Down minimize, Super+Left/Right snap,
     * Alt+Tab next window, Alt+F4 close. Returns true if handled.
     */
    fun key(code: Int, down: Boolean, mods: Int): Boolean {
        if (!down) return false
        val sup = mods and 64 != 0
        val alt = mods and 8 != 0
        val top = topmost()
        val m = top?.let { managed[it] }
        when {
            sup && code in 10..13 -> { switchDesktop(code - 10); return true }       // 1..4
            sup && code == 111 && m != null -> { toggleMaximize(m); return true }     // Up
            sup && code == 116 && m != null -> { minimize(m); return true }           // Down
            sup && code == 113 && m != null -> { snapHalf(m, left = true); return true }
            sup && code == 114 && m != null -> { snapHalf(m, left = false); return true }
            alt && code == 23 -> { cycle(); return true }                              // Tab
            alt && code == 70 && top != null -> { close(top); return true }           // F4
        }
        return false
    }

    private fun snapHalf(m: Managed, left: Boolean) {
        val w = m.window
        m.restore = intArrayOf(w.x, w.y, w.width, w.height)
        setGeometry(m, if (left) 0 else s.width / 2, tb, s.width / 2 - 2 * w.border, s.height - tb - 2 * w.border)
    }

    /** The shortcuts above, for the shortcut list and conflict checks. */
    val shortcuts = listOf(
        "Super+1…4" to "Switch desktop", "Super+Up" to "Maximize / restore", "Super+Down" to "Minimize",
        "Super+Left" to "Snap left", "Super+Right" to "Snap right", "Alt+Tab" to "Next window", "Alt+F4" to "Close window",
    )
}
