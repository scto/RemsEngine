package me.anno.engine.ui.control

import me.anno.config.ConfigRef
import me.anno.studio.Inspectable

open class ControlSettings: Inspectable {
    var changeYByWASD by ConfigRef("ui.moveSettings.changeYByWASD",false)
    var enableShiftSpaceControls by ConfigRef("ui.moveSettings.enableShiftSpace", false)
}