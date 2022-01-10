package me.anno.engine.ui.render

import me.anno.engine.ui.EditorState
import me.anno.engine.ui.control.ControlScheme
import me.anno.engine.ui.control.DraggingControls
import me.anno.ui.base.groups.PanelStack
import me.anno.ui.style.Style

class SceneView(val library: EditorState, style: Style) : PanelStack(style) {

    val renderer = RenderView(library, PlayMode.EDITING, style)

    var controls: ControlScheme = DraggingControls(renderer)

    init {
        add(renderer)
        add(controls)
        renderer.controlScheme = controls
    }

}