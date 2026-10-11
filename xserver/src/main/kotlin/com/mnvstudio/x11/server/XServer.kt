package com.mnvstudio.x11.server

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.math.max
import kotlin.math.min

/** A screen rectangle. */
data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

/** Event mask bits. */
object Ev {
    const val KEY_PRESS = 1 shl 0; const val KEY_RELEASE = 1 shl 1; const val BUTTON_PRESS = 1 shl 2; const val BUTTON_RELEASE = 1 shl 3
    const val ENTER = 1 shl 4; const val LEAVE = 1 shl 5; const val POINTER_MOTION = 1 shl 6; const val MOTION_HINT = 1 shl 7
    const val BUTTON_MOTION = 1 shl 13; const val EXPOSURE = 1 shl 15; const val STRUCTURE = 1 shl 17
    const val SUBSTRUCTURE = 1 shl 19; const val REDIRECT = 1 shl 20; const val FOCUS = 1 shl 21; const val PROPERTY = 1 shl 22
    const val OWNER_GRAB_BUTTON = 1 shl 24
}

/**
 * An X11 (core protocol) server with a software framebuffer. Every window keeps its own pixels, so windows are
 * composited into [framebuffer] and never need re-exposing when uncovered.
 *
 * All state is guarded by the server's monitor: request handling, input injection and [render] all synchronize on it.
 */
class XServer(val width: Int, val height: Int, val dpi: Int = 96) {
    val atoms = Atoms()
    val keyboard = Keyboard()
    val resources = HashMap<Int, Resource>()
    val clients = ArrayList<XClient>()
    val root = Window(ROOT, null, null, 0, 0, width, height, 0, 1, 24)
    /** Composited screen, 0xAARRGGBB, refreshed by [render]. */
    val framebuffer = IntArray(width * height)
    private val start = System.currentTimeMillis()

    /** Top-level windows fill the screen when mapped, unless a window manager is running. */
    @Volatile var fitToScreen = true
    /** Called (on any thread) when the screen changed and [render] has something to draw. */
    @Volatile var onDamage: (() -> Unit)? = null
    /** Called when clients connect or disconnect, with the number connected. */
    @Volatile var onClients: ((Int) -> Unit)? = null
    @Volatile var onBell: (() -> Unit)? = null

    private var dirty: Rect? = null

    /** The built-in window manager, when turned on (and no external window manager is running). */
    var wm: WindowManager? = null; private set

    @Synchronized
    fun setWindowManager(on: Boolean) {
        wm = if (on) (wm ?: WindowManager(this)) else null
        damage(0, 0, width, height)
    }

    private fun activeWm(): WindowManager? = wm?.takeIf { !hasWindowManager() }

    // Input state.
    var pointerX = width / 2; private set
    var pointerY = height / 2; private set
    private var buttons = 0
    private val keysDown = BooleanArray(256)
    private var capsLock = false
    private var pointerWindow: Window = root
    /** Focus: 0 = None, 1 = PointerRoot, otherwise a window id. */
    var focus = 1; private set
    private var focusRevert = 1

    class Grab(val client: XClient, val window: Window, val ownerEvents: Boolean, val mask: Int, val implicit: Boolean)
    private var pointerGrab: Grab? = null
    private var keyboardGrab: Grab? = null

    class Selection(val window: Int, val client: XClient?, val time: Int)
    val selections = HashMap<Int, Selection>()

    private var nextClient = 1
    private val listeners = ArrayList<java.io.Closeable>()

    init {
        root.mapped = true
        root.bgPixel = 0x2B3A4A
        root.colormap = COLORMAP
        resources[ROOT] = root
        resources[COLORMAP] = Colormap(COLORMAP, null)
        clearBackground(root, 0, 0, width, height)
        damage(0, 0, width, height)
    }

    val time: Int get() = (System.currentTimeMillis() - start).toInt()
    val modState: Int get() {
        var s = buttons
        for (k in keysDown.indices) if (keysDown[k]) s = s or keyboard.modBit(k)
        s = s and 2.inv()
        return if (capsLock) s or 2 else s
    }

    // ---------------------------------------------------------------- connections

