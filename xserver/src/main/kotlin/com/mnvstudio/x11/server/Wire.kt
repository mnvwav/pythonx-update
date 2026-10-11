package com.mnvstudio.x11.server

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** X11 error codes (core protocol). */
object Err {
    const val REQUEST = 1; const val VALUE = 2; const val WINDOW = 3; const val PIXMAP = 4; const val ATOM = 5
    const val CURSOR = 6; const val FONT = 7; const val MATCH = 8; const val DRAWABLE = 9; const val ACCESS = 10
    const val ALLOC = 11; const val COLORMAP = 12; const val GCONTEXT = 13; const val IDCHOICE = 14; const val NAME = 15
    const val LENGTH = 16; const val IMPLEMENTATION = 17
}

/** Thrown while handling a request; turned into an X error packet for the client. */
class XError(val code: Int, val value: Int = 0) : Exception(null, null, false, false)

fun pad4(n: Int) = (n + 3) and 3.inv()

/** One request, read with the client's byte order. Offsets are absolute from the start of the request, as in the spec. */
class Req(val buf: ByteBuffer) {
    val opcode get() = u8(0)
    val data get() = u8(1)
    val size get() = buf.limit()
    fun u8(o: Int) = buf.get(o).toInt() and 0xFF
    fun i8(o: Int) = buf.get(o).toInt()
    fun u16(o: Int) = buf.getShort(o).toInt() and 0xFFFF
    fun i16(o: Int) = buf.getShort(o).toInt()
    fun u32(o: Int) = buf.getInt(o)
    fun bytes(o: Int, n: Int): ByteArray = ByteArray(n).also { for (i in 0 until n) it[i] = buf.get(o + i) }
    fun str(o: Int, n: Int) = String(bytes(o, n), Charsets.ISO_8859_1)
    fun need(n: Int) { if (size < n) throw XError(Err.LENGTH) }

    /** Values of a bitmask-selected value list, as (bit, value) pairs in bit order. */
    fun values(o: Int, mask: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var p = o
        for (bit in 0 until 32) if (mask and (1 shl bit) != 0) {
            if (p + 4 > size) throw XError(Err.LENGTH)
            out += (1 shl bit) to u32(p); p += 4
        }
        return out
    }
}

/** Builds a fixed-layout message (reply, event or error) in a client's byte order. */
class Msg(size: Int, order: ByteOrder) {
    val buf: ByteBuffer = ByteBuffer.allocate(size).order(order)
    fun b(o: Int, v: Int) = apply { buf.put(o, v.toByte()) }
    fun s(o: Int, v: Int) = apply { buf.putShort(o, v.toShort()) }
    fun i(o: Int, v: Int) = apply { buf.putInt(o, v) }
    fun bytes(o: Int, a: ByteArray) = apply { for (k in a.indices) buf.put(o + k, a[k]) }
    fun str(o: Int, s: String) = bytes(o, s.toByteArray(Charsets.ISO_8859_1))
    fun array(): ByteArray = buf.array()
}
