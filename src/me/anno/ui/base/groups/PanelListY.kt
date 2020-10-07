package me.anno.ui.base.groups

import me.anno.gpu.GFX
import me.anno.ui.base.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.style.Style
import kotlin.math.max
import kotlin.math.roundToInt

open class PanelListY(sorter: Comparator<Panel>?, style: Style) : PanelList(sorter, style) {
    constructor(style: Style) : this(null, style)

    var sumConst = 0
    var sumWeight = 0f

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        if (spacing > 0) {
            for (i in 1 until children.size) {
                val prev = children[i - 1]
                val i0 = prev.y + prev.h
                val i1 = children[i].y
                if (i1 > i0) GFX.drawRect(x, i0, w, i1 - i0, spaceColor)
            }
        }
    }

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        var maxX = x
        var constantSum = 0
        var variableSum = 0f
        for (child in children.filter { it.visibility != Visibility.GONE }) {
            child.calculateSize(w, h)
            constantSum += child.minH
            maxX = max(maxX, child.x + child.minW)
            variableSum += max(0f, child.weight)
        }
        val spaceCount = children.size - 1
        constantSum += spacing * spaceCount
        sumConst = constantSum
        sumWeight = variableSum
        minH = sumConst
        minW = maxX - x
    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)

        var perWeight = 0f
        val perConst = 1f

        if (h > sumConst) {
            val extraAvailable = max(0, h - sumConst)
            perWeight = extraAvailable / max(sumWeight, 1e-9f)
        }

        var yCtr = y
        for (child in children.filter { it.visibility != Visibility.GONE }) {
            val childH = (perConst * child.minH + perWeight * max(0f, child.weight)).roundToInt()
            child.calculateSize(w, childH)
            child.placeInParent(x, yCtr)
            child.applyPlacement(w, childH)
            yCtr += childH + spacing
        }

    }

}