package me.anno.tests

import me.anno.io.files.WebRef

fun main() {
    println(WebRef("http://api.phychi.com/elemental/?n=60&v=1&sid=0").readTextSync())
}
