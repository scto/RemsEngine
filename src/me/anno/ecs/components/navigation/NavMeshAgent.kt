package me.anno.ecs.components.navigation

import me.anno.ecs.Component
import me.anno.io.serialization.NotSerializedProperty
import org.joml.Vector3f
import org.recast4j.detour.DefaultQueryFilter
import org.recast4j.detour.FindRandomPointResult
import org.recast4j.detour.MeshData
import org.recast4j.detour.NavMeshQuery
import org.recast4j.detour.crowd.Crowd
import org.recast4j.detour.crowd.CrowdAgent
import org.recast4j.detour.crowd.CrowdAgentParams
import java.util.*

// todo if null, find first best nav mesh
// todo given the mesh, find a good path
// todo find path towards target
// todo functions for jumps and links and such...

open class NavMeshAgent(
    meshData: MeshData,
    navMesh: org.recast4j.detour.NavMesh,
    val query: NavMeshQuery,
    val filter: DefaultQueryFilter,
    val random: Random,
    navMesh1: NavMesh,
    crowd: Crowd,
    val mask: Int
) : Component() {

    @NotSerializedProperty
    var currRef: FindRandomPointResult

    val params = CrowdAgentParams()
    val crowdAgent: CrowdAgent

    val speed = 10f

    init {

        val header = meshData.header!!
        val tileRef = navMesh.getTileRefAt(header.x, header.y, header.layer)
        currRef = query.findRandomPointWithinCircle(tileRef, Vector3f(), 200f, filter, random).result!!

        params.radius = navMesh1.agentRadius
        params.height = navMesh1.agentHeight
        params.maxSpeed = speed
        params.maxAcceleration = 10f
        params.collisionQueryRange = navMesh1.agentRadius * 2f
        params.pathOptimizationRange = params.collisionQueryRange * 1.5f
        // other params?
        crowdAgent = crowd.addAgent(currRef.randomPt, params)

    }

    open fun findNextTarget() {
        val nextRef = query.findRandomPointWithinCircle(
            currRef.randomRef, crowdAgent.targetPos,
            200f, filter, random
        ).result
        if (nextRef != null) crowdAgent.setTarget(nextRef.randomRef, nextRef.randomPt)
        else lastWarning = "Cannot find random point within circle!"
    }

    override fun clone() = this
    override val className = "AgentController"
}