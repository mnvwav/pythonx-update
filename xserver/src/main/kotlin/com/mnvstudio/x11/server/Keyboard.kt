package com.mnvstudio.x11.server

/**
 * US keyboard map. Keycodes are Linux evdev codes + 8, as on a desktop X server, with two keysyms per key
 * (plain, shifted). Characters outside the layout are typed through spare keycodes that are remapped on the fly.
 */
class Keyboard {
    val minKeycode = 8
    val maxKeycode = 255
    val perKeycode = 2
    val map = IntArray((maxKeycode + 1) * perKeycode)

    /** Modifier map: Shift, Lock, Control, Mod1..Mod5, [perModifier] keycodes each (0 = unused). */
    val perModifier = 2
    val modifiers = intArrayOf(
        SHIFT_L, SHIFT_R, CAPS, 0, CTRL_L, CTRL_R, ALT_L, ALT_R, 0, 0, 0, 0, SUPER_L, SUPER_R, 0, 0,
    )
    private var nextSpare = SPARE_FIRST

    init {
        fun key(code: Int, plain: Int, shifted: Int = plain) { map[(code + 8) * 2] = plain; map[(code + 8) * 2 + 1] = shifted }
        "1!2@3#4$5%6^7&8*9(0)".chunked(2).forEachIndexed { i, s -> key(2 + i, s[0].code, s[1].code) }
        key(12, '-'.code, '_'.code); key(13, '='.code, '+'.code)
        "qwertyuiop".forEachIndexed { i, c -> key(16 + i, c.code, c.uppercaseChar().code) }
        key(26, '['.code, '{'.code); key(27, ']'.code, '}'.code)
        "asdfghjkl".forEachIndexed { i, c -> key(30 + i, c.code, c.uppercaseChar().code) }
        key(39, ';'.code, ':'.code); key(40, '\''.code, '"'.code); key(41, '`'.code, '~'.code); key(43, '\\'.code, '|'.code)
        "zxcvbnm".forEachIndexed { i, c -> key(44 + i, c.code, c.uppercaseChar().code) }
        key(51, ','.code, '<'.code); key(52, '.'.code, '>'.code); key(53, '/'.code, '?'.code); key(57, ' '.code)
        key(1, 0xff1b); key(14, 0xff08); key(15, 0xff09, 0xfe20); key(28, 0xff0d)
        key(29, 0xffe3); key(97, 0xffe4); key(42, 0xffe1); key(54, 0xffe2); key(56, 0xffe9); key(100, 0xffea)
        key(58, 0xffe5); key(125, 0xffeb); key(126, 0xffec); key(55, 0xffaa)
        for (i in 0 until 10) key(59 + i, 0xffbe + i)
        key(87, 0xffc8); key(88, 0xffc9)
        key(102, 0xff50); key(103, 0xff52); key(104, 0xff55); key(105, 0xff51); key(106, 0xff53)
        key(107, 0xff57); key(108, 0xff54); key(109, 0xff56); key(110, 0xff63); key(111, 0xffff)
    }

    fun keysyms(code: Int): IntArray = IntArray(perKeycode) { map.getOrElse(code * perKeycode + it) { 0 } }

    fun set(code: Int, syms: IntArray) { for (i in 0 until perKeycode) map[code * perKeycode + i] = syms.getOrElse(i) { 0 } }

    /** Keycode and shift state that produce keysym [ks], or null if no key has it. */
    fun find(ks: Int): Pair<Int, Boolean>? {
        for (code in minKeycode..maxKeycode) {
            if (map[code * 2] == ks) return code to false
            if (map[code * 2 + 1] == ks) return code to true
        }
        return null
    }

    /** Puts [ks] on a spare keycode (round robin) and returns it; the caller announces the change with MappingNotify. */
    fun assignSpare(ks: Int): Int {
        val code = nextSpare
        nextSpare = if (nextSpare == SPARE_LAST) SPARE_FIRST else nextSpare + 1
        set(code, intArrayOf(ks, ks))
        return code
    }

    /** Modifier state bit for a modifier keycode, or 0. */
    fun modBit(code: Int): Int {
        val i = modifiers.indexOf(code)
        return if (code == 0 || i < 0) 0 else 1 shl (i / perModifier)
    }

    companion object {
        const val SHIFT_L = 50; const val SHIFT_R = 62; const val CAPS = 66; const val CTRL_L = 37; const val CTRL_R = 105
        const val ALT_L = 64; const val ALT_R = 108; const val SUPER_L = 133; const val SUPER_R = 134
        private const val SPARE_FIRST = 200; private const val SPARE_LAST = 250

        /** Keysym for a Unicode code point: Latin-1 maps directly, the rest uses the 0x01000000 range. */
        fun keysymFor(cp: Int): Int = when {
            cp == '\n'.code || cp == '\r'.code -> 0xff0d
            cp == '\t'.code -> 0xff09
            cp == 8 -> 0xff08
            cp == 27 -> 0xff1b
            cp in 0x20..0x7e || cp in 0xa0..0xff -> cp
            else -> 0x01000000 or cp
        }
    }
}
