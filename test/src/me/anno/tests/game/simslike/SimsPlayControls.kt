package me.anno.tests.game.simslike

import me.anno.ecs.EntityQuery.getComponents
import me.anno.engine.raycast.RayHit
import me.anno.engine.raycast.RayQuery
import me.anno.engine.raycast.Raycast
import me.anno.engine.ui.render.RenderView
import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.mix
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.utils.types.Floats.toRadians
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tan

class SimsPlayControls(controls: SimsControls, rv: RenderView) :
    SimsControlBase(controls, rv) {

    var currentSim = household.sims.first()

    val actionStack = PanelListY(style)

    init {

        // add sim-selection bar
        val simsSelectBar = PanelListX(style)
        simsSelectBar.alignmentX = AxisAlignment.CENTER
        simsSelectBar.alignmentY = AxisAlignment.MAX
        for (sim in household.sims) {
            simsSelectBar.add(
                TextButton(sim.name, style)
                    .addLeftClickListener {
                        currentSim = sim
                        rebuildActionStack()
                    }
            )
        }
        add(simsSelectBar)

        actionStack.add(TextPanel("Actions", style))
        actionStack.alignmentX = AxisAlignment.MIN
        actionStack.alignmentY = AxisAlignment.MAX
        add(actionStack)

        add(TextButton("Build", style)
            .addLeftClickListener {
                // switch to build mode
                sceneView.editControls = controls.buildControls
                controls.buildControls.rotationTarget.set(rotationTarget)
            }
            .apply {
                alignmentX = AxisAlignment.MAX
                alignmentY = AxisAlignment.MAX
            })
    }

    fun rebuildActionStack() {
        // todo add action stack
    }

    fun onClickedActions(actions: List<SimAction>, hit: RayHit) {
        // to do group them?
        Menu.openMenu(windowStack, actions.map {
            MenuOption(NameDesc(it.name, it.description, "")) {
                // todo add action to current character
                // todo character will be active, so add action to UI, too
            }
        })
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (Input.isLeftDown) {
            // move around by dragging
            val tanSpeed = 2.0 * tan(renderView.fovYRadians * 0.5)
            val xSpeed = -tanSpeed * renderView.radius / height
            val ry = rotationTarget.y.toRadians()
            val ySpeed = xSpeed / mix(1.0, abs(sin(ry)), 0.5)
            moveCamera(dx * xSpeed, 0.0, dy * ySpeed)
        } else super.onMouseMoved(x, y, dx, dy)
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        // resolve click
        // todo when clicked Sim, select it
        val query = RayQuery(renderView.cameraPosition, renderView.mouseDirection, 1e9)
        if (Raycast.raycastClosestHit(scene, query)) {
            val comp = query.result.component
            var entity = comp?.entity
            while (entity != null) {
                val actions = entity.getComponents(SimAction::class)
                if (actions.isNotEmpty()) {
                    onClickedActions(actions, query.result)
                    return
                }
                entity = entity.parentEntity
            }
        }// else hit the sky... idk about that
    }
}