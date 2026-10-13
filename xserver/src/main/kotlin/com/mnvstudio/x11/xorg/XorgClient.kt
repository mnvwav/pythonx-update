package com.mnvstudio.x11.xorg

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A minimal X11 client for driving a real X.Org server (Xvfb) from the app: it injects pointer and keyboard input
 * through the XTEST extension and types any character by temporarily mapping a spare keycode, the way XSDL and
 * xdotool do. Replies are only awaited during setup; afterwards everything is fire-and-forget.
 */
class XorgClient private constructor(private val socket: Socket) {
    private val out: OutputStream = socket.getOutputStream().buffered()
    private val inp = DataInputStream(socket.getInputStream().buffered())
    var root = 0; private set
    var width = 0; private set
    var height = 0; private set
    private var minKeycode = 8
    private var maxKeycode = 255
    private var xtest = 0
    /** keysym → (keycode, needs shift). */
    private val keymap = HashMap<Int, Pair<Int, Boolean>>()
    private var spare = -1
    private var shiftCode = 0
    private var seq = 0

    companion object {
        fun connect(host: String, port: Int, timeoutMs: Int = 3000): XorgClient {
            val s = Socket(); s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            return XorgClient(s).apply { setup() }
        }
        private const val KEY_PRESS = 2; private const val KEY_RELEASE = 3
        private const val BUTTON_PRESS = 4; private const val BUTTON_RELEASE = 5; private const val MOTION = 6
        const val XK_SHIFT_L = 0xffe1
    }

    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
    private fun pad(n: Int) = (4 - n % 4) % 4

    private fun readLe(n: Int): ByteBuffer { val b = ByteArray(n); inp.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN) }

    private fun send(b: ByteBuffer) { out.write(b.array(), 0, b.position()); seq++ }
    fun flush() = synchronized(this) { out.flush() }

    private fun setup() {
        val b = le(12); b.put('l'.code.toByte()); b.put(0); b.putShort(11); b.putShort(0); b.putShort(0); b.putShort(0); b.putShort(0)
        out.write(b.array()); out.flush()
        val head = readLe(8)
        val status = head.get(0).toInt()
        val extra = (head.getShort(6).toInt() and 0xFFFF) * 4
        val body = readLe(extra)
        if (status != 1) error("X server refused the connection: " + String(body.array(), Charsets.ISO_8859_1).trim())
        val vendorLen = body.getShort(16).toInt() and 0xFFFF
        val formats = body.get(21).toInt() and 0xFF
        minKeycode = body.get(26).toInt() and 0xFF; maxKeycode = body.get(27).toInt() and 0xFF
        val screenOff = 32 + vendorLen + pad(vendorLen) + formats * 8
        root = body.getInt(screenOff)
        width = body.getShort(screenOff + 20).toInt() and 0xFFFF
        height = body.getShort(screenOff + 22).toInt() and 0xFFFF
        xtest = queryExtension("XTEST")
        if (xtest == 0) error("The X server has no XTEST extension")
        loadKeymap()
        // Drain events and errors in the background so the server never blocks on us.
        Thread({ runCatching { val buf = ByteArray(4096); val i = socket.getInputStream(); while (i.read(buf) >= 0) {} } }, "xorg-client-drain")
            .apply { isDaemon = true }.start()
    }

    /** Waits for the reply to the last request (setup phase only), skipping events. */
    private fun reply(): ByteBuffer {
        while (true) {
            val h = readLe(32)
            when (h.get(0).toInt()) {
                1 -> { val more = h.getInt(4) * 4; val r = le(32 + more); r.put(h.array()); if (more > 0) r.put(readLe(more).array()); r.flip(); return r }
                0 -> error("X error ${h.get(1)} on request")
                else -> {}  // event
            }
        }
    }

    private fun queryExtension(name: String): Int {
        val n = name.length
        val b = le(8 + n + pad(n)); b.put(98); b.put(0); b.putShort(((8 + n + pad(n)) / 4).toShort()); b.putShort(n.toShort()); b.putShort(0)
        b.put(name.toByteArray(Charsets.ISO_8859_1)); b.position(8 + n + pad(n))
        send(b); out.flush()
        val r = reply()
        return if (r.get(8).toInt() != 0) r.get(9).toInt() and 0xFF else 0
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
            if (syms.all { it == 0 } && code > minKeycode + 8) spare = code   // the highest unused keycode
            syms.getOrNull(0)?.takeIf { it != 0 }?.let { keymap.putIfAbsent(it, code to false) }
            syms.getOrNull(1)?.takeIf { it != 0 }?.let { keymap.putIfAbsent(it, code to true) }
        }
        shiftCode = keymap[XK_SHIFT_L]?.first ?: 50
    }

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
            // ChangeKeyboardMapping: one keycode, one keysym per keycode.
            val b = le(12); b.put(100); b.put(1); b.putShort(3); b.put(spare.toByte()); b.put(1); b.putShort(0); b.putInt(sym)
            send(b); out.flush()
        }
        // Give clients a moment to process the MappingNotify before the key arrives (xdotool does the same).
        Thread.sleep(30)
        synchronized(this) { fake(KEY_PRESS, spare); fake(KEY_RELEASE, spare) }
        flush()
        Thread.sleep(10)
    }

    fun close() = runCatching { socket.close() }
}
