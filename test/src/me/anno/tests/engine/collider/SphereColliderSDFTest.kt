package me.anno.tests.engine.collider

import me.anno.ecs.components.collider.SphereCollider
import me.anno.engine.raycast.RayQuery
import me.anno.sdf.shapes.SDFSphere
import me.anno.utils.assertions.assertEquals
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SphereColliderSDFTest {

    @Test
    fun testSignedDistance() {
        val tested = SphereCollider()
        val base = SDFSphere()
        val random = Random(1234)
        for (i in 0 until 20) {
            val pos = Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat())
                .sub(0.5f).mul(5f)
            if (i == 0) pos.set(0f)
            val nor = Vector3f()
            val tmp = Vector3f(pos)
            val radius = random.nextFloat()
            tested.radius = radius.toDouble()
            base.scale = radius
            val distance = tested.getSignedDistance(tmp, nor)
            assertEquals(pos, tmp)
            assertEquals(Vector3f(pos).safeNormalize(), nor)
            assertEquals(base.computeSDF(Vector4f(pos, 0f), IntArrayList(0)), distance, 1e-5f)
            assertEquals(pos.length() - radius, distance)
        }
    }

    @Test
    fun testRaycast() {
        val tested = SphereCollider()
        val random = Random(1234)
        for (i in 0 until 100) {
            val x = (random.nextFloat() - 0.5f) * 5f
            val y = (random.nextFloat() - 0.5f) * 5f
            assertEquals(
                tested.raycastClosestHit(
                    RayQuery(
                        Vector3d(x, y, -2f),
                        Vector3d(0f, 0f, 1f),
                        1e3
                    )
                ), x * x + y * y < 1f
            )
        }
    }

    // todo test collider hits using sdf calculations
}