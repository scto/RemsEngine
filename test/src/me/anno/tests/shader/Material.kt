package me.anno.tests.shader

import me.anno.Engine
import me.anno.ecs.components.mesh.material.Material
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.ECSRegistry
import me.anno.engine.OfficialExtensions
import me.anno.utils.OS
import org.apache.logging.log4j.LogManager

fun main() {
    OfficialExtensions.initForTests()
    ECSRegistry.init()
    val prefab = PrefabCache[OS.documents.getChild("cube bricks.glb")]!!
    val logger = LogManager.getLogger(Material::class)
    for (change in prefab.adds) {
        logger.info(change)
    }
    for (change in prefab.sets) {
        logger.info(change)
    }
    val instance = prefab.createInstance()
    logger.info(instance)
    Engine.requestShutdown()
}