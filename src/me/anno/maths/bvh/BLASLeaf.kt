package me.anno.maths.bvh

import me.anno.engine.raycast.RayHit
import me.anno.utils.Tabs
import me.anno.utils.types.AABBs.volume
import me.anno.utils.types.Triangles
import org.joml.AABBf
import org.joml.Vector3f

class BLASLeaf(val start: Int, val length: Int, val pos: FloatArray, bounds: AABBf) : BLASNode(bounds) {

    override fun print(depth: Int) {
        println(Tabs.spaces(depth * 2) + " ${bounds.volume()}, $start += $length")
    }

    override fun countNodes() = 1
    override fun maxDepth() = 1
    override fun forEach(run: (BVHBuilder) -> Unit) = run(this)

    override fun intersect(pos: Vector3f, dir: Vector3f, invDir: Vector3f, dirIsNeg: Int, hit: RayHit) {
        if (intersectBounds(pos, invDir, dirIsNeg, hit.distance.toFloat())) {

            val end = start + length
            val vs = hit.tmpVector3fs
            val a = vs[0]
            val b = vs[1]
            val c = vs[2]
            val localHitTmp = vs[3]
            val localNormalTmp = vs[4]

            val localHit = vs[5]
            val localNormal = vs[6]

            var bestLocalDistance = hit.distance.toFloat()
            val bld0 = bestLocalDistance

            val p = this.pos

            var i3 = start * 9
            for (i in start until end) {

                a.set(p[i3++], p[i3++], p[i3++])
                b.set(p[i3++], p[i3++], p[i3++])
                c.set(p[i3++], p[i3++], p[i3++])

                val localDistance = Triangles.rayTriangleIntersection(
                    pos, dir, a, b, c,
                    bestLocalDistance, localNormalTmp, localHitTmp
                )
                if (localDistance < bestLocalDistance) {
                    bestLocalDistance = localDistance
                    // could swap pointers as well
                    localHit.set(localHitTmp)
                    localNormal.set(localNormalTmp)
                }

            }

            val bld = bestLocalDistance.toDouble()
            if (bld < bld0) {
                hit.distance = bld
                hit.normalWS.set(localNormal)
            }

        }
    }

    override fun findCompactPositions() = pos

}