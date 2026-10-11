package com.mnvstudio.x11.linux

import android.system.Os
import java.io.File
import java.io.InputStream

/**
 * Minimal tar extractor for Linux root filesystems: regular files, directories, symlinks and hard links,
 * with permissions, GNU long names and pax path headers. Entries can't escape [dest].
 */
object Tar {
    fun extract(input: InputStream, dest: File, onEntry: (String) -> Unit = {}) {
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        val root = dest.canonicalPath
        while (true) {
            if (!readFully(input, header)) return
            if (header.all { it.toInt() == 0 }) return
            val size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            var name = longName ?: (str(header, 345, 155).let { if (it.isEmpty()) "" else "$it/" } + str(header, 0, 100))
            val link = longLink ?: str(header, 157, 100)
            longName = null; longLink = null
            when (type) {
                'L', 'K' -> { val v = String(readData(input, size), Charsets.UTF_8).trimEnd('\u0000'); if (type == 'L') longName = v else longLink = v; continue }
                'x' -> { pax(String(readData(input, size), Charsets.UTF_8)).let { longName = it["path"]; longLink = it["linkpath"] }; continue }
                'g' -> { skip(input, size); continue }
            }
            name = name.removePrefix("./").trimEnd('/')
            if (name.isEmpty()) { skip(input, size); continue }
            val out = File(dest, name)
            if (!out.canonicalPath.startsWith(root) && !out.parentFile!!.canonicalPath.startsWith(root)) { skip(input, size); continue }
            val mode = octal(header, 100, 8).toInt() and 0xFFF
            onEntry(name)
            out.parentFile?.mkdirs()
            when (type) {
                '5' -> { out.mkdirs(); runCatching { Os.chmod(out.path, mode or 0x1C0) } }
                '2' -> { out.delete(); runCatching { Os.symlink(link, out.path) } }
                '1' -> {
                    out.delete()
                    val target = File(dest, link.removePrefix("./"))
                    runCatching { Os.link(target.path, out.path) }.onFailure { runCatching { target.copyTo(out, true) } }
                }
                '0', '\u0000', '7' -> {
                    out.delete()
                    out.outputStream().use { o ->
                        var left = size
                        val buf = ByteArray(64 * 1024)
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) return
                            o.write(buf, 0, n); left -= n
                        }
                    }
                    skip(input, (512 - size % 512) % 512)
                    runCatching { Os.chmod(out.path, mode or 0x180) }
                    continue
                }
                else -> {}
            }
            skip(input, size + (512 - size % 512) % 512)
        }
    }

    private fun readData(input: InputStream, size: Long): ByteArray {
        val b = ByteArray(size.toInt())
        readFully(input, b)
        skip(input, (512 - size % 512) % 512)
        return b
    }

    private fun pax(s: String): Map<String, String> = s.lines().mapNotNull { line ->
        val kv = line.substringAfter(' ', "")
        val i = kv.indexOf('=')
        if (i > 0) kv.substring(0, i) to kv.substring(i + 1) else null
    }.toMap()

    private fun readFully(input: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) { val n = input.read(b, off, b.size - off); if (n < 0) return false; off += n }
        return true
    }

    private fun skip(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) { val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) return; left -= r }
    }

    private fun str(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end].toInt() != 0) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        // Large sizes use base-256 (high bit set).
        if (b[off].toInt() and 0x80 != 0) { var v = 0L; for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xFF); return v }
        return str(b, off, len).trim().ifEmpty { "0" }.toLong(8)
    }
}