    /** Accepts TCP connections on port 6000+[display] (DISPLAY=127.0.0.1:[display]) on a background thread. */
    fun listenTcp(display: Int, loopbackOnly: Boolean = true) {
        // Not getLoopbackAddress(): on Android that is ::1, and clients connect to 127.0.0.1.
        val ss = if (loopbackOnly) ServerSocket(6000 + display, 16, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) else ServerSocket(6000 + display)
        addListener(ss)
        Thread({
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                val from = s.inetAddress
                if (!from.isLoopbackAddress && !allowed(from.hostAddress ?: "")) {
                    onRejected?.invoke(from.hostAddress ?: "?")
                    runCatching { s.close() }
                    continue
                }
                s.tcpNoDelay = true
                serve(s.getInputStream(), s.getOutputStream(), if (from.isLoopbackAddress) "local" else from.hostAddress ?: "?") { runCatching { s.close() } }
            }
        }, "x11-accept-tcp").apply { isDaemon = true }.start()
    }

    /**
     * Addresses allowed to connect over the network (loopback always is). Entries are exact IPs or prefixes ending in
     * '*' (e.g. "192.168.1.*"); an empty list allows nobody from outside.
     */
    @Volatile var allowList: List<String> = emptyList()
    /** Called with the address of a refused network connection. */
    @Volatile var onRejected: ((String) -> Unit)? = null

    fun allowed(address: String): Boolean = allowList.any { rule ->
        val r = rule.trim()
        r.isNotEmpty() && if (r.endsWith("*")) address.startsWith(r.dropLast(1)) else address == r
    }

    /** Serves one client connection (any transport) on its own threads. */
    fun serve(input: InputStream, output: OutputStream, peer: String = "local", close: () -> Unit) {
        val (c, n) = synchronized(this) { XClient(this, nextClient++, input, output, close).also { it.peer = peer; clients += it } to clients.size }
        onClients?.invoke(n)
        c.start()
    }

    fun addListener(l: java.io.Closeable) = synchronized(listeners) { listeners += l }

    /** Stops listening and disconnects every client. */
    fun shutdown() {
        synchronized(listeners) { listeners.forEach { runCatching { it.close() } }; listeners.clear() }
        synchronized(this) { clients.toList() }.forEach { it.close() }
    }

    @Synchronized
    internal fun removeClient(c: XClient) {
        if (!clients.remove(c)) return
        resources.values.filter { it.owner === c && it is Window }.forEach { if (resources.containsKey(it.id)) destroy(it as Window) }
        resources.values.removeAll { it.owner === c }
        fun strip(w: Window) { w.masks.remove(c); w.children.forEach(::strip) }
        strip(root)
        if (pointerGrab?.client === c) pointerGrab = null
        if (keyboardGrab?.client === c) keyboardGrab = null
        selections.values.removeAll { it.client === c }
        onClients?.invoke(clients.size)
    }

    // ---------------------------------------------------------------- lookups

    fun window(id: Int) = resources[id] as? Window ?: throw XError(Err.WINDOW, id)
    fun drawable(id: Int) = resources[id] as? Drawable ?: throw XError(Err.DRAWABLE, id)
    fun pixmap(id: Int) = resources[id] as? Pixmap ?: throw XError(Err.PIXMAP, id)
    fun gc(id: Int) = resources[id] as? GContext ?: throw XError(Err.GCONTEXT, id)
    fun font(id: Int): XFont = when (val r = resources[id]) {
        is FontRef -> r.font
        is GContext -> r.font ?: throw XError(Err.FONT, id)
        else -> throw XError(Err.FONT, id)
    }

    // ---------------------------------------------------------------- events

    fun send(c: XClient, code: Int, fill: (Msg) -> Unit) {
        val m = Msg(32, c.order)
        m.b(0, code)
        fill(m)
        m.s(2, c.seq)
        c.write(m.array())
    }

    /** Sends to every client selecting any bit of [mask] on [w]; returns whether anyone got it. */
    fun deliver(w: Window, mask: Int, code: Int, fill: (Msg) -> Unit): Boolean {
        var any = false
        for ((c, m) in w.masks.entries.toList()) if (m and mask != 0) { send(c, code, fill); any = true }
        return any
    }

    private fun redirectClient(parent: Window?): XClient? = parent?.masks?.entries?.firstOrNull { it.value and Ev.REDIRECT != 0 }?.key

    fun broadcastMapping(first: Int, count: Int) {
        for (c in clients) send(c, 34) { it.b(4, 1).b(5, first).b(6, count) }
    }

    // ---------------------------------------------------------------- drawing & composition

    fun damage(x: Int, y: Int, w: Int, h: Int) {
        val x0 = max(0, x); val y0 = max(0, y); val x1 = min(width, x + w); val y1 = min(height, y + h)
        if (x0 >= x1 || y0 >= y1) return
        val d = dirty
        dirty = if (d == null) Rect(x0, y0, x1 - x0, y1 - y0)
        else { val nx = min(d.x, x0); val ny = min(d.y, y0); Rect(nx, ny, max(d.x + d.w, x1) - nx, max(d.y + d.h, y1) - ny) }
        onDamage?.invoke()
    }

    /** Marks what a drawing call touched on [d] as needing composition. */
    fun drew(d: Drawable, p: Paint) {
        if (d is Window && p.touched && d.viewable) damage(d.absX + p.minX, d.absY + p.minY, p.maxX - p.minX + 1, p.maxY - p.minY + 1)
    }

    private fun damageWindow(w: Window) {
        if (!w.viewable) return
        damage(w.absX - w.border, w.absY - w.border, w.width + 2 * w.border, w.height + 2 * w.border)
        activeWm()?.frame(w)?.let { damage(it.x, it.y, it.w, it.h) }
    }

    /**
     * Composites what changed since the last call into [framebuffer] and passes the changed rectangle to [use],
     * still holding the lock so the pixels can be copied consistently. Returns false if nothing changed.
     */
    @Synchronized
    fun render(use: (IntArray, Rect) -> Unit): Boolean {
        val r = dirty ?: return false
        dirty = null
        composite(r)
        use(framebuffer, r)
        return true
    }

    private fun composite(r: Rect) {
        val rp = root.pixels
        for (y in r.y until r.y + r.h) {
            val o = y * width
            for (x in r.x until r.x + r.w) framebuffer[o + x] = rp[o + x] or OPAQUE
        }
        val m = activeWm()
        for (c in root.children) {
            if (m != null && c.mapped) {
                if (m.hidden(c)) continue
                m.drawFrame(framebuffer, width, c, r)
            }
            paint(c, r)
        }
    }

    private fun paint(w: Window, clip: Rect) {
        if (!w.mapped) return
        val b = w.border
        val outer = intersect(clip, Rect(w.absX - b, w.absY - b, w.width + 2 * b, w.height + 2 * b)) ?: return
        val inner = intersect(clip, Rect(w.absX, w.absY, w.width, w.height))
        if (b > 0) {
            val bp = w.borderPixel or OPAQUE
            for (y in outer.y until outer.y + outer.h) for (x in outer.x until outer.x + outer.w) {
                if (inner == null || x < inner.x || x >= inner.x + inner.w || y < inner.y || y >= inner.y + inner.h) framebuffer[y * width + x] = bp
            }
        }
        if (inner == null || w.cls == 2) return
        val px = w.pixels; val ww = w.width; val ax = w.absX; val ay = w.absY
        for (y in inner.y until inner.y + inner.h) {
            val src = (y - ay) * ww - ax; val dst = y * width
            for (x in inner.x until inner.x + inner.w) framebuffer[dst + x] = px[src + x] or OPAQUE
        }
        for (c in w.children) paint(c, inner)
    }

    private fun intersect(a: Rect, b: Rect): Rect? {
        val x0 = max(a.x, b.x); val y0 = max(a.y, b.y); val x1 = min(a.x + a.w, b.x + b.w); val y1 = min(a.y + a.h, b.y + b.h)
        return if (x0 < x1 && y0 < y1) Rect(x0, y0, x1 - x0, y1 - y0) else null
    }

    /** Reads back screen pixels for GetImage on a window (includes children and overlapping windows, as on X). */
    fun screenPixel(x: Int, y: Int): Int {
        dirty?.let { composite(it) }
        return if (x in 0 until width && y in 0 until height) framebuffer[y * width + x] and 0xFFFFFF else 0
    }

    fun clearBackground(w: Window, x: Int, y: Int, ww: Int, hh: Int) {
        if (w.cls == 2) return
        var src: Window = w
        while (src.bgParentRelative && src.parent != null) src = src.parent!!
        val tile = src.bgPixmap
        val pixel = src.bgPixel
        if (tile == null && pixel < 0) return
        val x0 = max(0, x); val y0 = max(0, y); val x1 = min(w.width, x + ww); val y1 = min(w.height, y + hh)
        for (yy in y0 until y1) for (xx in x0 until x1) {
            w.pixels[yy * w.width + xx] =
                if (tile != null && tile.width > 0 && tile.height > 0) tile.pixels[(yy % tile.height) * tile.width + xx % tile.width] else pixel
        }
        if (x0 < x1 && y0 < y1 && w.viewable) damage(w.absX + x0, w.absY + y0, x1 - x0, y1 - y0)
    }

    fun sendExpose(w: Window, x: Int, y: Int, ww: Int, hh: Int) {
        if (w.cls == 2 || ww <= 0 || hh <= 0) return
        deliver(w, Ev.EXPOSURE, 12) { it.i(4, w.id).s(8, x).s(10, y).s(12, ww).s(14, hh).s(16, 0) }
    }

    /** Paints backgrounds and sends Expose for [w] and its viewable descendants, after they become visible. */
    private fun reveal(w: Window) {
        if (!w.viewable) return
        clearBackground(w, 0, 0, w.width, w.height)
        sendExpose(w, 0, 0, w.width, w.height)
        for (c in w.children) reveal(c)
    }

    // ---------------------------------------------------------------- window tree

    fun createWindow(c: XClient, id: Int, parent: Window, x: Int, y: Int, w: Int, h: Int, border: Int, cls: Int, depth: Int) {
        if (w == 0 || h == 0) throw XError(Err.VALUE, 0)
        if (parent.cls == 2 && cls != 2) throw XError(Err.MATCH)
        val win = Window(id, c, parent, x, y, w, h, if (cls == 2) 0 else border, cls, if (cls == 2) 0 else (if (depth == 0) parent.depth else depth))
        win.colormap = COLORMAP
        parent.children += win
        resources[id] = win
        deliver(parent, Ev.SUBSTRUCTURE, 16) {
            it.i(4, parent.id).i(8, id).s(12, x).s(14, y).s(16, w).s(18, h).s(20, win.border).b(22, 0)
        }
    }

    fun setAttributes(c: XClient, w: Window, values: List<Pair<Int, Int>>) {
        for ((bit, v) in values) when (bit) {
            0x1 -> when (v) {
                0 -> { w.bgPixel = -1; w.bgPixmap = null; w.bgParentRelative = false }
                1 -> { w.bgParentRelative = true; w.bgPixmap = null }
                else -> { w.bgPixmap = pixmap(v); w.bgParentRelative = false }
            }
            0x2 -> { w.bgPixel = v and 0xFFFFFF; w.bgPixmap = null; w.bgParentRelative = false }
            0x8 -> w.borderPixel = v and 0xFFFFFF
            0x10 -> w.bitGravity = v
            0x20 -> w.winGravity = v
            0x200 -> w.overrideRedirect = v and 1 != 0
            0x800 -> {
                for ((other, m) in w.masks) if (other !== c) {
                    if (v and m and Ev.REDIRECT != 0) throw XError(Err.ACCESS)
                    if (v and m and Ev.BUTTON_PRESS != 0) throw XError(Err.ACCESS)
                }
                if (v == 0) w.masks.remove(c) else w.masks[c] = v
            }
            0x1000 -> w.doNotPropagate = v
            0x2000 -> w.colormap = v
            0x4000 -> w.cursor = v
        }
    }

    private fun hasWindowManager() = redirectClient(root) != null

    fun map(c: XClient?, w: Window) {
        if (w.mapped) return
        val wm = redirectClient(w.parent)
        if (!w.overrideRedirect && wm != null && wm !== c) {
            send(wm, 20) { it.i(4, w.parent!!.id).i(8, w.id) }
            return
        }
        val bwm = activeWm()
        if (bwm != null && w.parent === root && !w.overrideRedirect) {
            bwm.manage(w)
            notifyConfigure(w)
        } else if (fitToScreen && w.parent === root && !w.overrideRedirect && !hasWindowManager() &&
            (w.x != 0 || w.y != 0 || w.width != width || w.height != height || w.border != 0)) {
            w.x = 0; w.y = 0; w.border = 0
            w.resize(width, height)
            notifyConfigure(w)
        }
        w.mapped = true
        val parent = w.parent!!
        deliver(w, Ev.STRUCTURE, 19) { it.i(4, w.id).i(8, w.id).b(12, if (w.overrideRedirect) 1 else 0) }
        deliver(parent, Ev.SUBSTRUCTURE, 19) { it.i(4, parent.id).i(8, w.id).b(12, if (w.overrideRedirect) 1 else 0) }
        if (w.viewable) { reveal(w); damageWindow(w) }
        updatePointerWindow()
    }

    fun mapSubwindows(c: XClient?, w: Window) { for (ch in w.children.toList()) map(c, ch) }

    fun unmap(w: Window, fromConfigure: Boolean = false) {
        if (!w.mapped || w === root) return
        damageWindow(w)
        w.mapped = false
        val parent = w.parent!!
        val fc = if (fromConfigure) 1 else 0
        deliver(w, Ev.STRUCTURE, 18) { it.i(4, w.id).i(8, w.id).b(12, fc) }
        deliver(parent, Ev.SUBSTRUCTURE, 18) { it.i(4, parent.id).i(8, w.id).b(12, fc) }
        loseFocusIfHidden()
        updatePointerWindow()
    }

    fun unmapSubwindows(w: Window) { for (ch in w.children.reversed()) unmap(ch) }

    fun destroy(w: Window) {
        if (w === root) return
        unmap(w)
        destroyTree(w)
        w.parent?.children?.remove(w)
        if (pointerWindow === w || w.isAncestorOf(pointerWindow)) pointerWindow = w.parent ?: root
        updatePointerWindow()
    }

    private fun destroyTree(w: Window) {
        for (ch in w.children.toList()) destroyTree(ch)
        w.children.clear()
        val parent = w.parent!!
        deliver(w, Ev.STRUCTURE, 17) { it.i(4, w.id).i(8, w.id) }
        deliver(parent, Ev.SUBSTRUCTURE, 17) { it.i(4, parent.id).i(8, w.id) }
        resources.remove(w.id)
        wm?.forget(w)
        if (focus == w.id) revertFocus()
        if (pointerGrab?.window === w) pointerGrab = null
        if (keyboardGrab?.window === w) keyboardGrab = null
        selections.values.removeAll { it.window == w.id }
        if (pointerWindow === w) pointerWindow = parent
    }

    fun destroySubwindows(w: Window) { for (ch in w.children.reversed()) destroy(ch) }

    private fun notifyConfigure(w: Window) {
        val parent = w.parent ?: return
        val i = parent.children.indexOf(w)
        val above = if (i > 0) parent.children[i - 1].id else 0
        val fill: (Msg) -> Unit = {
            it.i(8, w.id).i(12, above).s(16, w.x).s(18, w.y).s(20, w.width).s(22, w.height).s(24, w.border).b(26, if (w.overrideRedirect) 1 else 0)
        }
        deliver(w, Ev.STRUCTURE, 22) { it.i(4, w.id); fill(it) }
        deliver(parent, Ev.SUBSTRUCTURE, 22) { it.i(4, parent.id); fill(it) }
    }

    fun configure(c: XClient, w: Window, mask: Int, values: List<Pair<Int, Int>>) {
        if (w === root) return
        val v = values.toMap()
        val wm = redirectClient(w.parent)
        if (!w.overrideRedirect && wm != null && wm !== c) {
            val parent = w.parent!!
            send(wm, 23) {
                it.b(1, v[0x40] ?: 0).i(4, parent.id).i(8, w.id).i(12, v[0x20] ?: 0)
                    .s(16, v[0x1] ?: w.x).s(18, v[0x2] ?: w.y).s(20, v[0x4] ?: w.width).s(22, v[0x8] ?: w.height)
                    .s(24, v[0x10] ?: w.border).s(26, mask)
            }
            return
        }
        // With no window manager, full-screen top-level windows stay full-screen.
        val pinned = fitToScreen && w.parent === root && !w.overrideRedirect && !hasWindowManager() && activeWm() == null
        damageWindow(w)
        if (!pinned) {
            v[0x1]?.let { w.x = it.toShort().toInt() }
            v[0x2]?.let { w.y = it.toShort().toInt() }
            v[0x10]?.let { w.border = it and 0xFFFF }
        }
        val nw = if (pinned) w.width else v[0x4]?.and(0xFFFF) ?: w.width
        val nh = if (pinned) w.height else v[0x8]?.and(0xFFFF) ?: w.height
        if (nw == 0 || nh == 0) throw XError(Err.VALUE)
        val resized = nw != w.width || nh != w.height
        if (resized) w.resize(nw, nh)
        v[0x40]?.let { mode ->
            val sibs = w.parent!!.children
            val sib = v[0x20]?.let { id -> sibs.firstOrNull { it.id == id } ?: throw XError(Err.MATCH) }
            sibs.remove(w)
            when (mode) {
                0, 2, 4 -> if (sib != null) sibs.add(sibs.indexOf(sib) + 1, w) else sibs.add(w)
                else -> if (sib != null) sibs.add(sibs.indexOf(sib), w) else sibs.add(0, w)
            }
        }
        activeWm()?.clientConfigured(w)
        notifyConfigure(w)
        if (resized && w.viewable) {
            clearBackground(w, 0, 0, w.width, w.height)
            sendExpose(w, 0, 0, w.width, w.height)
        }
        damageWindow(w)
        updatePointerWindow()
    }

    /** Moves/resizes a window for the window manager (no redirect, no full-screen pinning). */
    internal fun wmConfigure(w: Window, values: List<Pair<Int, Int>>) {
        damageWindow(w)
        val v = values.toMap()
        v[0x1]?.let { w.x = it }; v[0x2]?.let { w.y = it }
        val nw = (v[0x4] ?: w.width).coerceAtLeast(1); val nh = (v[0x8] ?: w.height).coerceAtLeast(1)
        val resized = nw != w.width || nh != w.height
        if (resized) w.resize(nw, nh)
        notifyConfigure(w)
        if (resized && w.viewable) { clearBackground(w, 0, 0, w.width, w.height); sendExpose(w, 0, 0, w.width, w.height) }
        damageWindow(w)
        updatePointerWindow()
    }

    /** Puts a top-level window above its siblings. */
    internal fun wmRaise(w: Window) {
        val sibs = w.parent?.children ?: return
        if (sibs.lastOrNull() === w) return
        sibs.remove(w); sibs.add(w)
        notifyConfigure(w)
        damageWindow(w)
        updatePointerWindow()
    }

    /** After minimizing or switching desktop: move focus off hidden windows. */
    internal fun refocusAfterWm() {
        val m = activeWm() ?: return
        val f = resources[focus] as? Window
        if (f != null && generateSequence(f) { it.parent }.any { m.hidden(it) }) {
            val top = root.children.lastOrNull { it.mapped && m.isManaged(it) && !m.hidden(it) }
            setFocus(top?.id ?: 1, 1)
        }
        updatePointerWindow()
    }

    fun reparent(c: XClient, w: Window, parent: Window, x: Int, y: Int) {
        if (w === root || w === parent || w.isAncestorOf(parent)) throw XError(Err.MATCH)
        val wasMapped = w.mapped
        unmap(w)
        val old = w.parent!!
        old.children.remove(w)
        parent.children += w
        w.parent = parent; w.x = x; w.y = y
        val fill: (Msg) -> Unit = { it.i(8, w.id).i(12, parent.id).s(16, x).s(18, y).b(20, if (w.overrideRedirect) 1 else 0) }
        deliver(w, Ev.STRUCTURE, 21) { it.i(4, w.id); fill(it) }
        deliver(old, Ev.SUBSTRUCTURE, 21) { it.i(4, old.id); fill(it) }
        if (parent !== old) deliver(parent, Ev.SUBSTRUCTURE, 21) { it.i(4, parent.id); fill(it) }
        if (wasMapped) map(c, w)
    }

    fun circulate(w: Window, lowerHighest: Boolean) {
        if (w.children.size < 2) return
        val ch = if (lowerHighest) w.children.removeAt(w.children.lastIndex).also { w.children.add(0, it) }
        else w.children.removeAt(0).also { w.children.add(it) }
        val place = if (lowerHighest) 1 else 0
        deliver(ch, Ev.STRUCTURE, 26) { it.i(4, ch.id).i(8, ch.id).b(16, place) }
        deliver(w, Ev.SUBSTRUCTURE, 26) { it.i(4, w.id).i(8, ch.id).b(16, place) }
        damageWindow(ch)
        updatePointerWindow()
    }

    // ---------------------------------------------------------------- properties & selections

    fun propertyNotify(w: Window, atom: Int, deleted: Boolean) {
        deliver(w, Ev.PROPERTY, 28) { it.i(4, w.id).i(8, atom).i(12, time).b(16, if (deleted) 1 else 0) }
    }

    fun setSelectionOwner(c: XClient, owner: Int, selection: Int, t: Int) {
        val old = selections[selection]
        if (old?.client != null && (old.client !== c || old.window != owner)) {
            send(old.client, 29) { it.i(4, t).i(8, old.window).i(12, selection) }
        }
        if (owner == 0) selections.remove(selection) else selections[selection] = Selection(owner, c, if (t == 0) time else t)
        // A Linux app copied something: fetch it for the Android clipboard.
        if (owner != 0 && (selection == atomClipboard || (syncPrimary && selection == Atoms.PRIMARY))) {
            send(c, 30) { it.i(4, t).i(8, owner).i(12, CLIPWIN).i(16, selection).i(20, atomUtf8).i(24, atomClipProp) }
        }
    }

    fun convertSelection(c: XClient, requestor: Int, selection: Int, target: Int, property: Int, t: Int) {
        val s = selections[selection]
        when {
            s?.client != null ->
                send(s.client, 30) { it.i(4, t).i(8, s.window).i(12, requestor).i(16, selection).i(20, target).i(24, property) }
            s != null && s.window == CLIPWIN && clipText != null -> {
                val prop = answerClipboard(window(requestor), target, if (property == 0) target else property)
                send(c, 31) { it.i(4, t).i(8, requestor).i(12, selection).i(16, target).i(20, prop) }
            }
            else -> send(c, 31) { it.i(4, t).i(8, requestor).i(12, selection).i(16, target).i(20, 0) }
        }
    }

    // ---------------------------------------------------------------- clipboard bridge

    /** Called (on a server thread) with text a Linux app copied. */
    @Volatile var onClipboard: ((String) -> Unit)? = null
    /** Also sync the PRIMARY selection (text selected with the mouse) to Android. */
    @Volatile var syncPrimary = false
    private var clipText: String? = null
    private val atomClipboard = atoms.intern("CLIPBOARD")
    private val atomUtf8 = atoms.intern("UTF8_STRING")
    private val atomTargets = atoms.intern("TARGETS")
    private val atomText = atoms.intern("TEXT")
    private val atomClipProp = atoms.intern("_MNV_CLIPBOARD")
    /** Hidden window that owns selections for Android and receives copied text; not in the window tree. */
    private val clipWindow = Window(CLIPWIN, null, root, -100, -100, 1, 1, 0, 2, 0).also { resources[CLIPWIN] = it }

    /** Makes Android's clipboard text the X CLIPBOARD and PRIMARY selections. */
    @Synchronized
    fun setClipboard(text: String) {
        if (text == clipText && selections[atomClipboard]?.window == CLIPWIN) return
        clipText = text
        for (sel in intArrayOf(atomClipboard, Atoms.PRIMARY)) {
            val old = selections[sel]
            if (old?.client != null) send(old.client, 29) { it.i(4, time).i(8, old.window).i(12, sel) }
            selections[sel] = Selection(CLIPWIN, null, time)
        }
    }

    /** Writes the clipboard to [requestor] in the [target] format; returns the property used, or 0 if unsupported. */
    private fun answerClipboard(requestor: Window, target: Int, property: Int): Int {
        val text = clipText ?: return 0
        val prop = when (target) {
            atomTargets -> {
                val list = intArrayOf(atomTargets, atomUtf8, Atoms.STRING, atomText)
                val b = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                list.forEach { b.putInt(it) }
                Property(Atoms.ATOM, 32, b.array())
            }
            atomUtf8, atomText -> Property(atomUtf8, 8, text.toByteArray(Charsets.UTF_8))
            Atoms.STRING -> Property(Atoms.STRING, 8, text.toByteArray(Charsets.ISO_8859_1))
            else -> return 0
        }
        requestor.props[property] = prop
        propertyNotify(requestor, property, false)
        return property
    }

    /** SelectionNotify sent to our hidden window: the copied text is in its property. */
    internal fun clipboardReply(property: Int) {
        val p = clipWindow.props.remove(atomClipProp) ?: return
        if (property == 0) return
        val text = if (p.type == Atoms.STRING) String(p.data, Charsets.ISO_8859_1) else String(p.data, Charsets.UTF_8)
        clipText = text
        onClipboard?.invoke(text)
    }

    /** Publishes X resources on the root window (e.g. Xft.dpi), read by toolkits at startup. */
    @Synchronized
    fun setResources(text: String) {
        root.props[atoms.intern("RESOURCE_MANAGER")] = Property(Atoms.STRING, 8, text.toByteArray(Charsets.ISO_8859_1))
    }

    // ---------------------------------------------------------------- focus & grabs

    fun setFocus(value: Int, revertTo: Int) {
        if (value == focus) { focusRevert = revertTo; return }
        val old = focus
        focus = value; focusRevert = revertTo
        (resources[old] as? Window)?.let { w -> deliver(w, Ev.FOCUS, 10) { it.b(1, 3).i(4, w.id).b(8, 0) } }
        (resources[value] as? Window)?.let { w -> deliver(w, Ev.FOCUS, 9) { it.b(1, 3).i(4, w.id).b(8, 0) } }
    }

    private fun revertFocus() {
        val w = resources[focus] as? Window
        focus = when (focusRevert) { 2 -> w?.parent?.id ?: 1; 1 -> 1; else -> 0 }
    }

    private fun loseFocusIfHidden() {
        val w = resources[focus] as? Window ?: return
        if (!w.viewable) revertFocus()
    }

    fun grabPointer(c: XClient, w: Window, ownerEvents: Boolean, mask: Int): Int {
        val g = pointerGrab
        if (g != null && g.client !== c) return 1 // AlreadyGrabbed
        if (!w.viewable) return 3 // NotViewable
        pointerGrab = Grab(c, w, ownerEvents, mask, false)
        return 0
    }

    fun ungrabPointer(c: XClient) { if (pointerGrab?.client === c) { pointerGrab = null; updatePointerWindow() } }

    fun grabKeyboard(c: XClient, w: Window, ownerEvents: Boolean): Int {
        val g = keyboardGrab
        if (g != null && g.client !== c) return 1
        if (!w.viewable) return 3
        keyboardGrab = Grab(c, w, ownerEvents, Ev.KEY_PRESS or Ev.KEY_RELEASE, false)
        return 0
    }

    fun ungrabKeyboard(c: XClient) { if (keyboardGrab?.client === c) keyboardGrab = null }

    // ---------------------------------------------------------------- input

    /** Deepest viewable window containing the screen point. */
    fun windowAt(x: Int, y: Int): Window {
        var w = root
        outer@ while (true) {
            for (c in w.children.asReversed()) {
                if (!c.mapped) continue
                if (w === root && activeWm()?.hidden(c) == true) continue
                val ox = c.absX - c.border; val oy = c.absY - c.border
                if (x >= ox && y >= oy && x < ox + c.width + 2 * c.border && y < oy + c.height + 2 * c.border) { w = c; continue@outer }
            }
            return w
        }
    }

    private fun childOn(path: Window, of: Window): Window? {
        var p: Window? = path
        while (p != null && p.parent !== of) p = p.parent
        return p
    }

    private fun pointerFields(m: Msg, w: Window, child: Int, state: Int) {
        m.i(4, time).i(8, ROOT).i(12, w.id).i(16, child).s(20, pointerX).s(22, pointerY)
            .s(24, pointerX - w.absX).s(26, pointerY - w.absY).s(28, state).b(30, 1)
    }

    private fun focusFlag(w: Window): Int {
        val f = resources[focus] as? Window
        return if (focus == 1 || f === w || f?.isAncestorOf(w) == true) 1 else 0
    }

    private fun crossing(code: Int, w: Window, detail: Int, child: Window?) {
        val mask = if (code == 7) Ev.ENTER else Ev.LEAVE
        val state = modState
        deliver(w, mask, code) {
            it.b(1, detail); pointerFields(it, w, child?.id ?: 0, state); it.b(30, 0).b(31, 2 or focusFlag(w))
        }
    }

    /** Sends Enter/Leave events when the window under the pointer changes. */
    private fun updatePointerWindow() {
        if (pointerGrab != null) return
        val now = windowAt(pointerX, pointerY)
        val old = pointerWindow
        if (now === old) return
        pointerWindow = now
        if (!resources.containsKey(old.id)) return
        when {
            old.isAncestorOf(now) -> {
                crossing(8, old, 2, childOn(now, old))
                generateSequence(now.parent) { it.parent }.takeWhile { it !== old }.toList().asReversed()
                    .forEach { crossing(7, it, 1, childOn(now, it)) }
                crossing(7, now, 0, null)
            }
            now.isAncestorOf(old) -> {
                crossing(8, old, 0, null)
                generateSequence(old.parent) { it.parent }.takeWhile { it !== now }.forEach { crossing(8, it, 1, childOn(old, it)) }
                crossing(7, now, 2, childOn(old, now))
            }
            else -> {
                val common = generateSequence(old.parent) { it.parent }.first { it.isAncestorOf(now) }
                crossing(8, old, 3, null)
                generateSequence(old.parent) { it.parent }.takeWhile { it !== common }.forEach { crossing(8, it, 4, childOn(old, it)) }
                generateSequence(now.parent) { it.parent }.takeWhile { it !== common }.toList().asReversed()
                    .forEach { crossing(7, it, 4, childOn(now, it)) }
                crossing(7, now, 3, null)
            }
        }
    }

    /**
     * Delivers a pointer or key event from [src] up the window tree (or to an active grab).
     * Returns the window and client that received it, for implicit grabs.
     */
    private fun deliverDevice(src: Window, code: Int, mask: Int, detail: Int, state: Int, grab: Grab?, stopAt: Window? = null): Pair<Window, XClient>? {
        if (grab != null) {
            if (grab.ownerEvents) {
                // Normal delivery, but only to the grabbing client.
                var w: Window? = src; var child: Window? = null
                while (w != null) {
                    val m = w.masks[grab.client]
                    if (m != null && m and mask != 0) {
                        val ww = w; val ch = child
                        send(grab.client, code) { it.b(1, detail); pointerFields(it, ww, ch?.id ?: 0, state) }
                        return w to grab.client
                    }
                    if (w === stopAt || w.doNotPropagate and mask != 0) break
                    child = w; w = w.parent
                }
            }
            if (grab.mask and mask == 0) return null
            val w = grab.window
            val child = if (w.isAncestorOf(src)) childOn(src, w)?.id ?: 0 else 0
            send(grab.client, code) { it.b(1, detail); pointerFields(it, w, child, state) }
            return w to grab.client
        }
        var w: Window? = src
        var child: Window? = null
        while (w != null) {
            var got: XClient? = null
            for ((c, m) in w.masks.entries.toList()) if (m and mask != 0) {
                val d = if (code == 6 && m and Ev.MOTION_HINT != 0) 1 else detail
                val ww = w; val ch = child
                send(c, code) { it.b(1, d); pointerFields(it, ww, ch?.id ?: 0, state) }
                if (got == null) got = c
            }
            if (got != null) return w to got
            if (w === stopAt || w.doNotPropagate and mask != 0) return null
            child = w; w = w.parent
        }
        return null
    }

    private fun motionMask(): Int {
        var m = Ev.POINTER_MOTION
        if (buttons != 0) {
            m = m or Ev.BUTTON_MOTION
            for (b in 1..5) if (buttons and (0x80 shl b) != 0) m = m or (1 shl (7 + b))
        }
        return m
    }

    /** Records and replays injected input. */
    val recorder = InputRecorder(this)

    /** WM_NAME of a window, for lists. */
    fun windowTitle(w: Window): String = w.props[Atoms.WM_NAME]?.let { String(it.data, Charsets.ISO_8859_1) } ?: ""

    /** Moves the pointer to a screen position. */
    @Synchronized
    fun motion(x: Int, y: Int) {
        recorder.motion(x, y)
        val nx = x.coerceIn(0, width - 1); val ny = y.coerceIn(0, height - 1)
        if (nx == pointerX && ny == pointerY) return
        pointerX = nx; pointerY = ny
        if (activeWm()?.motion(nx, ny) == true) return
        updatePointerWindow()
        deliverDevice(windowAt(nx, ny), 6, motionMask(), 0, modState, pointerGrab)
    }

    /** Presses or releases mouse button 1..5 (4/5 are scroll up/down). */
    @Synchronized
    fun button(b: Int, down: Boolean) {
        recorder.button(b, down)
        val bit = 0x80 shl b
        // The window manager sees presses first (title bars) and the release that ends its drag.
        if (pointerGrab == null && (down || buttons and bit == 0) && activeWm()?.button(pointerX, pointerY, b, down) == true) return
        if (down == (buttons and bit != 0)) return
        val state = modState
        val src = windowAt(pointerX, pointerY)
        if (down) {
            buttons = buttons or bit
            val got = deliverDevice(src, 4, Ev.BUTTON_PRESS, b, state, pointerGrab)
            if (got != null && pointerGrab == null) {
                val m = got.first.masks[got.second] ?: 0
                pointerGrab = Grab(got.second, got.first, m and Ev.OWNER_GRAB_BUTTON != 0, m, true)
            }
        } else {
            buttons = buttons and bit.inv()
            deliverDevice(src, 5, Ev.BUTTON_RELEASE, b, state, pointerGrab)
            if (buttons == 0 && pointerGrab?.implicit == true) { pointerGrab = null; updatePointerWindow() }
        }
    }

    /** A click (press and release) of [b]. */
    fun click(b: Int) { button(b, true); button(b, false) }

    /** Presses or releases an X keycode. */
    @Synchronized
    fun key(code: Int, down: Boolean) {
        if (code !in 8..255) return
        recorder.key(code, down)
        if (!down && !keysDown[code]) return
        val state = modState
        if (down && keyboardGrab == null && activeWm()?.key(code, true, state) == true) return
        keysDown[code] = down
        if (down && code == Keyboard.CAPS) capsLock = !capsLock
        val f = when (focus) { 0 -> return; 1 -> root; else -> resources[focus] as? Window ?: root }
        val under = windowAt(pointerX, pointerY)
        val src = if (under === f || f.isAncestorOf(under)) under else f
        val mask = if (down) Ev.KEY_PRESS else Ev.KEY_RELEASE
        deliverDevice(src, if (down) 2 else 3, mask, code, state, keyboardGrab, stopAt = if (focus == 1) null else f)
    }

    /** Types one Unicode character, pressing Shift if the layout needs it and remapping a spare key if no key has it. */
    @Synchronized
    fun type(cp: Int) {
        val ks = Keyboard.keysymFor(cp)
        val (code, shift) = keyboard.find(ks) ?: keyboard.assignSpare(ks).let { broadcastMapping(it, 1); it to false }
        val holdShift = shift && !keysDown[Keyboard.SHIFT_L] && !keysDown[Keyboard.SHIFT_R]
        if (holdShift) key(Keyboard.SHIFT_L, true)
        key(code, true); key(code, false)
        if (holdShift) key(Keyboard.SHIFT_L, false)
    }

    /** Presses and releases the key producing keysym [ks] (e.g. 0xff0d Return), if the keyboard has one. */
    @Synchronized
    fun tapKeysym(ks: Int) {
        val code = keyboard.find(ks)?.first ?: return
        key(code, true); key(code, false)
    }

    @Synchronized
    fun isKeyDown(code: Int) = keysDown.getOrElse(code) { false }

    companion object {
        const val ROOT = 0x20
        const val COLORMAP = 0x21
        const val VISUAL = 0x22
        const val CLIPWIN = 0x23
        const val OPAQUE = 0xFF000000.toInt()
        const val VENDOR = "MNV Studio X11"
    }
}
