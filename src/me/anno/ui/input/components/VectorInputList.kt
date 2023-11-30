package me.anno.ui.input.components

import me.anno.ui.Style
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.input.InputVisibility

class VectorInputList(val visibilityKey: String, style: Style) : PanelListX(style) {
    override var isVisible: Boolean
        get() = InputVisibility[visibilityKey]
        set(_) {}

    init {
        disableConstantSpaceForWeightedChildren = true
        alignmentX = AxisAlignment.FILL
    }
}