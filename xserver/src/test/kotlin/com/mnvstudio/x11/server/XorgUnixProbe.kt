package com.mnvstudio.x11.server

import com.mnvstudio.x11.xorg.XorgClient
import com.mnvstudio.x11.xorg.XwdFrame
import java.io.File
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

/** XorgUnixProbe <socket> <cookie-hex> <fbdir>: connect like the phone does (Unix socket + cookie), type, read the frame. */
fun main(args: Array<String>) {
    val ch = SocketChannel.open(UnixDomainSocketAddress.of(args[0]))
    val cookie = args[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val c = XorgClient.connect(Channels.newInputStream(ch), Channels.newOutputStream(ch), ch, cookie)
    println("UNIX+COOKIE OK ${c.width}x${c.height}")
    c.motion(100, 60); c.click(1); "echo works\n".forEach { c.type(it.code) }
    Thread.sleep(700)
    val f = XwdFrame.open(File(args[2]))!!; val px = IntArray(f.width * f.height); f.copyTo(px)
    println("frame ok, non-black pixels: " + px.count { (it and 0xFFFFFF) != 0 })
}
