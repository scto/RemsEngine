package me.anno.tests.shader

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.light.sky.Skybox
import me.anno.engine.OfficialExtensions
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.extensions.ExtensionLoader
import me.anno.utils.OS.downloads

fun main() {
    OfficialExtensions.register()
    ExtensionLoader.load()
    val scene = Entity()
    scene.add(Skybox())
    scene.add(MeshComponent(downloads.getChild("3d/DamagedHelmet.glb")))
    testSceneWithUI("MSAADeferred", scene) {
        it.renderer.renderMode = RenderMode.MSAA_DEFERRED
    }
}