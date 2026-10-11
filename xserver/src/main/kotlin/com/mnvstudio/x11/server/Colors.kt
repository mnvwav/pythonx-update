package com.mnvstudio.x11.server

/** Color names for AllocNamedColor and LookupColor: the common part of X11's rgb.txt, plus grayN and #rgb forms. */
object Colors {
    private val named: Map<String, Int> = """
        black 000000 white ffffff red ff0000 green 00ff00 blue 0000ff yellow ffff00 cyan 00ffff magenta ff00ff
        gray bebebe grey bebebe darkgray a9a9a9 darkgrey a9a9a9 lightgray d3d3d3 lightgrey d3d3d3 dimgray 696969
        dimgrey 696969 slategray 708090 slategrey 708090 lightslategray 778899 gainsboro dcdcdc whitesmoke f5f5f5
        snow fffafa ivory fffff0 beige f5f5dc linen faf0e6 wheat f5deb3 tan d2b48c khaki f0e68c lavender e6e6fa
        navy 000080 navyblue 000080 midnightblue 191970 darkblue 00008b mediumblue 0000cd royalblue 4169e1
        steelblue 4682b4 lightsteelblue b0c4de dodgerblue 1e90ff deepskyblue 00bfff skyblue 87ceeb lightskyblue 87cefa
        lightblue add8e6 powderblue b0e0e6 cadetblue 5f9ea0 slateblue 6a5acd darkslateblue 483d8b cornflowerblue 6495ed
        turquoise 40e0d0 darkturquoise 00ced1 aquamarine 7fffd4 teal 008080 darkcyan 008b8b lightcyan e0ffff
        darkgreen 006400 forestgreen 228b22 seagreen 2e8b57 mediumseagreen 3cb371 limegreen 32cd32 lime 00ff00
        lawngreen 7cfc00 chartreuse 7fff00 palegreen 98fb98 lightgreen 90ee90 springgreen 00ff7f olivedrab 6b8e23
        darkolivegreen 556b2f yellowgreen 9acd32 olive 808000 gold ffd700 goldenrod daa520 darkgoldenrod b8860b
        lightyellow ffffe0 lemonchiffon fffacd orange ffa500 darkorange ff8c00 coral ff7f50 tomato ff6347
        orangered ff4500 salmon fa8072 lightsalmon ffa07a darksalmon e9967a indianred cd5c5c firebrick b22222
        darkred 8b0000 maroon b03060 brown a52a2a sienna a0522d chocolate d2691e peru cd853f sandybrown f4a460
        burlywood deb887 rosybrown bc8f8f pink ffc0cb lightpink ffb6c1 hotpink ff69b4 deeppink ff1493
        mediumvioletred c71585 palevioletred db7093 violet ee82ee orchid da70d6 plum dda0dd thistle d8bfd8
        purple a020f0 mediumpurple 9370db darkviolet 9400d3 darkorchid 9932cc darkmagenta 8b008b blueviolet 8a2be2
        indigo 4b0082 mintcream f5fffa honeydew f0fff0 aliceblue f0f8ff ghostwhite f8f8ff seashell fff5ee
        oldlace fdf5e6 floralwhite fffaf0 antiquewhite faebd7 papayawhip ffefd5 blanchedalmond ffebcd bisque ffe4c4
        peachpuff ffdab9 navajowhite ffdead moccasin ffe4b5 cornsilk fff8dc mistyrose ffe4e1 lavenderblush fff0f5
        darkslategray 2f4f4f darkslategrey 2f4f4f lightgoldenrod eedd82 palegoldenrod eee8aa darkkhaki bdb76b
    """.trim().split(Regex("\\s+")).chunked(2).associate { it[0] to it[1].toInt(16) }

    /** 0xRRGGBB for a color name or spec (case and spaces ignored), or null. */
    fun lookup(spec: String): Int? {
        val s = spec.lowercase().replace(" ", "")
        named[s]?.let { return it }
        // Numbered variants like red3 or gray50.
        Regex("(gr[ae]y)(\\d{1,3})").matchEntire(s)?.let { m ->
            val n = m.groupValues[2].toInt(); if (n <= 100) { val v = (n * 255 + 50) / 100; return (v shl 16) or (v shl 8) or v }
        }
        Regex("([a-z]+)([1-4])").matchEntire(s)?.let { m ->
            val base = named[m.groupValues[1]] ?: return@let
            val f = doubleArrayOf(1.0, 0.932, 0.804, 0.545)[m.groupValues[2].toInt() - 1]
            return (((base shr 16 and 255) * f).toInt() shl 16) or (((base shr 8 and 255) * f).toInt() shl 8) or ((base and 255) * f).toInt()
        }
        if (s.startsWith("#")) {
            val h = s.substring(1)
            if (h.isNotEmpty() && h.length % 3 == 0 && h.all { it.isDigit() || it in 'a'..'f' }) {
                val n = h.length / 3
                fun c(i: Int) = h.substring(i * n, i * n + n).toInt(16).let { v -> if (n == 1) v * 17 else v shr (4 * (n - 2)) }
                return (c(0) shl 16) or (c(1) shl 8) or c(2)
            }
        }
        if (s.startsWith("rgb:")) {
            val p = s.substring(4).split('/')
            if (p.size == 3) runCatching {
                fun c(t: String) = (t.toInt(16) * 255 / ((1 shl (4 * t.length)) - 1))
                return (c(p[0]) shl 16) or (c(p[1]) shl 8) or c(p[2])
            }
        }
        return null
    }
}
