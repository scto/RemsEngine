package me.anno.tests.mesh

import me.anno.ecs.components.mesh.Material
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.shapes.CylinderModel
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.ui.UIColors.axisXColor
import me.anno.ui.UIColors.axisZColor
import me.anno.utils.Color.white

fun main() {
    val mesh = CylinderModel.createMesh(
        16, 3, true, true,
        listOf(
            Material.diffuse(white).ref,
            Material.diffuse(axisXColor).ref,
            Material.diffuse(axisZColor).ref
        ),
        3f, Mesh()
    )
    testSceneWithUI("Cylinder", mesh)
}