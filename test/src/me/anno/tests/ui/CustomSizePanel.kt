package me.anno.tests.ui

import me.anno.config.DefaultConfig.style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.custom.CustomSizeContainer
import me.anno.ui.debug.TestStudio.Companion.testUI

fun main() {
    val wrapper = PanelListY(style)
    val resizeable = TextButton("Test", style)
    val container = CustomSizeContainer(true, true, resizeable, style).apply {
        alignmentX = AxisAlignment.MIN
        alignmentY = AxisAlignment.MIN
    }
    wrapper.add(container)
    testUI("Tracking", container)
}