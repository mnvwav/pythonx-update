package com.mnvstudio.x11.server

import com.mnvstudio.x11.xorg.XorgClient
import com.mnvstudio.x11.xorg.XwdFrame
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Manual check against a real Xvfb: XorgProbe <port> <fbdir> <out.png> [text] */
fun main(args: Array<String>) {
    val c = XorgClient.connect("127.0.0.1", args[0].toInt())
    println("connected: root=${c.root} ${c.width}x${c.height}")
    c.motion(200, 150); c.click(1)
    args.getOrNull(3)?.forEach { c.type(it.code) }
    Thread.sleep(800)
    val f = XwdFrame.open(File(args[1]))!!
    val px = IntArray(f.width * f.height); f.copyTo(px)
    val img = BufferedImage(f.width, f.height, BufferedImage.TYPE_INT_RGB); img.setRGB(0, 0, f.width, f.height, px, 0, f.width)
    ImageIO.write(img, "png", File(args[2])); println("frame ${f.width}x${f.height} saved")
}
