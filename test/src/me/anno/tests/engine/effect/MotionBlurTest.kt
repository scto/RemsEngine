package me.anno.tests.engine.effect

import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Material
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.mesh.shapes.CylinderModel
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.io.files.Reference.getReference
import kotlin.math.PI

fun main() {
    val scene = Entity()
    val box = Entity(scene)
    box.add(object : Component() {
        override fun onUpdate(): Int {
            Thread.sleep(50) // simulate low fps, so the result is better visible
            box.rotation = box.rotation.rotateY(PI / 6) // rotate quickly
            return 1
        }
    })
    val cyl = CylinderModel.createMesh(50, 2, top = true, bottom = true, null, 3f, Mesh())
    box.add(MeshComponent(cyl).apply {
        materials = listOf(Material().apply {
            diffuseMap = getReference("res://icon.png")
            linearFiltering = false
        }.ref)
    })
    testSceneWithUI("MotionBlur", scene) {
        it.renderer.renderMode = RenderMode.MOTION_BLUR
    }
}