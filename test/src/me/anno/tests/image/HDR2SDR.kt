package me.anno.tests.image

import me.anno.image.ImageCache
import me.anno.io.files.Reference.getReference

fun main() {
    val src = getReference("C:/XAMPP/htdocs/DigitalCampus/images/environment/kloofendal_38d_partly_cloudy_2k.hdr")
    val dst = src.getSibling("${src.nameWithoutExtension}.jpg")
    ImageCache[src, false]!!.write(dst)
}