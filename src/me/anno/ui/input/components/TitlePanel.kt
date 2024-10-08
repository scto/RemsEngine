package me.anno.ui.input.components

import me.anno.language.translation.NameDesc
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.text.TextPanel

/**
 * TextPanel that redirects copy/paste operations to its controlling panel.
 * Copy is most important here.
 * */
class TitlePanel(nameDesc: NameDesc, var owner: Panel, style: Style) : TextPanel(nameDesc, style) {
    override fun onCopyRequested(x: Float, y: Float): Any? {
        return owner.onCopyRequested(x, y)
    }
}