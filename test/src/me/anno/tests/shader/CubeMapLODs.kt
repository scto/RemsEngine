package me.anno.tests.shader

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Material
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.shaders.Skybox
import me.anno.engine.PluginRegistry
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.extensions.ExtensionLoader
import me.anno.gpu.CullMode
import me.anno.utils.OS.documents

fun main() {
    PluginRegistry.init()
    ExtensionLoader.load()
    // render a dynamic cube map,
    // calculate its LODs
    // display the result
    val scene = Entity()
    val mesh = MeshComponent(documents.getChild("MetallicSphere.glb"))
    mesh.materials = listOf(Material().apply {
        cullMode = CullMode.BOTH
        metallicMinMax.set(1f)
        roughnessMinMax.set(0.5f)
    }.ref)
    scene.add(mesh)
    scene.add(Skybox())
    testSceneWithUI("CubeMap LODs", scene)
}