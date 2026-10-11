package com.mnvstudio.x11.display

import android.view.KeyEvent

/** Android key codes to X keycodes (Linux evdev + 8) for a US layout, matching the server's keyboard map. */
object KeyMap {
    private val map = HashMap<Int, Int>()

    init {
        fun k(android: Int, evdev: Int) { map[android] = evdev + 8 }
        val letters = intArrayOf(30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44)
        for (i in 0 until 26) k(KeyEvent.KEYCODE_A + i, letters[i])
        k(KeyEvent.KEYCODE_0, 11)
        for (i in 1..9) k(KeyEvent.KEYCODE_0 + i, 1 + i)
        k(KeyEvent.KEYCODE_ENTER, 28); k(KeyEvent.KEYCODE_NUMPAD_ENTER, 28)
        k(KeyEvent.KEYCODE_DEL, 14); k(KeyEvent.KEYCODE_FORWARD_DEL, 111); k(KeyEvent.KEYCODE_TAB, 15)
        k(KeyEvent.KEYCODE_ESCAPE, 1); k(KeyEvent.KEYCODE_SPACE, 57)
        k(KeyEvent.KEYCODE_MINUS, 12); k(KeyEvent.KEYCODE_EQUALS, 13)
        k(KeyEvent.KEYCODE_LEFT_BRACKET, 26); k(KeyEvent.KEYCODE_RIGHT_BRACKET, 27); k(KeyEvent.KEYCODE_BACKSLASH, 43)
        k(KeyEvent.KEYCODE_SEMICOLON, 39); k(KeyEvent.KEYCODE_APOSTROPHE, 40); k(KeyEvent.KEYCODE_GRAVE, 41)
        k(KeyEvent.KEYCODE_COMMA, 51); k(KeyEvent.KEYCODE_PERIOD, 52); k(KeyEvent.KEYCODE_SLASH, 53)
        k(KeyEvent.KEYCODE_SHIFT_LEFT, 42); k(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        k(KeyEvent.KEYCODE_CTRL_LEFT, 29); k(KeyEvent.KEYCODE_CTRL_RIGHT, 97)
        k(KeyEvent.KEYCODE_ALT_LEFT, 56); k(KeyEvent.KEYCODE_ALT_RIGHT, 100)
        k(KeyEvent.KEYCODE_META_LEFT, 125); k(KeyEvent.KEYCODE_META_RIGHT, 126); k(KeyEvent.KEYCODE_CAPS_LOCK, 58)
        k(KeyEvent.KEYCODE_DPAD_UP, 103); k(KeyEvent.KEYCODE_DPAD_DOWN, 108); k(KeyEvent.KEYCODE_DPAD_LEFT, 105); k(KeyEvent.KEYCODE_DPAD_RIGHT, 106)
        k(KeyEvent.KEYCODE_MOVE_HOME, 102); k(KeyEvent.KEYCODE_MOVE_END, 107)
        k(KeyEvent.KEYCODE_PAGE_UP, 104); k(KeyEvent.KEYCODE_PAGE_DOWN, 109); k(KeyEvent.KEYCODE_INSERT, 110)
        for (i in 0 until 10) k(KeyEvent.KEYCODE_F1 + i, 59 + i)
        k(KeyEvent.KEYCODE_F11, 87); k(KeyEvent.KEYCODE_F12, 88)
    }

    fun x(androidKeyCode: Int): Int? = map[androidKeyCode]

    // X keycodes used by the extra-keys bar.
    const val ESC = 9; const val TAB = 23; const val CTRL = 37; const val ALT = 64; const val SHIFT = 50
    const val UP = 111; const val DOWN = 116; const val LEFT = 113; const val RIGHT = 114
    const val HOME = 110; const val END = 115; const val PGUP = 112; const val PGDN = 117; const val BACKSPACE = 22; const val ENTER = 36
}
