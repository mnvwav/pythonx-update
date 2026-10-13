package com.mnvstudio.x11.server

import com.mnvstudio.x11.xorg.XorgClient

/** XorgDamageProbe <port>: idle frames should report nothing; typing should report small rectangles. */
fun main(args: Array<String>) {
    val c = XorgClient.connect("127.0.0.1", args[0].toInt())
    println("tracks damage: ${c.tracksDamage}")
    Thread.sleep(300); println("first: " + c.takeDirty()?.joinToString())
    Thread.sleep(500); println("idle: " + c.takeDirty()?.joinToString())
    c.motion(60, 30); c.click(1); "abc".forEach { c.type(it.code) }
    Thread.sleep(500); println("after typing: " + c.takeDirty()?.joinToString())
    Thread.sleep(500); println("idle again: " + c.takeDirty()?.joinToString())
}
