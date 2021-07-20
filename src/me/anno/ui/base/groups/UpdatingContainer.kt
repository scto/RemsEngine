package me.anno.ui.base.groups

import me.anno.gpu.GFX
import me.anno.ui.base.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.components.Padding
import me.anno.ui.style.Style
import kotlin.math.abs

class UpdatingContainer(updateMillis: Long, val getter: () -> Panel?, style: Style) :
    PanelContainer(getter() ?: Panel(style), Padding.Zero, style) {

    val updateNanos = updateMillis * 1_000_000
    var lastUpdate = 0L

    override fun tickUpdate() {
        val time = GFX.gameTime
        if (abs(lastUpdate - time) >= updateNanos) {
            val value = getter()
            visibility = Visibility[value != null]
            if (value != null) child = value
            lastUpdate = time
        }
        super.tickUpdate()
    }

}