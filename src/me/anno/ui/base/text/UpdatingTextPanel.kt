package me.anno.ui.base.text

import me.anno.Time
import me.anno.ui.Style
import kotlin.math.abs

class UpdatingTextPanel(updateMillis: Long, style: Style, val getValue: () -> String?) :
    TextPanel(getValue() ?: "", style) {

    val updateNanos = updateMillis * 1_000_000
    var lastUpdate = 0L

    override fun onUpdate() {
        val time = Time.nanoTime
        if (abs(lastUpdate - time) >= updateNanos) {
            val value = getValue()
            isVisible = value != null
            text = value ?: text
            lastUpdate = time
        }
        super.onUpdate()
    }

}