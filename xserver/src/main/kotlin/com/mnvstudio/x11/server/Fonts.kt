package com.mnvstudio.x11.server

/**
 * A character-cell bitmap font covering ISO 8859-1, from the public-domain misc-fixed family.
 * Glyphs are [height] rows of [rowBytes] bytes, most significant bit = leftmost pixel.
 */
class XFont(val name: String, val width: Int, val ascent: Int, val descent: Int, private val bits: ByteArray) {
    val height = ascent + descent
    private val rowBytes = (width + 7) / 8

    fun pixel(ch: Int, x: Int, y: Int): Boolean {
        if (ch !in 0..255 || x !in 0 until width || y !in 0 until height) return false
        val b = bits[(ch * height + y) * rowBytes + x / 8].toInt()
        return b and (0x80 ushr (x % 8)) != 0
    }

    companion object {
        fun load(name: String, file: String): XFont {
            val d = XFont::class.java.getResourceAsStream("fonts/$file.xfnt")!!.use { it.readBytes() }
            require(String(d, 0, 4, Charsets.US_ASCII) == "XFNT")
            return XFont(name, d[5].toInt(), d[6].toInt(), d[7].toInt(), d.copyOfRange(10, d.size))
        }
    }
}

/** The fonts the server offers, with XLFD names and the short aliases (fixed, 9x15, ...) clients expect. */
object Fonts {
    private class Entry(val file: String, val xlfd: String, val aliases: List<String>)

    private val entries = listOf(
        Entry("6x13", "-misc-fixed-medium-r-semicondensed--13-120-75-75-c-60-iso8859-1", listOf("fixed", "6x13", "cursor", "variable")),
        Entry("6x13B", "-misc-fixed-bold-r-semicondensed--13-120-75-75-c-60-iso8859-1", listOf("6x13bold")),
        Entry("5x8", "-misc-fixed-medium-r-normal--8-80-75-75-c-50-iso8859-1", listOf("5x8")),
        Entry("6x10", "-misc-fixed-medium-r-normal--10-100-75-75-c-60-iso8859-1", listOf("6x10")),
        Entry("7x14", "-misc-fixed-medium-r-normal--14-130-75-75-c-70-iso8859-1", listOf("7x14")),
        Entry("7x14B", "-misc-fixed-bold-r-normal--14-130-75-75-c-70-iso8859-1", listOf("7x14bold")),
        Entry("8x13", "-misc-fixed-medium-r-normal--13-120-75-75-c-80-iso8859-1", listOf("8x13")),
        Entry("8x13B", "-misc-fixed-bold-r-normal--13-120-75-75-c-80-iso8859-1", listOf("8x13bold")),
        Entry("9x15", "-misc-fixed-medium-r-normal--15-140-75-75-c-90-iso8859-1", listOf("9x15")),
        Entry("9x15B", "-misc-fixed-bold-r-normal--15-140-75-75-c-90-iso8859-1", listOf("9x15bold")),
        Entry("10x20", "-misc-fixed-medium-r-normal--20-200-75-75-c-100-iso8859-1", listOf("10x20")),
        Entry("12x24", "-misc-fixed-medium-r-normal--24-170-100-100-c-120-iso8859-1", listOf("12x24")),
    )
    private val loaded = HashMap<String, XFont>()

    private fun font(e: Entry) = synchronized(loaded) { loaded.getOrPut(e.file) { XFont.load(e.xlfd, e.file) } }

    /** Glob match with X's wildcards: '*' any run, '?' one character; case-insensitive. */
    fun matches(pattern: String, name: String): Boolean {
        val p = pattern.lowercase(); val n = name.lowercase()
        // ok[j]: the pattern so far matches n[0 until j].
        var ok = BooleanArray(n.length + 1).also { it[0] = true }
        for (c in p) {
            val next = BooleanArray(n.length + 1)
            if (c == '*') { var any = false; for (j in 0..n.length) { any = any || ok[j]; next[j] = any } }
            else for (j in 1..n.length) next[j] = ok[j - 1] && (c == '?' || c == n[j - 1])
            ok = next
        }
        return ok[n.length]
    }

    /** Names matching [pattern]; an XLFD pattern with no exact match gets the closest font so clients still find one. */
    fun list(pattern: String, max: Int): List<String> {
        val out = entries.flatMap { listOf(it.xlfd) + it.aliases }.filter { matches(pattern, it) }
        return (out.ifEmpty { closest(pattern)?.let { listOf(it.xlfd) } ?: emptyList() }).distinct().take(max)
    }

    fun open(name: String): XFont? {
        entries.firstOrNull { e -> matches(name, e.xlfd) || e.aliases.any { matches(name, it) } }?.let { return font(it) }
        return closest(name)?.let { font(it) }
    }

    /** For XLFD names we don't have (helvetica, courier ...): the fixed font nearest in pixel size and weight. */
    private fun closest(name: String): Entry? {
        if (!name.startsWith("-")) return null
        val f = name.split('-')
        val px = f.getOrNull(7)?.toIntOrNull() ?: f.getOrNull(8)?.toIntOrNull()?.let { it / 10 } ?: 13
        val bold = f.getOrNull(3)?.let { it.contains("bold") || it.contains("demi") } == true
        return entries.filter { it.xlfd.contains("-bold-") == bold }
            .minByOrNull { kotlin.math.abs(it.xlfd.split('-')[7].toInt() - px) }
    }
}
