package com.mnvstudio.x11.xorg

import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's connection to a real X.Org server (Xvfb), the way XSDL and Termux:X11 drive theirs:
 *  - input through XTEST (pointer, buttons, wheel, keys; any Unicode character via a spare keycode, like xdotool),
 *  - the clipboard in both directions (it owns CLIPBOARD when Android has text; XFIXES says when Linux copies),
 *  - the current cursor image (XFIXES), so the app can draw the real I-beam, hand or resize arrows.
 * Works over TCP or a Unix socket (any pair of streams).
 */
class XorgClient private constructor(inp: InputStream, out: OutputStream, private val closer: Closeable, private val cookie: ByteArray?) {
    private val out: OutputStream = out.buffered()
    private val inp = DataInputStream(inp.buffered())
    var root = 0; private set
    var width = 0; private set
    var height = 0; private set
    private var idBase = 0
    private var minKeycode = 8
    private var maxKeycode = 255
    private var xtest = 0
    private var xfixes = 0
    private var xfixesEvent = 0
    /** keysym → (keycode, needs shift). */
    private val keymap = HashMap<Int, Pair<Int, Boolean>>()
    private var spare = -1
    private var shiftCode = 0
    private var seq = 0
    private val pending = ConcurrentHashMap<Int, (ByteBuffer?) -> Unit>()
    private val atoms = HashMap<String, Int>()
    private var window = 0
    private var damageExt = 0
    private var damageEvent = 0
    private var damage = 0
    /** Region changed since [takeDirty] was last called: x0, y0, x1, y1 (empty when x0 >= x1). */
    private val dirty = intArrayOf(0, 0, 0, 0)
    /** True when the server reports changes (DAMAGE); without it the screen must be copied every frame. */
    val tracksDamage get() = damage != 0
    @Volatile private var closed = false

    /** Text copied by a Linux app (called on the reader thread). */
    @Volatile var onClipboard: ((String) -> Unit)? = null
    /** The cursor changed: ARGB pixels (premultiplied), size and hotspot (reader thread). */
    @Volatile var onCursor: ((IntArray, Int, Int, Int, Int) -> Unit)? = null
    @Volatile private var ownText: String? = null

