package me.anno.tests.image

import me.anno.utils.OS
import me.anno.video.ImageSequenceMeta

fun main() {
    val file = OS.documents.getChild("Blender\\Image Sequence\\%.jpg")
    val meta = ImageSequenceMeta(file)
    println(meta.toString())
    /*meta.matches.forEach { (file, _) ->
        val src = ImageIO.read(file)
        val dst = src.withoutAlpha()
        val out = File(file.parentFile, file.nameWithoutExtension + ".jpg")
        ImageIO.write(dst, "jpg", out)
    }*/
}