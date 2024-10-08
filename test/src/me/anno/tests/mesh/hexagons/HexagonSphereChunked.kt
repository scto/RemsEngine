package me.anno.tests.mesh.hexagons

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.maths.chunks.spherical.HexagonSphere

fun main() {
    val n = 100
    val sphere = HexagonSphere(n, 1)
    val scene = Entity()
    for (i in 0 until 20) {
        val mesh = createFaceMesh(Mesh(), sphere.queryChunk(i, 0, 0))
        Entity(scene).add(MeshComponent(mesh))
    }
    testSceneWithUI("HexSphere Chunked", scene)
}
