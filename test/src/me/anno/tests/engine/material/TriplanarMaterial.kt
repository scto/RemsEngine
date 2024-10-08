package me.anno.tests.engine.material

import me.anno.ecs.components.mesh.material.TriplanarMaterial
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.utils.OS.pictures

fun main() {
    val mat = TriplanarMaterial()
    mat.diffuseMap = pictures.getChild("BricksColor.png")
    mat.normalMap = pictures.getChild("BricksNormal.png")
    mat.sharpness = 1f
    mat.blendPreferY = 0.507f
    testSceneWithUI("Triplanar Material", mat)
}