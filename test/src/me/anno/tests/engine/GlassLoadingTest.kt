package me.anno.tests.engine

import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.io.files.FileReference.Companion.getReference
import org.apache.logging.log4j.LogManager

fun main() {
    LogManager.logAll()
    testSceneWithUI(
        "Glass?", getReference(
            "E:/Assets/Sources/POLYGON_Military_Source_Files.zip/SourceFiles/Fbx/SM_Bld_Village_House_02_Destroyed.fbx"
        )
    )
}