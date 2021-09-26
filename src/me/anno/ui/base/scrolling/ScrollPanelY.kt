package me.anno.ui.base.scrolling

import me.anno.config.DefaultConfig
import me.anno.input.MouseButton
import me.anno.ui.base.Panel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.style.Style
import me.anno.utils.maths.Maths.clamp
import kotlin.math.max

open class ScrollPanelY(
    child: Panel, padding: Padding,
    style: Style,
    alignX: AxisAlignment
) : PanelContainer(child, padding, style), ScrollableY {

    constructor(child: Panel, style: Style) : this(child, Padding(), style, AxisAlignment.MIN)
    constructor(child: Panel, padding: Padding, style: Style) : this(child, padding, style, AxisAlignment.MIN)
    constructor(padding: Padding, align: AxisAlignment, style: Style) : this(PanelListY(style), padding, style, align)

    init {
        child += WrapAlign(alignX, AxisAlignment.MIN)
        weight = 0.0001f
    }

    var lsp = -1f
    var lmsp = -1
    override fun tickUpdate() {
        super.tickUpdate()
        if (scrollPositionY != lsp || maxScrollPositionY != lmsp) {
            lsp = scrollPositionY
            lmsp = maxScrollPositionY
            window!!.needsLayout += this
        }
    }

    override fun drawsOverlaysOverChildren(lx0: Int, ly0: Int, lx1: Int, ly1: Int): Boolean {
        return maxScrollPositionY > 0 && lx1 > this.lx1 - scrollbarWidth // overlaps on the right
    }

    override var scrollPositionY = 0f
    var isDownOnScrollbar = false

    override val maxScrollPositionY get() = max(0, child.minH + padding.height - h)
    val scrollbar = ScrollbarY(this, style)
    val scrollbarWidth = style.getSize("scrollbarWidth", 8)
    val scrollbarPadding = style.getSize("scrollbarPadding", 1)

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)

        child.calculateSize(w - padding.width, maxLength - padding.height)

        minW = child.minW + padding.width
        minH = child.minH + padding.height
        if (maxScrollPositionY > 0) minW += scrollbarWidth
    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)

        val scroll = scrollPositionY.toInt()
        child.placeInParent(x + padding.left, y + padding.top - scroll)

    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        clampScrollPosition()
        super.onDraw(x0, y0, x1, y1)
        if (maxScrollPositionY > 0f) {
            scrollbar.x = x1 - scrollbarWidth - scrollbarPadding
            scrollbar.y = y + scrollbarPadding
            scrollbar.w = scrollbarWidth
            scrollbar.h = h - 2 * scrollbarPadding
            drawChild(scrollbar, x0, y0, x1, y1)
        }
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        val delta = -dy * scrollSpeed
        if ((delta > 0f && scrollPositionY >= maxScrollPositionY) ||
            (delta < 0f && scrollPositionY <= 0f)
        ) {// if done scrolling go up the hierarchy one
            super.onMouseWheel(x, y, dx, dy, byMouse)
        } else {
            scrollPositionY += delta
            clampScrollPosition()
            // we consumed dy
            if (dx != 0f) {
                super.onMouseWheel(x, y, dx, 0f, byMouse)
            }
        }
    }

    fun clampScrollPosition() {
        scrollPositionY = clamp(scrollPositionY, 0f, maxScrollPositionY.toFloat())
    }

    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = scrollbar.contains(x, y, scrollbarPadding * 2)
        if (!isDownOnScrollbar) super.onMouseDown(x, y, button)
    }

    override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = false
        super.onMouseUp(x, y, button)
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDownOnScrollbar) {
            scrollbar.onMouseMoved(x, y, dx, dy)
            clampScrollPosition()
            invalidateLayout()
        } else super.onMouseMoved(x, y, dx, dy)
    }

    companion object {
        val scrollSpeed get() = DefaultConfig["ui.scroll.speed", 30f]
    }

}