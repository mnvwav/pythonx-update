package com.mnvstudio.x11

/**
 * The `mnvx11` command for Termux, like `termux-x11 :0`: starts this app's X server on a display and runs a program
 * on it, or prints the export line so `eval "$(mnvx11)"` sets DISPLAY in the current shell.
 *
 *   mnvx11                 start :0 and print "export DISPLAY=:0"
 *   mnvx11 :1 xterm        start :1 and run xterm on it
 *   mnvx11 -o xfce4-session   also open the display on screen
 */
object TermuxCommand {
    const val ACTION_START = "com.mnvstudio.x11.START"
    const val EXTRA_DISPLAY = "display"
    const val EXTRA_OPEN = "open"

    private val SCRIPT = """
        #!/data/data/com.termux/files/usr/bin/sh
        # mnvx11 [-o] [:N] [program args...] - start X11 and run a program on it
        OPEN=false
        [ "${'$'}1" = "-o" ] && { OPEN=true; shift; }
        D=:0
        case "${'$'}1" in :[0-9]*) D=${'$'}1; shift;; esac
        am start -n com.mnvstudio.x11/.MainActivity -a $ACTION_START --ei $EXTRA_DISPLAY "${'$'}{D#:}" --ez $EXTRA_OPEN ${'$'}OPEN >/dev/null 2>&1
        sleep 1
        export DISPLAY=${'$'}D
        if [ ${'$'}# -gt 0 ]; then exec "${'$'}@"; else echo "export DISPLAY=${'$'}D"; fi
    """.trimIndent()

    /** One paste into Termux installs the command into ${'$'}PREFIX/bin. */
    val install: String = "cat > \$PREFIX/bin/mnvx11 <<'EOF'\n$SCRIPT\nEOF\nchmod +x \$PREFIX/bin/mnvx11"
}