    companion object {
        /** [cookie]: the 16-byte MIT-MAGIC-COOKIE-1 the server was started with (null when it runs without access control). */
        fun connect(host: String, port: Int, cookie: ByteArray? = null, timeoutMs: Int = 3000): XorgClient {
            val s = Socket(); s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            return XorgClient(s.getInputStream(), s.getOutputStream(), s, cookie).apply { setup() }
        }
        /** Over any connected stream pair, e.g. an Android LocalSocket on X.Org's Unix socket. */
        fun connect(inp: InputStream, out: OutputStream, closer: Closeable, cookie: ByteArray? = null): XorgClient =
            XorgClient(inp, out, closer, cookie).apply { setup() }

        /** An Xauthority file with one wildcard entry (any host, any display) for [cookie]: for Xvfb -auth and XAUTHORITY. */
        fun xauthority(cookie: ByteArray): ByteArray {
            val o = java.io.ByteArrayOutputStream()
            fun field(b: ByteArray) { o.write(b.size shr 8); o.write(b.size and 0xFF); o.write(b) }
            o.write(0xFF); o.write(0xFF)   // FamilyWild
            field(ByteArray(0)); field(ByteArray(0)); field("MIT-MAGIC-COOKIE-1".toByteArray()); field(cookie)
            return o.toByteArray()
        }

        private const val KEY_PRESS = 2; private const val KEY_RELEASE = 3
        private const val BUTTON_PRESS = 4; private const val BUTTON_RELEASE = 5; private const val MOTION = 6
        private const val SELECTION_CLEAR = 29; private const val SELECTION_REQUEST = 30; private const val SELECTION_NOTIFY = 31
        const val XK_SHIFT_L = 0xffe1
        private const val MAX_CLIP = 4 * 1024 * 1024
    }

    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
    private fun pad(n: Int) = (4 - n % 4) % 4
    private fun readLe(n: Int): ByteBuffer { val b = ByteArray(n); inp.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN) }

    /** Sends one request; returns its sequence number. Callers hold the lock. */
    private fun send(b: ByteBuffer): Int { out.write(b.array(), 0, b.position()); seq = (seq + 1) and 0xFFFF; return seq }
    private fun request(b: ByteBuffer, onReply: (ByteBuffer?) -> Unit) = synchronized(this) { pending[send(b).let { it }] = onReply; out.flush() }
    fun flush() = synchronized(this) { out.flush() }

    // ---------------------------------------------------------------- setup (synchronous)

    private fun setup() {
        val name = if (cookie != null) "MIT-MAGIC-COOKIE-1".toByteArray() else ByteArray(0)
        val data = cookie ?: ByteArray(0)
        val b = le(12 + name.size + pad(name.size) + data.size + pad(data.size))
        b.put('l'.code.toByte()); b.put(0); b.putShort(11); b.putShort(0); b.putShort(name.size.toShort()); b.putShort(data.size.toShort()); b.putShort(0)
        b.put(name); b.position(b.position() + pad(name.size)); b.put(data)
        out.write(b.array()); out.flush()
        val head = readLe(8)
        val status = head.get(0).toInt()
        val extra = (head.getShort(6).toInt() and 0xFFFF) * 4
        val body = readLe(extra)
        if (status != 1) error("X server refused the connection: " + String(body.array(), Charsets.ISO_8859_1).trim())
        idBase = body.getInt(4)
        val vendorLen = body.getShort(16).toInt() and 0xFFFF
        val formats = body.get(21).toInt() and 0xFF
        minKeycode = body.get(26).toInt() and 0xFF; maxKeycode = body.get(27).toInt() and 0xFF
        val screenOff = 32 + vendorLen + pad(vendorLen) + formats * 8
        root = body.getInt(screenOff)
        width = body.getShort(screenOff + 20).toInt() and 0xFFFF
        height = body.getShort(screenOff + 22).toInt() and 0xFFFF
        xtest = queryExtension("XTEST").first
        if (xtest == 0) error("The X server has no XTEST extension")
        queryExtension("XFIXES").let { (major, firstEvent) -> xfixes = major; xfixesEvent = firstEvent }
        loadKeymap()
        for (a in listOf("CLIPBOARD", "UTF8_STRING", "TARGETS", "TEXT", "STRING", "X11_ANDROID_CLIP")) atoms[a] = internAtom(a)
        // A hidden window to own the clipboard and receive pasted data.
        window = idBase or 1
        val w = le(32); w.put(1); w.put(0); w.putShort(8); w.putInt(window); w.putInt(root); w.putShort(0); w.putShort(0); w.putShort(1); w.putShort(1)
        w.putShort(0); w.putShort(2); w.putInt(0); w.putInt(0)
        send(w)
        if (xfixes != 0) {
            // QueryVersion is required before other XFIXES requests.
            val v = le(12); v.put(xfixes.toByte()); v.put(0); v.putShort(3); v.putInt(5); v.putInt(0); send(v); out.flush(); reply()
            val s = le(16); s.put(xfixes.toByte()); s.put(2); s.putShort(4); s.putInt(window); s.putInt(atoms.getValue("CLIPBOARD")); s.putInt(1); send(s)
            val c = le(12); c.put(xfixes.toByte()); c.put(3); c.putShort(3); c.putInt(root); c.putInt(1); send(c)
        }
        queryExtension("DAMAGE").let { (major, ev) -> damageExt = major; damageEvent = ev }
        if (damageExt != 0) {
            val v = le(12); v.put(damageExt.toByte()); v.put(0); v.putShort(3); v.putInt(1); v.putInt(1); send(v); out.flush(); reply()
            damage = idBase or 2
            // BoundingBox level: one event per growth of the changed area; DamageSubtract resets it after each frame.
            val c = le(16); c.put(damageExt.toByte()); c.put(1); c.putShort(4); c.putInt(damage); c.putInt(root); c.put(2); c.put(0); c.putShort(0); send(c)
            markDirty(0, 0, width, height)
        }
        out.flush()
        Thread({ readLoop() }, "xorg-client").apply { isDaemon = true }.start()
        if (xfixes != 0) requestCursor()
    }

    /** Waits for the reply to the last request (setup phase only), skipping events. */
    private fun reply(): ByteBuffer {
        while (true) {
            val h = readLe(32)
            when (h.get(0).toInt()) {
                1 -> { val more = h.getInt(4) * 4; val r = le(32 + more); r.put(h.array()); if (more > 0) r.put(readLe(more).array()); r.flip(); return r }
                0 -> error("X error ${h.get(1)} during setup")
                else -> {}
            }
        }
    }

    private fun queryExtension(name: String): Pair<Int, Int> {
        val n = name.length
        val b = le(8 + n + pad(n)); b.put(98); b.put(0); b.putShort(((8 + n + pad(n)) / 4).toShort()); b.putShort(n.toShort()); b.putShort(0)
        b.put(name.toByteArray(Charsets.ISO_8859_1)); b.position(8 + n + pad(n))
        send(b); out.flush()
        val r = reply()
        return if (r.get(8).toInt() != 0) (r.get(9).toInt() and 0xFF) to (r.get(10).toInt() and 0xFF) else 0 to 0
    }

    private fun internAtom(name: String): Int {
        val n = name.length
        val b = le(8 + n + pad(n)); b.put(16); b.put(0); b.putShort(((8 + n + pad(n)) / 4).toShort()); b.putShort(n.toShort()); b.putShort(0)
        b.put(name.toByteArray(Charsets.ISO_8859_1)); b.position(8 + n + pad(n))
        send(b); out.flush()
        return reply().getInt(8)
    }

    private fun loadKeymap() {
        val count = maxKeycode - minKeycode + 1
        val b = le(8); b.put(101); b.put(0); b.putShort(2); b.put(minKeycode.toByte()); b.put(count.toByte()); b.putShort(0)
        send(b); out.flush()
        val r = reply()
        val per = r.get(1).toInt() and 0xFF
        keymap.clear()
        for (k in 0 until count) {
            val code = minKeycode + k
            val syms = (0 until per).map { r.getInt(32 + (k * per + it) * 4) }
            if (syms.all { it == 0 } && code > minKeycode + 8) spare = code
            syms.getOrNull(0)?.takeIf { it != 0 }?.let { keymap.putIfAbsent(it, code to false) }
            syms.getOrNull(1)?.takeIf { it != 0 }?.let { keymap.putIfAbsent(it, code to true) }
        }
        shiftCode = keymap[XK_SHIFT_L]?.first ?: 50
    }

    // ---------------------------------------------------------------- event loop

    private fun readLoop() {
        try {
            while (!closed) {
                val h = readLe(32)
                val type = h.get(0).toInt() and 0x7F
                val s = h.getShort(2).toInt() and 0xFFFF
                when (type) {
                    1 -> {
                        val more = h.getInt(4) * 4
                        val r = le(32 + more); r.put(h.array()); if (more > 0) r.put(readLe(more).array()); r.flip()
                        pending.remove(s)?.invoke(r)
                    }
                    0 -> pending.remove(s)?.invoke(null)
                    SELECTION_REQUEST -> answerSelection(h)
                    SELECTION_NOTIFY -> {}
                    SELECTION_CLEAR -> ownText = null
                    else -> if (damage != 0 && type == damageEvent) {
                        markDirty(h.getShort(16).toInt(), h.getShort(18).toInt(),
                            h.getShort(16) + (h.getShort(20).toInt() and 0xFFFF), h.getShort(18) + (h.getShort(22).toInt() and 0xFFFF))
                    } else if (xfixes != 0 && type == xfixesEvent) {
                        // XFixesSelectionNotify: someone took the clipboard. Ask for its text unless it's us.
                        val owner = h.getInt(8)
                        if (owner != window && owner != 0) fetchClipboard()
                    } else if (xfixes != 0 && type == xfixesEvent + 1) requestCursor()
                }
            }
        } catch (_: Exception) { }
    }

    // ---------------------------------------------------------------- damage

    private fun markDirty(x0: Int, y0: Int, x1: Int, y1: Int) = synchronized(dirty) {
        if (dirty[0] >= dirty[2]) { dirty[0] = x0; dirty[1] = y0; dirty[2] = x1; dirty[3] = y1 }
        else { dirty[0] = minOf(dirty[0], x0); dirty[1] = minOf(dirty[1], y0); dirty[2] = maxOf(dirty[2], x1); dirty[3] = maxOf(dirty[3], y1) }
    }

    /**
     * Returns the changed rectangle (x, y, w, h) clipped to the screen and clears it, or null if nothing changed.
     * Also tells the server to start a new damage region (DamageSubtract).
     */
    fun takeDirty(): IntArray? {
        val r = synchronized(dirty) {
            if (dirty[0] >= dirty[2] || dirty[1] >= dirty[3]) return null
            val x0 = dirty[0].coerceIn(0, width); val y0 = dirty[1].coerceIn(0, height)
            val x1 = dirty[2].coerceIn(0, width); val y1 = dirty[3].coerceIn(0, height)
            dirty[0] = 0; dirty[1] = 0; dirty[2] = 0; dirty[3] = 0
            if (x0 >= x1 || y0 >= y1) return null
            intArrayOf(x0, y0, x1 - x0, y1 - y0)
        }
        synchronized(this) {
            val b = le(16); b.put(damageExt.toByte()); b.put(3); b.putShort(4); b.putInt(damage); b.putInt(0); b.putInt(0); send(b); out.flush()
        }
        return r
    }

    // ---------------------------------------------------------------- clipboard

    /** Linux copied something: convert CLIPBOARD to UTF8_STRING onto our window, then read it. */
    private fun fetchClipboard() = synchronized(this) {
        val prop = atoms.getValue("X11_ANDROID_CLIP")
        val b = le(24); b.put(24); b.put(0); b.putShort(6); b.putInt(window); b.putInt(atoms.getValue("CLIPBOARD")); b.putInt(atoms.getValue("UTF8_STRING"))
        b.putInt(prop); b.putInt(0)
        send(b)
        // GetProperty right after: X processes requests in order, but the owner answers asynchronously, so retry a few times.
        out.flush()
        Thread { repeat(10) { i -> Thread.sleep(40L + i * 40); if (readProperty(prop)) return@Thread } }.start()
    }

    private fun readProperty(prop: Int): Boolean {
        var done = false
        val latch = java.util.concurrent.CountDownLatch(1)
        val b = le(24); b.put(20); b.put(1); b.putShort(6); b.putInt(window); b.putInt(prop); b.putInt(0); b.putInt(0); b.putInt(MAX_CLIP / 4)
        request(b) { r ->
            if (r != null && r.getInt(8) != 0) {
                val format = r.get(1).toInt() and 0xFF
                val n = r.getInt(16) * (format / 8)
                if (format == 8 && n > 0) {
                    val bytes = ByteArray(n); r.position(32); r.get(bytes)
                    onClipboard?.invoke(String(bytes, Charsets.UTF_8)); done = true
                }
            }
            latch.countDown()
        }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return done
    }

    /** Android has new text: own CLIPBOARD (and PRIMARY isn't touched) so Linux apps paste it. */
    fun setClipboard(text: String) = synchronized(this) {
        ownText = text
        val b = le(16); b.put(22); b.put(0); b.putShort(4); b.putInt(window); b.putInt(atoms.getValue("CLIPBOARD")); b.putInt(0)
        send(b); out.flush()
    }

    private fun answerSelection(e: ByteBuffer) = synchronized(this) {
        val time = e.getInt(4); val requestor = e.getInt(12); val selection = e.getInt(16); val target = e.getInt(20)
        var property = e.getInt(24).let { if (it == 0) target else it }
        val text = ownText
        val utf8 = atoms.getValue("UTF8_STRING"); val targets = atoms.getValue("TARGETS")
        when {
            text == null -> property = 0
            target == targets -> changeProperty(requestor, property, 4 /* ATOM */, 32,
                le(12).apply { putInt(targets); putInt(utf8); putInt(31 /* STRING */) }.array())
            target == utf8 || target == atoms["TEXT"] -> changeProperty(requestor, property, utf8, 8, text.toByteArray(Charsets.UTF_8))
            target == 31 -> changeProperty(requestor, property, 31, 8, text.toByteArray(Charsets.ISO_8859_1))
            else -> property = 0
        }
        // SendEvent(SelectionNotify) to the requestor.
        val b = le(44); b.put(25); b.put(0); b.putShort(11); b.putInt(requestor); b.putInt(0)
        b.put(SELECTION_NOTIFY.toByte()); b.put(0); b.putShort(0); b.putInt(time); b.putInt(requestor); b.putInt(selection); b.putInt(target); b.putInt(property)
        b.position(44)
        send(b); out.flush()
    }

    private fun changeProperty(win: Int, prop: Int, type: Int, format: Int, data: ByteArray) {
        val n = data.size
        val b = le(24 + n + pad(n)); b.put(18); b.put(0); b.putShort(((24 + n + pad(n)) / 4).toShort()); b.putInt(win); b.putInt(prop); b.putInt(type)
        b.put(format.toByte()); b.put(0); b.putShort(0); b.putInt(n / (format / 8)); b.put(data); b.position(24 + n + pad(n))
        send(b)
    }

    // ---------------------------------------------------------------- cursor

    fun requestCursor() {
        val b = le(4); b.put(xfixes.toByte()); b.put(4); b.putShort(1)
        request(b) { r ->
            if (r == null) return@request
            val w = r.getShort(12).toInt() and 0xFFFF; val h = r.getShort(14).toInt() and 0xFFFF
            val hx = r.getShort(16).toInt() and 0xFFFF; val hy = r.getShort(18).toInt() and 0xFFFF
            if (w == 0 || h == 0 || w > 256 || h > 256) return@request
            val px = IntArray(w * h); r.position(32); for (i in px.indices) px[i] = r.getInt()
            onCursor?.invoke(px, w, h, hx, hy)
        }
    }

    // ---------------------------------------------------------------- input

    private fun fake(type: Int, detail: Int, x: Int = 0, y: Int = 0) = synchronized(this) {
        val b = le(36)
        b.put(xtest.toByte()); b.put(2); b.putShort(9)
        b.put(type.toByte()); b.put(detail.toByte()); b.putShort(0); b.putInt(0); b.putInt(if (type == MOTION) root else 0)
        b.putInt(0); b.putInt(0); b.putShort(x.toShort()); b.putShort(y.toShort()); b.putInt(0); b.putShort(0); b.put(0); b.put(0)
        send(b)
    }

    fun motion(x: Int, y: Int) { fake(MOTION, 0, x, y); flush() }
    fun button(button: Int, down: Boolean) { fake(if (down) BUTTON_PRESS else BUTTON_RELEASE, button); flush() }
    fun click(button: Int) { button(button, true); button(button, false) }
    fun scroll(up: Boolean, steps: Int = 1) = repeat(steps) { click(if (up) 4 else 5) }
    /** Horizontal wheel: buttons 6 (left) and 7 (right). */
    fun scrollSideways(left: Boolean) = click(if (left) 6 else 7)
    fun key(keycode: Int, down: Boolean) { fake(if (down) KEY_PRESS else KEY_RELEASE, keycode); flush() }

    /** Presses or releases the key for [keysym], adding Shift when the symbol is on the shifted level. */
    fun keysym(keysym: Int, down: Boolean): Boolean {
        val (code, shift) = keymap[keysym] ?: return false
        if (shift && down) fake(KEY_PRESS, shiftCode)
        fake(if (down) KEY_PRESS else KEY_RELEASE, code)
        if (shift && !down) fake(KEY_RELEASE, shiftCode)
        flush(); return true
    }

    /** Types one character; symbols without a key are mapped onto a spare keycode for the moment. */
    fun type(ch: Int) {
        val sym = when {
            ch == '\n'.code -> 0xff0d; ch == '\t'.code -> 0xff09; ch == 8 -> 0xff08
            ch in 0x20..0x7e || ch in 0xa0..0xff -> ch
            else -> 0x01000000 or ch
        }
        if (keymap.containsKey(sym)) { keysym(sym, true); keysym(sym, false); return }
        if (spare < 0) return
        synchronized(this) {
            val b = le(12); b.put(100); b.put(1); b.putShort(3); b.put(spare.toByte()); b.put(1); b.putShort(0); b.putInt(sym)
            send(b); out.flush()
        }
        // Give clients a moment to process the MappingNotify before the key arrives (xdotool does the same).
        Thread.sleep(30)
        synchronized(this) { fake(KEY_PRESS, spare); fake(KEY_RELEASE, spare) }
        flush()
        Thread.sleep(10)
    }

    fun close() { closed = true; runCatching { closer.close() } }
}
