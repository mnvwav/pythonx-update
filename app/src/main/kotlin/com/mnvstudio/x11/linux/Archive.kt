package com.mnvstudio.x11.linux

import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Archives a Linux root filesystem as tar.gz (files, folders, symlinks, executable bits), optionally encrypted with a
 * password. Used for backups, snapshots and cloning. Pure Java so it can be tested off-device.
 *
 * Encrypted format: "X11E" + version byte + 16-byte salt + 16-byte IV, then AES-256-CTR data, then a 32-byte
 * HMAC-SHA256 over everything before it. Keys come from PBKDF2-HMAC-SHA256 (210 000 rounds). Decryption checks the MAC
 * before anything is extracted.
 */
object Archive {
    private const val MAGIC = "X11E"
    private const val ROUNDS = 210_000

    // ---------------------------------------------------------------- tar writing

    /** Writes [root] as tar.gz to [out]; [progress] gets the number of entries written. */
    fun writeTarGz(root: File, out: OutputStream, progress: (Int) -> Unit = {}) {
        val gz = GZIPOutputStream(out.buffered(), 64 * 1024)
        val base = root.toPath()
        var n = 0
        Files.walk(base).use { stream ->
            for (p in stream) {
                if (p == base) continue
                val rel = base.relativize(p).toString().replace(File.separatorChar, '/')
                if (rel.startsWith(".x11-") && rel.count { it == '/' } == 0 && rel != ".x11-ready") continue
                writeEntry(gz, p, rel)
                if (++n % 100 == 0) progress(n)
            }
        }
        gz.write(ByteArray(1024)) // two empty blocks end the archive
        gz.finish(); gz.flush()
        progress(n)
    }

    private fun writeEntry(out: OutputStream, p: Path, rel: String) {
        val link = Files.isSymbolicLink(p)
        val dir = !link && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
        val target = if (link) Files.readSymbolicLink(p).toString() else ""
        val size = if (link || dir) 0L else Files.size(p)
        val mode = when { dir -> "755"; link -> "777"; Files.isExecutable(p) -> "755"; else -> "644" }
        val name = if (dir) "$rel/" else rel
        if (name.toByteArray().size > 100) longEntry(out, 'L', name)
        if (target.toByteArray().size > 100) longEntry(out, 'K', target)
        out.write(header(name, mode, size, when { dir -> '5'; link -> '2'; else -> '0' }, target))
        if (!link && !dir) {
            Files.newInputStream(p).use { it.copyTo(out, 64 * 1024) }
            pad(out, size)
        }
    }

    private fun longEntry(out: OutputStream, type: Char, value: String) {
        val b = value.toByteArray() + 0
        out.write(header("././@LongLink", "644", b.size.toLong(), type, ""))
        out.write(b); pad(out, b.size.toLong())
    }

    private fun pad(out: OutputStream, size: Long) { val r = (512 - size % 512) % 512; if (r > 0) out.write(ByteArray(r.toInt())) }

    private fun header(name: String, mode: String, size: Long, type: Char, link: String): ByteArray {
        val h = ByteArray(512)
        fun put(off: Int, len: Int, s: String) { val b = s.toByteArray(); System.arraycopy(b, 0, h, off, minOf(b.size, len)) }
        fun octal(off: Int, len: Int, v: Long) = put(off, len, v.toString(8).padStart(len - 1, '0'))
        put(0, 100, name); octal(100, 8, mode.toLong(8)); octal(108, 8, 0); octal(116, 8, 0)
        octal(124, 12, size); octal(136, 12, System.currentTimeMillis() / 1000)
        h[156] = type.code.toByte(); put(157, 100, link); put(257, 6, "ustar"); put(263, 2, "00")
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        val sum = h.sumOf { it.toInt() and 0xFF }
        put(148, 8, sum.toString(8).padStart(6, '0') + "\u0000 ")
        return h
    }

    // ---------------------------------------------------------------- encryption

    private fun keys(password: CharArray, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = f.generateSecret(PBEKeySpec(password, salt, ROUNDS, 512)).encoded
        return SecretKeySpec(raw.copyOfRange(0, 32), "AES") to SecretKeySpec(raw.copyOfRange(32, 64), "HmacSHA256")
    }

