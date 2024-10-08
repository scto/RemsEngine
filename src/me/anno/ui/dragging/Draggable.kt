package me.anno.ui.dragging

import me.anno.gpu.GFX.loadTexturesSync
import me.anno.ui.Panel
import me.anno.ui.base.text.TextPanel
import me.anno.ui.Style
import me.anno.utils.structures.tuples.IntPair

/**
 * Standard implementation for IDraggable
 * */
open class Draggable(
    private val content: String,
    private val contentType: String,
    private val original: Any?,
    val ui: Panel
) : IDraggable {

    constructor(
        content: String, contentType: String, original: Any?,
        title: String, style: Style
    ) : this(content, contentType, original, TextPanel(title, style))

    @Suppress("unused")
    constructor(
        content: String, contentType: String, original: Any?,
        style: Style
    ) : this(content, contentType, original, content, style)

    init {
        loadTexturesSync.push(true)
        ui.calculateSize(300, 300)
        ui.setPosSize(0, 0, ui.minW, ui.minH)
        loadTexturesSync.pop()
    }

    override fun draw(x: Int, y: Int) {
        ui.setPosition(x, y)
        ui.draw(x, y, x + ui.width, y + ui.height)
    }

    override fun getSize(w: Int, h: Int): IntPair {
        return IntPair(ui.width, ui.height)
    }

    override fun getContent(): String = content
    override fun getContentType(): String = contentType
    override fun getOriginal(): Any? = original

}