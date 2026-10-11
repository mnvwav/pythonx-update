package com.mnvstudio.x11.server

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the server without a display for testing with real X clients (DISPLAY=127.0.0.1:<display>).
 * Reads commands from stdin: `shot <name>`, `move <x> <y>`, `click <button>`, `type <text>`, `key <keysym hex>`.
 */
fun main(args: Array<String>) {
    val display = args.getOrNull(0)?.toInt() ?: 1
    val w = args.getOrNull(1)?.toInt() ?: 1024
    val h = args.getOrNull(2)?.toInt() ?: 768
    val out = File(args.getOrNull(3) ?: "shots").apply { mkdirs() }
    val server = XServer(w, h)
    server.listenTcp(display)
    server.onClipboard = { println("clipboard: $it") }
    println("listening on 127.0.0.1:$display")
    while (true) {
        val line = readLine() ?: break
        val parts = line.trim().split(" ", limit = 2)
        when (parts[0]) {
            "shot" -> {
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                server.render { fb, _ -> }
                synchronized(server) { img.setRGB(0, 0, w, h, server.framebuffer, 0, w) }
                ImageIO.write(img, "png", File(out, parts[1] + ".png"))
                println("shot ${parts[1]}")
            }
            "move" -> parts[1].split(" ").let { server.motion(it[0].toInt(), it[1].toInt()) }
            "click" -> server.click(parts[1].toInt())
            "type" -> parts[1].forEach { server.type(it.code) }
            "key" -> server.tapKeysym(parts[1].toInt(16))
            "clip" -> server.setClipboard(parts[1])
            "wm" -> synchronized(server) { server.setWindowManager(parts[1] == "on") }
            "press" -> server.button(parts[1].toInt(), true)
            "release" -> server.button(parts[1].toInt(), false)
            "desk" -> synchronized(server) { server.wm?.switchDesktop(parts[1].toInt()) }
            "windows" -> println(synchronized(server) { server.wm?.windows() })
            "keycode" -> parts[1].split(" ").let { server.key(it[0].toInt(), true); server.key(it[0].toInt(), false) }
            "clients" -> println("clients ${synchronized(server) { server.clients.size }}")
        }
    }
}
