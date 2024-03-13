package me.anno.tests.mesh

import me.anno.Engine
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.engine.OfficialExtensions
import me.anno.engine.raycast.RayQuery
import me.anno.extensions.ExtensionLoader
import me.anno.utils.Clock
import me.anno.utils.OS.downloads
import org.joml.Vector3d

fun main() {
    // first click onto dragon is slow... why???
    // -> RTAS building takes a long time
    val clock = Clock()
    OfficialExtensions.register()
    ExtensionLoader.load()
    clock.stop("Loading Extensions")
    val comp = MeshComponent(downloads.getChild("3d/dragon.obj"))
    comp.getMesh()
    clock.stop("Loading Mesh")
    val query = RayQuery(
        Vector3d(-100.0, 0.0, 0.0),
        Vector3d(1.0, 0.0, 0.0),
        200.0
    )
    comp.raycastClosestHit(query)
    clock.stop("Raycast")
    Engine.requestShutdown()
}