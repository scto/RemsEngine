package me.anno.engine

import me.anno.config.DefaultConfig.style
import me.anno.engine.ui.DefaultLayout
import me.anno.gpu.GFX.windowStack
import me.anno.gpu.Window
import me.anno.input.ActionManager
import me.anno.language.translation.Dict
import me.anno.studio.StudioBase
import me.anno.studio.rems.StudioActions
import me.anno.ui.base.Visibility
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.debug.ConsoleOutputPanel
import me.anno.ui.editor.OptionBar
import org.apache.logging.log4j.LogManager

class RemsEngine : StudioBase(true, "Rem's Engine", "RemsEngine", 1) {

    override fun createUI() {

        ECSRegistry.init()

        Dict.loadDefault()

        // todo select project view, like Rem's Studio
        // todo select scene
        // todo show scene, and stuff, like Rem's Studio

        // todo different editing modes like Blender?, e.g. animating stuff, scripting, ...
        // todo and always be capable to change stuff

        // todo create our editor, where we can drag stuff into the scene, view it in 3D, move around, and such
        // todo play the scene

        // todo base shaders, which can be easily made touch-able

        // for testing directly jump in the editor
        val editScene = ECSWorld()
        val gameScene = ECSWorld()

        val style = style

        val list = PanelListY(style)
        val controls = OptionBar(style)

        val editUI = DefaultLayout.createDefaultMainUI(editScene, false, style)
        val gameUI = DefaultLayout.createDefaultMainUI(gameScene, true, style)
        gameUI.visibility = Visibility.GONE

        controls.addMajor("Play/Stop") {
            val goStart = editUI.visibility == Visibility.VISIBLE
            if (goStart) {
                // todo reset second scene

            } else {

            }
            editUI.visibility = Visibility[!goStart]
            gameUI.visibility = Visibility[goStart]
            list.invalidateLayout()
            list.invalidateDrawing()
        }
        list.add(controls)
        // todo different controls

        list += editUI
        list += gameUI

        list += ConsoleOutputPanel(style)
        windowStack.add(Window(list))

        StudioActions.register()
        ActionManager.init()

    }

    companion object {

        private val LOGGER = LogManager.getLogger(RemsEngine::class)

        @JvmStatic
        fun main(args: Array<String>) {
            RemsEngine().run()
        }

    }

}