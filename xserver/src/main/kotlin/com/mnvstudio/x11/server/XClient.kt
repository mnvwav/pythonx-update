package com.mnvstudio.x11.server

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

/**
 * One client connection. A reader thread parses requests and runs them under the server lock;
 * a writer thread drains the output queue, so a slow client can never block the server.
 */
class XClient(
    val server: XServer, val index: Int,
    private val input: InputStream, private val output: OutputStream, private val closer: () -> Unit,
) {
    var order: ByteOrder = ByteOrder.LITTLE_ENDIAN; private set
    /** Sequence number of the last request processed (16 bits on the wire). */
    var seq = 0; private set
    val idBase = index shl 21
    /** Where the client connected from: "local" or a network address. */
    var peer = "local"
    @Volatile var requestCount = 0L; private set
    @Volatile var errorCount = 0L; private set
    val idMask = 0x1FFFFF
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false

    fun start() {
        Thread(::readLoop, "x11-client-$index").apply { isDaemon = true }.start()
        Thread(::writeLoop, "x11-client-$index-out").apply { isDaemon = true }.start()
    }

    fun write(b: ByteArray) { if (!closed) queue.put(b) }

    fun close() {
        if (closed) return
        closed = true
        queue.put(ByteArray(0))
        runCatching { closer() }
        server.removeClient(this)
    }

    private fun writeLoop() {
        try {
            while (true) {
                val b = queue.take()
                if (b.isEmpty() && closed) break
                output.write(b)
                if (queue.isEmpty()) output.flush()
            }
        } catch (_: Exception) {
        } finally { close() }
    }

    private fun readLoop() {
        val din = DataInputStream(input)
        try {
            setup(din)
            val head = ByteArray(4)
            while (!closed) {
                din.readFully(head)
                val len = ByteBuffer.wrap(head).order(order).getShort(2).toInt() and 0xFFFF
                if (len == 0) {
                    // BIG-REQUESTS isn't offered, so a zero length is malformed.
                    synchronized(server) { seq++; error(Err.LENGTH, 0, head[0].toInt() and 0xFF) }
                    continue
                }
                val body = ByteArray(len * 4)
                System.arraycopy(head, 0, body, 0, 4)
                din.readFully(body, 4, len * 4 - 4)
                val req = Req(ByteBuffer.wrap(body).order(order))
                synchronized(server) {
                    seq++
                    requestCount++
                    var err = 0
                    try { handle(req) }
                    catch (e: XError) { err = e.code; error(e.code, e.value, req.opcode) }
                    catch (e: IndexOutOfBoundsException) { err = Err.LENGTH; error(Err.LENGTH, 0, req.opcode) }
                    if (err != 0) errorCount++
                    Inspector.record(index, seq, req.opcode, req.size, err)
                }
            }
        } catch (_: Exception) {
        } finally { close() }
    }

    private fun error(code: Int, value: Int, major: Int) {
        write(Msg(32, order).b(0, 0).b(1, code).s(2, seq).i(4, value).s(8, 0).b(10, major).array())
    }

    // ------------------------------------------------------------ setup

    private fun setup(din: DataInputStream) {
        val first = din.readUnsignedByte()
        order = if (first == 'B'.code) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val rest = ByteArray(11).also { din.readFully(it) }
        val bb = ByteBuffer.wrap(byteArrayOf(first.toByte()) + rest).order(order)
        val nameLen = bb.getShort(6).toInt() and 0xFFFF
        val dataLen = bb.getShort(8).toInt() and 0xFFFF
        din.readFully(ByteArray(pad4(nameLen) + pad4(dataLen)))
        write(synchronized(server) { setupReply() })
    }

    private fun setupReply(): ByteArray {
        val s = server
        val vendor = XServer.VENDOR.toByteArray()
        val formats = listOf(intArrayOf(1, 1, 32), intArrayOf(24, 32, 32))
        // Screen: 40 bytes + depth 24 (8 + one 24-byte visual) + depth 1 (8, no visuals).
        val screenLen = 40 + 8 + 24 + 8
        val total = 40 + pad4(vendor.size) + 8 * formats.size + screenLen
        val m = Msg(total, order)
        m.b(0, 1).s(2, 11).s(4, 0).s(6, (total - 8) / 4)
        m.i(8, 11_000_000).i(12, idBase).i(16, idMask).i(20, 0).s(24, vendor.size).s(26, 0xFFFF)
        m.b(28, 1).b(29, formats.size).b(30, 0).b(31, 0).b(32, 32).b(33, 32).b(34, s.keyboard.minKeycode).b(35, s.keyboard.maxKeycode)
        m.bytes(40, vendor)
        var p = 40 + pad4(vendor.size)
        for (f in formats) { m.b(p, f[0]).b(p + 1, f[1]).b(p + 2, f[2]); p += 8 }
        val mm = { px: Int -> px * 254 / (s.dpi * 10) }
        m.i(p, XServer.ROOT).i(p + 4, XServer.COLORMAP).i(p + 8, 0xFFFFFF).i(p + 12, 0).i(p + 16, s.root.allMasks())
            .s(p + 20, s.width).s(p + 22, s.height).s(p + 24, mm(s.width)).s(p + 26, mm(s.height))
            .s(p + 28, 1).s(p + 30, 1).i(p + 32, XServer.VISUAL).b(p + 36, 0).b(p + 37, 0).b(p + 38, 24).b(p + 39, 2)
        p += 40
        m.b(p, 24).s(p + 2, 1); p += 8
        m.i(p, XServer.VISUAL).b(p + 4, 4).b(p + 5, 8).s(p + 6, 256).i(p + 8, 0xFF0000).i(p + 12, 0xFF00).i(p + 16, 0xFF); p += 24
        m.b(p, 1).s(p + 2, 0)
        return m.array()
    }

    // ------------------------------------------------------------ helpers

    /** Sends a reply: [extra] bytes after the 32-byte header, filled by [fill] at absolute offsets. */
    private fun reply(extra: Int = 0, fill: (Msg) -> Unit = {}) {
        val m = Msg(32 + pad4(extra), order)
        m.b(0, 1).s(2, seq).i(4, pad4(extra) / 4)
        fill(m)
        write(m.array())
    }

    private fun newId(id: Int) {
        if (id and idMask.inv() != idBase || server.resources.containsKey(id)) throw XError(Err.IDCHOICE, id)
    }

    private fun points(r: Req, from: Int, relative: Boolean): Pair<IntArray, IntArray> {
        val n = (r.size - from) / 4
        val xs = IntArray(n); val ys = IntArray(n)
        for (i in 0 until n) {
            xs[i] = r.i16(from + i * 4); ys[i] = r.i16(from + i * 4 + 2)
            if (relative && i > 0) { xs[i] += xs[i - 1]; ys[i] += ys[i - 1] }
        }
        return xs to ys
    }

    private fun applyGc(gc: GContext, values: List<Pair<Int, Int>>) {
        val s = server
        for ((bit, v) in values) when (bit) {
            0x1 -> gc.function = v and 15
            0x2 -> gc.planeMask = v
            0x4 -> gc.fg = v
            0x8 -> gc.bg = v
            0x10 -> gc.lineWidth = v and 0xFFFF
            0x20 -> gc.lineStyle = v
            0x40 -> gc.capStyle = v
            0x80 -> gc.joinStyle = v
            0x100 -> gc.fillStyle = v
            0x200 -> gc.fillRule = v
            0x400 -> gc.tile = s.pixmap(v)
            0x800 -> gc.stipple = s.pixmap(v).also { if (it.depth != 1) throw XError(Err.MATCH) }
            0x1000 -> gc.tsX = v.toShort().toInt()
            0x2000 -> gc.tsY = v.toShort().toInt()
            0x4000 -> gc.font = s.font(v)
            0x8000 -> gc.subwindowMode = v
            0x10000 -> gc.exposures = v != 0
            0x20000 -> gc.clipX = v.toShort().toInt()
            0x40000 -> gc.clipY = v.toShort().toInt()
            0x80000 -> { gc.clipRects = null; gc.clipMask = if (v == 0) null else s.pixmap(v) }
            0x400000 -> gc.arcMode = v
        }
    }

    private inline fun draw(r: Req, block: (Paint, Drawable, GContext) -> Unit) {
        val d = server.drawable(r.u32(4)); val gc = server.gc(r.u32(8))
        if (gc.depth != d.depth) throw XError(Err.MATCH)
        val p = Paint(d, gc)
        block(p, d, gc)
        server.drew(d, p)
    }

    private fun charInfo(m: Msg, o: Int, f: XFont) {
        m.s(o, 0).s(o + 2, f.width).s(o + 4, f.width).s(o + 6, f.ascent).s(o + 8, f.descent).s(o + 10, 0)
    }

    /** Fills the QueryFont / ListFontsWithInfo body shared by both replies; returns the offset after the properties. */
    private fun fontInfo(m: Msg, f: XFont, nameAtom: Int): Int {
        charInfo(m, 8, f); charInfo(m, 24, f)
        m.s(40, 0).s(42, 255).s(44, 32).s(46, 1).b(48, 0).b(49, 0).b(50, 0).b(51, 1).s(52, f.ascent).s(54, f.descent)
        m.i(60, Atoms.FONT).i(64, nameAtom)
        return 68
    }

    private fun chars16(r: Req, from: Int, n: Int) = IntArray(n) { r.u8(from + it * 2) shl 8 or r.u8(from + it * 2 + 1) }

    /** Draws PolyText items: each is a run of characters with an x delta, or a font switch. */
    private fun polyText(r: Req, wide: Boolean) = draw(r) { p, _, gc ->
        var x = r.i16(12); val y = r.i16(14)
        var o = 16
        while (o < r.size) {
            val len = r.u8(o)
            if (len == 0) break
            if (len == 255) {
                if (o + 5 > r.size) break
                val fid = (r.u8(o + 1) shl 24) or (r.u8(o + 2) shl 16) or (r.u8(o + 3) shl 8) or r.u8(o + 4)
                gc.font = server.font(fid); o += 5; continue
            }
            x += r.i8(o + 1)
            val chars = if (wide) chars16(r, o + 2, len) else IntArray(len) { r.u8(o + 2 + it) }
            x += p.text(x, y, chars, false)
            o += 2 + len * (if (wide) 2 else 1)
        }
    }

    // ------------------------------------------------------------ requests

    private fun handle(r: Req) {
        val s = server
        when (r.opcode) {
            1 -> { // CreateWindow
                r.need(32)
                val id = r.u32(4); newId(id)
                val parent = s.window(r.u32(8))
                val cls = r.u16(22).let { if (it == 0) parent.cls else it }
                s.createWindow(this, id, parent, r.i16(12), r.i16(14), r.u16(16), r.u16(18), r.u16(20), cls, r.data)
                s.setAttributes(this, s.window(id), r.values(32, r.u32(28)))
            }
            2 -> { val w = s.window(r.u32(4)); s.setAttributes(this, w, r.values(12, r.u32(8))) }
            3 -> { // GetWindowAttributes
                val w = s.window(r.u32(4))
                val state = when { !w.mapped -> 0; w.viewable -> 2; else -> 1 }
                reply(12) {
                    it.b(1, 0).i(8, XServer.VISUAL).s(12, w.cls).b(14, w.bitGravity).b(15, w.winGravity).i(16, -1).i(20, 0)
                        .b(24, 0).b(25, 1).b(26, state).b(27, if (w.overrideRedirect) 1 else 0).i(28, w.colormap)
                        .i(32, w.allMasks()).i(36, w.masks[this] ?: 0).s(40, w.doNotPropagate)
                }
            }
            4 -> s.destroy(s.window(r.u32(4)))
            5 -> s.destroySubwindows(s.window(r.u32(4)))
            6 -> s.window(r.u32(8))
            7 -> s.reparent(this, s.window(r.u32(4)), s.window(r.u32(8)), r.i16(12), r.i16(14))
            8 -> s.map(this, s.window(r.u32(4)))
            9 -> s.mapSubwindows(this, s.window(r.u32(4)))
            10 -> s.unmap(s.window(r.u32(4)))
            11 -> s.unmapSubwindows(s.window(r.u32(4)))
            12 -> { val mask = r.u16(8); s.configure(this, s.window(r.u32(4)), mask, r.values(12, mask)) }
            13 -> s.circulate(s.window(r.u32(4)), r.data == 1)
            14 -> { // GetGeometry
                val d = s.drawable(r.u32(4))
                val w = d as? Window
                reply { it.b(1, d.depth).i(8, XServer.ROOT).s(12, w?.x ?: 0).s(14, w?.y ?: 0).s(16, d.width).s(18, d.height).s(20, w?.border ?: 0) }
            }
            15 -> { // QueryTree
                val w = s.window(r.u32(4))
                reply(4 * w.children.size) { m ->
                    m.i(8, XServer.ROOT).i(12, w.parent?.id ?: 0).s(16, w.children.size)
                    w.children.forEachIndexed { i, c -> m.i(32 + 4 * i, c.id) }
                }
            }
            16 -> { // InternAtom
                val name = r.str(8, r.u16(4))
                val atom = if (r.data != 0) s.atoms.find(name) else s.atoms.intern(name)
                reply { it.i(8, atom) }
            }
            17 -> {
                val name = s.atoms.name(r.u32(4)) ?: throw XError(Err.ATOM, r.u32(4))
                val b = name.toByteArray(Charsets.ISO_8859_1)
                reply(b.size) { it.s(8, b.size).bytes(32, b) }
            }
            18 -> { // ChangeProperty
                val w = s.window(r.u32(4)); val prop = r.u32(8); val type = r.u32(12); val fmt = r.u8(16)
                if (!s.atoms.valid(prop)) throw XError(Err.ATOM, prop)
                if (fmt != 8 && fmt != 16 && fmt != 32) throw XError(Err.VALUE, fmt)
                // Values are stored in the client's byte order; clients on one device share it.
                val data = r.bytes(24, r.u32(20) * (fmt / 8))
                val old = w.props[prop]
                if (r.data == 0 || old == null) w.props[prop] = Property(type, fmt, data)
                else {
                    if (old.type != type || old.format != fmt) throw XError(Err.MATCH)
                    old.data = if (r.data == 1) data + old.data else old.data + data
                }
                s.propertyNotify(w, prop, false)
            }
            19 -> { val w = s.window(r.u32(4)); if (w.props.remove(r.u32(8)) != null) s.propertyNotify(w, r.u32(8), true) }
            20 -> { // GetProperty
                val w = s.window(r.u32(4)); val prop = r.u32(8); val type = r.u32(12)
                val offset = r.u32(16).toLong() and 0xFFFFFFFFL; val length = r.u32(20).toLong() and 0xFFFFFFFFL
                val p = w.props[prop]
                when {
                    p == null -> reply { it.b(1, 0).i(8, 0).i(12, 0).i(16, 0) }
                    type != 0 && type != p.type -> reply { it.b(1, p.format).i(8, p.type).i(12, p.data.size).i(16, 0) }
                    else -> {
                        val n = p.data.size
                        val i = 4L * offset
                        if (i > n) throw XError(Err.VALUE, offset.toInt())
                        val l = minOf(n - i, 4L * length).toInt()
                        val after = n - (i.toInt() + l)
                        val value = p.data.copyOfRange(i.toInt(), i.toInt() + l)
                        reply(l) { it.b(1, p.format).i(8, p.type).i(12, after).i(16, l / (p.format / 8)).bytes(32, value) }
                        if (r.data != 0 && after == 0) { w.props.remove(prop); s.propertyNotify(w, prop, true) }
                    }
                }
            }
            21 -> {
                val keys = s.window(r.u32(4)).props.keys.toList()
                reply(4 * keys.size) { m -> m.s(8, keys.size); keys.forEachIndexed { i, a -> m.i(32 + 4 * i, a) } }
            }
            22 -> {
                val owner = r.u32(4); if (owner != 0) s.window(owner)
                s.setSelectionOwner(this, owner, r.u32(8), r.u32(12))
            }
            23 -> reply { it.i(8, s.selections[r.u32(4)]?.window ?: 0) }
            24 -> { s.window(r.u32(4)); s.convertSelection(this, r.u32(4), r.u32(8), r.u32(12), r.u32(16), r.u32(20)) }
            25 -> sendEvent(r)
            26 -> { val status = s.grabPointer(this, s.window(r.u32(4)), r.data != 0, r.u16(8)); reply { it.b(1, status) } }
            27 -> s.ungrabPointer(this)
            28, 29, 30, 33, 34, 35, 36, 37 -> Unit // passive grabs, AllowEvents, server grabs: accepted, not enforced
            31 -> { val status = s.grabKeyboard(this, s.window(r.u32(4)), r.data != 0); reply { it.b(1, status) } }
            32 -> s.ungrabKeyboard(this)
            38 -> { // QueryPointer
                val w = s.window(r.u32(4))
                var child: Window? = s.windowAt(s.pointerX, s.pointerY)
                while (child != null && child.parent !== w) child = child.parent
                reply {
                    it.b(1, 1).i(8, XServer.ROOT).i(12, child?.id ?: 0).s(16, s.pointerX).s(18, s.pointerY)
                        .s(20, s.pointerX - w.absX).s(22, s.pointerY - w.absY).s(24, s.modState)
                }
            }
            39 -> reply { it.i(8, 0) }
            40 -> { // TranslateCoordinates
                val src = s.window(r.u32(4)); val dst = s.window(r.u32(8))
                val ax = src.absX + r.i16(12); val ay = src.absY + r.i16(14)
                var child: Window? = s.windowAt(ax, ay)
                while (child != null && child.parent !== dst) child = child.parent
                reply { it.b(1, 1).i(8, child?.id ?: 0).s(12, ax - dst.absX).s(14, ay - dst.absY) }
            }
            41 -> { // WarpPointer
                val dstId = r.u32(8)
                if (dstId != 0) { val d = s.window(dstId); s.motion(d.absX + r.i16(20), d.absY + r.i16(22)) }
                else s.motion(s.pointerX + r.i16(20), s.pointerY + r.i16(22))
            }
            42 -> { val f = r.u32(4); if (f > 1) s.window(f); s.setFocus(f, r.data) }
            43 -> reply { it.b(1, 1).i(8, s.focus) }
            44 -> reply(8) { m -> for (k in 8..255) if (s.isKeyDown(k)) m.b(8 + k / 8, m.buf.get(8 + k / 8).toInt() or (1 shl (k % 8))) }
            45 -> { // OpenFont
                val id = r.u32(4); newId(id)
                val f = Fonts.open(r.str(12, r.u16(8))) ?: throw XError(Err.NAME)
                s.resources[id] = FontRef(id, this, f)
            }
            46 -> if (s.resources[r.u32(4)] is FontRef) s.resources.remove(r.u32(4)) else throw XError(Err.FONT, r.u32(4))
            47 -> { // QueryFont
                val f = s.font(r.u32(4))
                val atom = s.atoms.intern(f.name)
                reply(28 + 8 + 12 * 256) { m ->
                    var o = fontInfo(m, f, atom)
                    m.i(56, 256)
                    for (c in 0 until 256) { charInfo(m, o, f); o += 12 }
                }
            }
            48 -> { // QueryTextExtents
                val f = s.font(r.u32(4))
                val w = ((r.size - 8) / 2 - r.data) * f.width
                reply { it.b(1, 0).s(8, f.ascent).s(10, f.descent).s(12, f.ascent).s(14, f.descent).i(16, w).i(20, 0).i(24, w) }
            }
            49 -> { // ListFonts
                val names = Fonts.list(r.str(8, r.u16(6)), r.u16(4)).map { it.toByteArray(Charsets.ISO_8859_1) }
                reply(names.sumOf { it.size + 1 }) { m ->
                    m.s(8, names.size)
                    var o = 32
                    for (n in names) { m.b(o, n.size).bytes(o + 1, n); o += n.size + 1 }
                }
            }
            50 -> { // ListFontsWithInfo
                val names = Fonts.list(r.str(8, r.u16(6)), r.u16(4))
                names.forEachIndexed { i, name ->
                    val f = Fonts.open(name) ?: return@forEachIndexed
                    val b = name.toByteArray(Charsets.ISO_8859_1)
                    reply(28 + 8 + b.size) { m ->
                        val o = fontInfo(m, f, s.atoms.intern(f.name))
                        m.b(1, b.size).i(56, names.size - i - 1).bytes(o, b)
                    }
                }
                reply(28) { it.b(1, 0) }
            }
            51 -> Unit
            52 -> reply { it.s(8, 0) }
            53 -> { // CreatePixmap
                val id = r.u32(4); newId(id); s.drawable(r.u32(8))
                val depth = r.data
                if (depth != 1 && depth != 24 && depth != 32) throw XError(Err.VALUE, depth)
                val w = r.u16(12); val h = r.u16(14)
                if (w == 0 || h == 0) throw XError(Err.VALUE)
                s.resources[id] = Pixmap(id, this, w, h, if (depth == 32) 24 else depth)
            }
            54 -> { s.pixmap(r.u32(4)); s.resources.remove(r.u32(4)) }
            55 -> { // CreateGC
                val id = r.u32(4); newId(id)
                val gc = GContext(id, this, s.drawable(r.u32(8)).depth)
                applyGc(gc, r.values(16, r.u32(12)))
                s.resources[id] = gc
            }
            56 -> applyGc(s.gc(r.u32(4)), r.values(12, r.u32(8)))
            57 -> s.gc(r.u32(8)).copyFrom(s.gc(r.u32(4)), r.u32(12))
            58 -> s.gc(r.u32(4))
            59 -> { // SetClipRectangles
                val gc = s.gc(r.u32(4))
                gc.clipX = r.i16(8); gc.clipY = r.i16(10)
                val n = (r.size - 12) / 8
                gc.clipRects = IntArray(n * 4) { i -> val o = 12 + (i / 4) * 8 + (i % 4) * 2; if (i % 4 < 2) r.i16(o) else r.u16(o) }
                gc.clipMask = null
            }
            60 -> { s.gc(r.u32(4)); s.resources.remove(r.u32(4)) }
            61 -> { // ClearArea
                val w = s.window(r.u32(4))
                val x = r.i16(8); val y = r.i16(10)
                val ww = r.u16(12).let { if (it == 0) w.width - x else it }
                val hh = r.u16(14).let { if (it == 0) w.height - y else it }
                s.clearBackground(w, x, y, ww, hh)
                if (r.data != 0 && w.viewable) s.sendExpose(w, x, y, ww, hh)
            }
            62 -> { // CopyArea
                val src = s.drawable(r.u32(4)); val dst = s.drawable(r.u32(8)); val gc = s.gc(r.u32(12))
                if (src.depth != dst.depth) throw XError(Err.MATCH)
                val p = Paint(dst, gc)
                p.copyArea(src, r.i16(16), r.i16(18), r.i16(20), r.i16(22), r.u16(24), r.u16(26))
                s.drew(dst, p)
                if (gc.exposures) noExpose(dst.id, 62)
            }
            63 -> { // CopyPlane
                val src = s.drawable(r.u32(4)); val dst = s.drawable(r.u32(8)); val gc = s.gc(r.u32(12))
                val p = Paint(dst, gc)
                p.copyPlane(src, r.i16(16), r.i16(18), r.i16(20), r.i16(22), r.u16(24), r.u16(26), r.u32(28))
                s.drew(dst, p)
                if (gc.exposures) noExpose(dst.id, 63)
            }
            64 -> draw(r) { p, _, _ -> val (xs, ys) = points(r, 12, r.data == 1); for (i in xs.indices) p.point(xs[i], ys[i]) }
            65 -> draw(r) { p, _, _ -> val (xs, ys) = points(r, 12, r.data == 1); if (xs.isNotEmpty()) p.polyline(xs, ys) }
            66 -> draw(r) { p, _, _ -> for (o in 12 until r.size - 7 step 8) p.line(r.i16(o), r.i16(o + 2), r.i16(o + 4), r.i16(o + 6)) }
            67 -> draw(r) { p, _, _ -> for (o in 12 until r.size - 7 step 8) p.rectOutline(r.i16(o), r.i16(o + 2), r.u16(o + 4), r.u16(o + 6)) }
            68 -> draw(r) { p, _, _ ->
                for (o in 12 until r.size - 11 step 12) p.arc(r.i16(o), r.i16(o + 2), r.u16(o + 4), r.u16(o + 6), r.i16(o + 8), r.i16(o + 10))
            }
            69 -> draw(r) { p, _, _ ->
                val (xs, ys) = points(r, 16, r.u8(13) == 1)
                p.polygon(DoubleArray(xs.size) { xs[it] + 0.5 }, DoubleArray(ys.size) { ys[it] + 0.5 })
            }
            70 -> draw(r) { p, _, _ -> for (o in 12 until r.size - 7 step 8) p.fillRect(r.i16(o), r.i16(o + 2), r.u16(o + 4), r.u16(o + 6)) }
            71 -> draw(r) { p, _, _ ->
                for (o in 12 until r.size - 11 step 12) p.fillArc(r.i16(o), r.i16(o + 2), r.u16(o + 4), r.u16(o + 6), r.i16(o + 8), r.i16(o + 10))
            }
            72 -> putImage(r)
            73 -> getImage(r)
            74 -> polyText(r, false)
            75 -> polyText(r, true)
            76 -> draw(r) { p, _, _ -> p.text(r.i16(12), r.i16(14), IntArray(r.data) { r.u8(16 + it) }, true) }
            77 -> draw(r) { p, _, _ -> p.text(r.i16(12), r.i16(14), chars16(r, 16, r.data), true) }
            78 -> { val id = r.u32(4); newId(id); s.window(r.u32(8)); s.resources[id] = Colormap(id, this) }
            79, 81, 82 -> Unit
            80 -> { val id = r.u32(4); newId(id); s.resources[id] = Colormap(id, this) }
            83 -> reply(4) { it.s(8, 1).i(32, XServer.COLORMAP) }
            84 -> { // AllocColor (TrueColor: the pixel is the color)
                val red = r.u16(8) shr 8; val green = r.u16(10) shr 8; val blue = r.u16(12) shr 8
                reply { it.s(8, red * 257).s(10, green * 257).s(12, blue * 257).i(16, (red shl 16) or (green shl 8) or blue) }
            }
            85 -> {
                val c = Colors.lookup(r.str(12, r.u16(8))) ?: throw XError(Err.NAME)
                val rr = (c shr 16 and 255) * 257; val gg = (c shr 8 and 255) * 257; val bb = (c and 255) * 257
                reply { it.i(8, c).s(12, rr).s(14, gg).s(16, bb).s(18, rr).s(20, gg).s(22, bb) }
            }
            86, 87 -> throw XError(Err.ACCESS)
            88, 89, 90 -> Unit
            91 -> { // QueryColors
                val n = (r.size - 8) / 4
                reply(8 * n) { m ->
                    m.s(8, n)
                    for (i in 0 until n) {
                        val px = r.u32(8 + 4 * i)
                        m.s(32 + 8 * i, (px shr 16 and 255) * 257).s(34 + 8 * i, (px shr 8 and 255) * 257).s(36 + 8 * i, (px and 255) * 257)
                    }
                }
            }
            92 -> {
                val c = Colors.lookup(r.str(12, r.u16(8))) ?: throw XError(Err.NAME)
                val rr = (c shr 16 and 255) * 257; val gg = (c shr 8 and 255) * 257; val bb = (c and 255) * 257
                reply { it.s(8, rr).s(10, gg).s(12, bb).s(14, rr).s(16, gg).s(18, bb) }
            }
            93, 94 -> { val id = r.u32(4); newId(id); s.resources[id] = Cursor(id, this) }
            95 -> s.resources.remove(r.u32(4))
            96 -> Unit
            97 -> reply { it.s(8, r.u16(8)).s(10, r.u16(10)) }
            98 -> reply { it.b(8, 0).b(9, 0).b(10, 0).b(11, 0) }
            99 -> reply { it.b(1, 0) }
            100 -> { // ChangeKeyboardMapping
                val count = r.data; val first = r.u8(4); val per = r.u8(5)
                for (k in 0 until count) s.keyboard.set(first + k, IntArray(per) { r.u32(8 + 4 * (k * per + it)) })
                s.broadcastMapping(first, count)
            }
            101 -> { // GetKeyboardMapping
                val first = r.u8(4); val count = r.u8(5)
                val per = s.keyboard.perKeycode
                if (first < s.keyboard.minKeycode || first + count - 1 > s.keyboard.maxKeycode) throw XError(Err.VALUE, first)
                reply(4 * count * per) { m ->
                    m.b(1, per)
                    for (k in 0 until count) s.keyboard.keysyms(first + k).forEachIndexed { i, ks -> m.i(32 + 4 * (k * per + i), ks) }
                }
            }
            102, 105, 107, 109, 111, 112, 115 -> Unit
            103 -> reply(20) { m -> m.b(1, 1).i(8, 0).b(12, 0).b(13, 50).s(14, 400).s(16, 100); for (i in 20 until 52) m.b(i, 0xFF) }
            104 -> s.onBell?.invoke()
            106 -> reply { it.s(8, 2).s(10, 1).s(12, 4) }
            108 -> reply { it.s(8, 0).s(10, 0).b(12, 0).b(13, 0) }
            110 -> reply { it.b(1, 0).s(8, 0) }
            113 -> { val target = s.resources[r.u32(4)]?.owner; if (target != null && target !== this) target.close() }
            114 -> { // RotateProperties
                val w = s.window(r.u32(4)); val n = r.u16(8); val delta = r.i16(10)
                val atoms = IntArray(n) { r.u32(12 + 4 * it) }
                val props = atoms.map { w.props[it] ?: throw XError(Err.MATCH) }
                for (i in 0 until n) w.props[atoms[Math.floorMod(i + delta, n)]] = props[i]
                atoms.forEach { s.propertyNotify(w, it, false) }
            }
            116 -> reply { it.b(1, 0) }
            117 -> reply(8) { m -> m.b(1, 5); for (i in 0 until 5) m.b(32 + i, i + 1) }
            118 -> reply { it.b(1, 0) }
            119 -> { // GetModifierMapping
                val mods = s.keyboard.modifiers
                reply(mods.size) { m -> m.b(1, s.keyboard.perModifier); mods.forEachIndexed { i, k -> m.b(32 + i, k) } }
            }
            127 -> Unit
            else -> throw XError(Err.REQUEST)
        }
    }

    private fun noExpose(drawable: Int, major: Int) {
        server.send(this, 14) { it.i(4, drawable).s(8, 0).b(10, major) }
    }

    private fun sendEvent(r: Req) {
        val s = server
        val dest = r.u32(4); val mask = r.u32(8)
        val ev = r.bytes(12, 32)
        if (dest == XServer.CLIPWIN) {
            if (ev[0].toInt() and 0x7F == 31) s.clipboardReply(r.u32(12 + 20))
            return
        }
        ev[0] = (ev[0].toInt() or 0x80).toByte()
        var w: Window? = when (dest) {
            0 -> s.windowAt(s.pointerX, s.pointerY)
            1 -> when (s.focus) { 0 -> null; 1 -> s.windowAt(s.pointerX, s.pointerY); else -> s.resources[s.focus] as? Window }
            else -> s.window(dest)
        }
        fun to(c: XClient) {
            val copy = ev.copyOf()
            ByteBuffer.wrap(copy).order(c.order).putShort(2, c.seq.toShort())
            c.write(copy)
        }
        if (mask == 0) { w?.owner?.let(::to); return }
        while (w != null) {
            val targets = w.masks.filter { it.value and mask != 0 }.keys
            if (targets.isNotEmpty()) { targets.forEach(::to); return }
            if (r.data == 0 || w.doNotPropagate and mask != 0) return
            w = w.parent
        }
    }

    private fun putImage(r: Req) {
        val d = server.drawable(r.u32(4)); val gc = server.gc(r.u32(8))
        val w = r.u16(12); val h = r.u16(14); val dx = r.i16(16); val dy = r.i16(18)
        val leftPad = r.u8(20); val depth = r.u8(21); val format = r.data
        val p = Paint(d, gc)
        val data = 24
        when (format) {
            2 -> { // ZPixmap
                if (depth != d.depth && !(depth == 32 && d.depth == 24)) throw XError(Err.MATCH)
                if (depth == 1) {
                    val stride = (w + 31) / 32 * 4
                    for (y in 0 until h) for (x in 0 until w) p.put(dx + x, dy + y, (r.u8(data + y * stride + x / 8) shr (x % 8)) and 1)
                } else {
                    val bb = r.buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    for (y in 0 until h) {
                        val row = data + y * w * 4
                        for (x in 0 until w) p.put(dx + x, dy + y, bb.getInt(row + x * 4) and 0xFFFFFF)
                    }
                }
            }
            0, 1 -> { // XYBitmap (fg/bg) or XYPixmap (planes, most significant first)
                if (format == 0 && depth != 1) throw XError(Err.MATCH)
                val stride = (w + leftPad + 31) / 32 * 4
                val planes = if (format == 0) 1 else depth
                val planeSize = stride * h
                for (y in 0 until h) for (x in 0 until w) {
                    val bx = x + leftPad
                    var v = 0
                    for (pl in 0 until planes) v = (v shl 1) or ((r.u8(data + pl * planeSize + y * stride + bx / 8) shr (bx % 8)) and 1)
                    p.put(dx + x, dy + y, if (format == 0) (if (v != 0) gc.fg else gc.bg) else v)
                }
            }
            else -> throw XError(Err.VALUE, format)
        }
        server.drew(d, p)
    }

    private fun getImage(r: Req) {
        val s = server
        val d = s.drawable(r.u32(4))
        val x0 = r.i16(8); val y0 = r.i16(10); val w = r.u16(12); val h = r.u16(14)
        if (x0 < 0 || y0 < 0 || x0 + w > d.width || y0 + h > d.height) throw XError(Err.MATCH)
        val win = d as? Window
        if (win != null && !win.viewable) throw XError(Err.MATCH)
        fun px(x: Int, y: Int) = if (win != null) s.screenPixel(win.absX + x, win.absY + y) else d.pixels[y * d.width + x]
        val planeMask = r.u32(16)
        if (r.data == 2 && d.depth != 1) {
            reply(w * h * 4) { m ->
                m.b(1, d.depth).i(8, XServer.VISUAL)
                val bb = m.buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                for (y in 0 until h) for (x in 0 until w) bb.putInt(32 + (y * w + x) * 4, px(x0 + x, y0 + y) and planeMask)
            }
        } else {
            // Depth 1 Z format, or XY format: one bitmap per plane, most significant first.
            val stride = (w + 31) / 32 * 4
            val planes = (d.depth - 1 downTo 0).filter { r.data == 2 || planeMask and (1 shl it) != 0 }
            reply(stride * h * planes.size) { m ->
                m.b(1, d.depth).i(8, if (win != null) XServer.VISUAL else 0)
                planes.forEachIndexed { pi, plane ->
                    for (y in 0 until h) for (x in 0 until w) if (px(x0 + x, y0 + y) and (1 shl plane) != 0) {
                        val o = 32 + pi * stride * h + y * stride + x / 8
                        m.b(o, m.buf.get(o).toInt() or (1 shl (x % 8)))
                    }
                }
            }
        }
    }
}
