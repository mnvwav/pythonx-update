package com.mnvstudio.x11.server

/** The atom table, starting with the 68 predefined atoms of the core protocol. */
class Atoms {
    private val names = ArrayList<String>()
    private val ids = HashMap<String, Int>()

    init {
        PREDEFINED.split(' ').forEach { intern(it) }
    }

    fun intern(name: String): Int = ids.getOrPut(name) { names += name; names.size }
    fun find(name: String): Int = ids[name] ?: 0
    fun name(atom: Int): String? = names.getOrNull(atom - 1)
    fun valid(atom: Int) = atom in 1..names.size

    companion object {
        const val PRIMARY = 1; const val ATOM = 4; const val CARDINAL = 6; const val FONT = 18; const val INTEGER = 19
        const val STRING = 31; const val WINDOW = 33; const val WM_NAME = 39

        private const val PREDEFINED = "PRIMARY SECONDARY ARC ATOM BITMAP CARDINAL COLORMAP CURSOR CUT_BUFFER0 CUT_BUFFER1 " +
            "CUT_BUFFER2 CUT_BUFFER3 CUT_BUFFER4 CUT_BUFFER5 CUT_BUFFER6 CUT_BUFFER7 DRAWABLE FONT INTEGER PIXMAP POINT " +
            "RECTANGLE RESOURCE_MANAGER RGB_COLOR_MAP RGB_BEST_MAP RGB_BLUE_MAP RGB_DEFAULT_MAP RGB_GRAY_MAP RGB_GREEN_MAP " +
            "RGB_RED_MAP STRING VISUALID WINDOW WM_COMMAND WM_HINTS WM_CLIENT_MACHINE WM_ICON_NAME WM_ICON_SIZE WM_NAME " +
            "WM_NORMAL_HINTS WM_SIZE_HINTS WM_ZOOM_HINTS MIN_SPACE NORM_SPACE MAX_SPACE END_SPACE SUPERSCRIPT_X SUPERSCRIPT_Y " +
            "SUBSCRIPT_X SUBSCRIPT_Y UNDERLINE_POSITION UNDERLINE_THICKNESS STRIKEOUT_ASCENT STRIKEOUT_DESCENT ITALIC_ANGLE " +
            "X_HEIGHT QUAD_WIDTH WEIGHT POINT_SIZE RESOLUTION COPYRIGHT NOTICE FONT_NAME FAMILY_NAME FULL_NAME CAP_HEIGHT " +
            "WM_CLASS WM_TRANSIENT_FOR"
    }
}
