package me.anno.tests.image

import me.anno.Engine
import me.anno.gpu.hidden.HiddenOpenGLContext
import me.anno.gpu.texture.TextureCache
import me.anno.utils.OS.desktop
import me.anno.utils.OS.pictures

fun main() {
    HiddenOpenGLContext.createOpenGL()
    TextureCache[pictures.getChild("fav128.png"), false]!!
        .write(desktop.getChild("fav128.png"))
    Engine.requestShutdown()
}