package me.anno.tests.engine

import me.anno.Time
import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.mesh.Shapes
import me.anno.utils.types.Floats.toRadians
import kotlin.math.max

fun main() {
    val scene = Entity()
    val child = Entity()
    child.add(MeshComponent(Shapes.flatCube.front.ref))
    child.add(object : Component() {
        override fun onUpdate(): Int {
            val transform = transform!!
            transform.localRotation = transform.localRotation.rotateY(120.0.toRadians())
            return max(1, Time.currentFPS.toInt())
        }
    })
    scene.add(child)
    testSceneWithUI("Transform Lerping", scene)
}