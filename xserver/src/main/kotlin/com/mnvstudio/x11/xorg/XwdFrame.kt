package com.mnvstudio.x11.xorg

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * The live screen of an X.Org server started with `Xvfb -fbdir DIR`: Xvfb keeps its framebuffer in an XWD file
 * and maps it into memory, so mapping the same file here shows every pixel it draws with no copying over sockets.
 */
class XwdFrame private constructor(private val map: MappedByteBuffer, val width: Int, val height: Int, private val stride: Int, private val offset: Int) {
    private val pixels: IntBuffer

    init {
        map.order(ByteOrder.LITTLE_ENDIAN)
        map.position(offset)
        pixels = map.slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    }

    companion object {
        /** Opens Xvfb_screen0 in [dir]; null until Xvfb has created it. */
        fun open(dir: File, screen: Int = 0): XwdFrame? {
            val f = File(dir, "Xvfb_screen$screen")
            if (!f.exists() || f.length() < 100) return null
            val ch = RandomAccessFile(f, "r").channel
            val map = ch.map(FileChannel.MapMode.READ_ONLY, 0, f.length())
            ch.close()
            // The header is 25 big-endian CARD32s, then the window name, then ncolors 12-byte colormap entries.
            map.order(ByteOrder.BIG_ENDIAN)
            val headerSize = map.getInt(0)
            val bpp = map.getInt(44)
            val stride = map.getInt(48)
            val ncolors = map.getInt(76)
            val w = map.getInt(16); val h = map.getInt(20)
            require(bpp == 32) { "Xvfb must run at depth 24 (32 bits per pixel), got $bpp" }
            return XwdFrame(map, w, h, stride, headerSize + ncolors * 12)
        }
    }

    /** Copies the whole screen into [dst] (width × height, ARGB with full alpha). */
    fun copyTo(dst: IntArray) {
        val rowInts = stride / 4
        for (y in 0 until height) {
            pixels.position(y * rowInts)
            pixels.get(dst, y * width, width)
        }
        for (i in 0 until width * height) dst[i] = dst[i] or -0x1000000
    }
}
