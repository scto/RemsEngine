package me.anno.tests.mesh.blender

import me.anno.engine.OfficialExtensions
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.utils.OS.downloads

fun main() {
    OfficialExtensions.initForTests()
    testSceneWithUI("Bone Indices Renderer", downloads.getChild("3d/Talking On Phone 2.fbx")) {
        it.renderView.renderMode = RenderMode.BONE_INDICES
    }
}