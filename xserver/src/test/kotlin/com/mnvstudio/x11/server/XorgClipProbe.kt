package com.mnvstudio.x11.server

import com.mnvstudio.x11.xorg.XorgClient
import java.io.File

/** XorgClipProbe <port> [write-auth-file] [cookie-hex]: auth check, clipboard and cursor. */
fun main(args: Array<String>) {
    if (args.size >= 3 && args[1] == "write") {
        File(args[2]).writeBytes(XorgClient.xauthority(args[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray())); println("wrote"); return
    }
    val cookie = args.getOrNull(1)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
    val c = runCatching { XorgClient.connect("127.0.0.1", args[0].toInt(), cookie) }.getOrElse { println("REFUSED: ${it.message}"); return }
    println("CONNECTED ${c.width}x${c.height}")
    c.close()
}
