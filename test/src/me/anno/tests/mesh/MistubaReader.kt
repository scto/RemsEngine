package me.anno.tests.mesh

import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.ui.render.SceneView
import me.anno.ui.debug.TestStudio
import me.anno.utils.OS

fun main() {
    TestStudio.testUI {
        val main = OS.downloads.getChild("gradientdomain-scenes.zip/gradientdomain-scenes")
        val name = "veach-lamp"
        val sceneMain = main.getChild("$name/$name-gpt.xml/Scene.json")
        SceneView.testScene(PrefabCache.loadScenePrefab(sceneMain).createInstance())
    }
}