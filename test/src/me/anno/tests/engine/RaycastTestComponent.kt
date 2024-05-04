package me.anno.tests.engine

import me.anno.ecs.Component
import me.anno.ecs.annotations.Docs
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.debug.DebugLine
import me.anno.engine.debug.DebugShapes
import me.anno.engine.raycast.RayQuery
import me.anno.engine.raycast.Raycast
import me.anno.engine.serialization.SerializedProperty
import org.joml.Vector3d

class RaycastTestComponent : Component() {

    @Docs("Only colliders with matching flags will be tested")
    @SerializedProperty
    var colliderMask = -1

    @Docs("Which kinds of colliders will be tested; flags; 1 = triangles, 2 = colliders, 4 = sdfs, see Raycast.kt")
    @SerializedProperty
    var typeMask = -1

    @SerializedProperty
    var maxDistance = 1e3

    @Docs("Thickness of the ray-cone at the start of the ray")
    @SerializedProperty
    var radiusAtOrigin = 0.0

    @Docs("Thickness delta with every unit along the ray")
    @SerializedProperty
    var radiusPerUnit = 0.0

    override fun onUpdate(): Int {
        // throw ray cast, and draw the result
        val entity = entity!!
        val transform = entity.transform.globalTransform
        val start = transform.transformPosition(Vector3d())
        val direction = transform.transformDirection(Vector3d(0.0, 0.0, 1.0)).normalize()

        val query = RayQuery(
            start, direction, maxDistance, radiusAtOrigin, radiusPerUnit,
            typeMask, colliderMask, false, emptySet(),
        )
        if (Raycast.raycastClosestHit(entity, query)) {
            DebugShapes.debugLines.add(DebugLine(start, query.result.positionWS, -1))
        } else {
            DebugShapes.debugLines.add(DebugLine(start, Vector3d(direction).add(start), 0xff0000))
        }
        return 1
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as RaycastTestComponent
        dst.colliderMask = colliderMask
        dst.maxDistance = maxDistance
    }
}