    /** Wraps [out] so everything written to the result is encrypted; close the result to write the MAC. */
    fun encrypting(out: OutputStream, password: CharArray): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(16).also { rnd.nextBytes(it) }
        val (enc, macKey) = keys(password, salt)
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        val head = MAGIC.toByteArray() + byteArrayOf(1) + salt + iv
        out.write(head); mac.update(head)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, enc, IvParameterSpec(iv)) }
        return object : FilterOutputStream(out) {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                val c = cipher.update(b, off, len) ?: return
                mac.update(c); out.write(c)
            }
            override fun close() {
                cipher.doFinal()?.let { if (it.isNotEmpty()) { mac.update(it); out.write(it) } }
                out.write(mac.doFinal()); out.flush(); out.close()
            }
        }
    }

    fun isEncrypted(head: ByteArray) = head.size >= 4 && String(head, 0, 4, Charsets.US_ASCII) == MAGIC

    /**
     * Decrypts an encrypted archive from [input] into [plain] (a temporary file), checking the password and that
     * nothing was changed. Throws with a readable message otherwise.
     */
    fun decryptTo(input: InputStream, password: CharArray, plain: File) {
        val tmp = File(plain.path + ".enc")
        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        try {
            val total = tmp.length()
            if (total < 37 + 32) error("This file is too short to be an X11 backup")
            tmp.inputStream().buffered().use { i ->
                val head = ByteArray(37).also { readFully(i, it) }
                if (!isEncrypted(head)) error("This isn't an encrypted X11 backup")
                val (enc, macKey) = keys(password, head.copyOfRange(5, 21))
                // First pass: verify the MAC.
                val mac = Mac.getInstance("HmacSHA256").apply { init(macKey); update(head) }
                var left = total - 37 - 32
                val buf = ByteArray(64 * 1024)
                while (left > 0) { val n = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; mac.update(buf, 0, n); left -= n }
                val tag = ByteArray(32).also { readFully(i, it) }
                if (!MessageDigest.isEqual(tag, mac.doFinal())) error("Wrong password, or the backup is damaged")
                // Second pass: decrypt.
                tmp.inputStream().buffered().use { j ->
                    j.skip(37)
                    val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, enc, IvParameterSpec(head.copyOfRange(21, 37))) }
                    plain.outputStream().buffered().use { o ->
                        var rest = total - 37 - 32
                        while (rest > 0) {
                            val n = j.read(buf, 0, minOf(buf.size.toLong(), rest).toInt()); if (n < 0) break
                            cipher.update(buf, 0, n)?.let { o.write(it) }; rest -= n
                        }
                        cipher.doFinal()?.let { o.write(it) }
                    }
                }
            }
        } finally { tmp.delete() }
    }

    private fun readFully(i: InputStream, b: ByteArray) { var o = 0; while (o < b.size) { val n = i.read(b, o, b.size - o); if (n < 0) error("Unexpected end of file"); o += n } }

    // ---------------------------------------------------------------- copying

    /** Copies a directory tree keeping symlinks as symlinks (for cloning profiles). */
    fun copyTree(from: File, to: File, progress: (Int) -> Unit = {}) {
        val src = from.toPath(); val dst = to.toPath()
        var n = 0
        Files.walk(src).use { stream ->
            for (p in stream) {
                val t = dst.resolve(src.relativize(p).toString())
                when {
                    Files.isSymbolicLink(p) -> { Files.deleteIfExists(t); Files.createSymbolicLink(t, Files.readSymbolicLink(p)) }
                    Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectories(t)
                    else -> Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                }
                if (++n % 200 == 0) progress(n)
            }
        }
    }

    /** Deletes a tree without following symlinks out of it. */
    fun deleteTree(dir: File) {
        if (!dir.exists() && !Files.isSymbolicLink(dir.toPath())) return
        Files.walk(dir.toPath()).use { s -> s.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } } }
    }

    @Suppress("unused") private fun path(s: String): Path = Paths.get(s)
}
