package me.anno.maths.bvh

import me.anno.engine.raycast.RayHit
import me.anno.utils.types.Strings
import me.anno.utils.types.Triangles
import me.anno.utils.types.Triangles.halfSubCrossDot
import me.anno.utils.types.Triangles.rayTriangleIntersectionFront
import org.joml.AABBf
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

class BLASLeaf(
    val start: Int, val length: Int,
    val geometry: GeometryData,
    bounds: AABBf
) : BLASNode(bounds) {

    override fun countNodes() = 1
    override fun maxDepth() = 1
    override fun forEach(run: (BLASNode) -> Unit) = run(this)

    override fun findClosestHit(pos: Vector3f, dir: Vector3f, invDir: Vector3f, dirIsNeg: Int, hit: RayHit): Boolean {
        hit.blasCtr++
        return if (bounds.isRayIntersecting(pos, invDir, hit.distance.toFloat())) {
            hit.trisCtr += length

            val vs = hit.tmpVector3fs
            // 0-3 are used by Raycast
            val a = vs[4]
            val b = vs[5]
            val c = vs[6]
            val localHitTmp = vs[7]
            val localNormalTmp = vs[8]

            val localHit = vs[9]
            val localNormal = vs[10]
            val localNormal2 = vs[11]
            val barycentrics = vs[12]

            var bestLocalDistance = hit.distance.toFloat()
            val bld0 = bestLocalDistance

            val positions = geometry.positions
            val indices = geometry.indices
            val normals = geometry.normals

            var i3 = start * 3
            val j3 = i3 + length * 3
            var triangleIndexX3 = -1
            while (i3 < j3) {

                val ai = indices[i3] * 3
                val bi = indices[i3 + 1] * 3
                val ci = indices[i3 + 2] * 3
                a.set(positions, ai)
                b.set(positions, bi)
                c.set(positions, ci)
                i3 += 3

                val localDistance = rayTriangleIntersectionFront(
                    pos, dir, a, b, c, bestLocalDistance,
                    localNormalTmp, localHitTmp, barycentrics
                )

                if (localDistance < bestLocalDistance) {
                    bestLocalDistance = localDistance
                    // could swap pointers as well
                    localHit.set(localHitTmp)
                    localNormal.set(localNormalTmp)
                    // barycentric is only set, if an improvement is found;
                    // find smooth normals using barycentrics
                    barycentrics.div(barycentrics.x + barycentrics.y + barycentrics.z)
                    localNormal2.set(
                        barycentrics.x * normals[ai] + barycentrics.y * normals[bi] + barycentrics.z * normals[ci],
                        barycentrics.x * normals[ai + 1] + barycentrics.y * normals[bi + 1] + barycentrics.z * normals[ci + 1],
                        barycentrics.x * normals[ai + 2] + barycentrics.y * normals[bi + 2] + barycentrics.z * normals[ci + 2],
                    )
                    triangleIndexX3 = i3
                }
            }

            val bld = bestLocalDistance.toDouble()
            if (bld < bld0) {
                hit.distance = bld
                hit.geometryNormalWS.set(localNormal)
                hit.shadingNormalWS.set(localNormal2)
                hit.barycentric.set(barycentrics)
                hit.triangleIndex = triangleIndexX3 / 3
                true
            } else false
        } else false
    }

    override fun findAnyHit(pos: Vector3f, dir: Vector3f, invDir: Vector3f, dirIsNeg: Int, hit: RayHit): Boolean {
        hit.blasCtr++
        if (bounds.isRayIntersecting(pos, invDir, hit.distance.toFloat())) {
            hit.trisCtr += length

            val vs = hit.tmpVector3fs
            // 0-3 are used by Raycast
            val a = vs[4]
            val b = vs[5]
            val c = vs[6]
            val localHitTmp = vs[7]
            val localNormalTmp = vs[8]

            val localHit = vs[9]
            val localNormal = vs[10]
            val localNormal2 = vs[11]
            val barycentrics = vs[12]

            val bestLocalDistance = hit.distance.toFloat()

            val positions = geometry.positions
            val indices = geometry.indices
            val normals = geometry.normals

            var i3 = start * 3
            val j3 = i3 + length * 3
            while (i3 < j3) {

                val ai = indices[i3] * 3
                val bi = indices[i3 + 1] * 3
                val ci = indices[i3 + 2] * 3
                a.set(positions, ai)
                b.set(positions, bi)
                c.set(positions, ci)
                i3 += 3

                val localDistance = rayTriangleIntersectionFront(
                    pos, dir, a, b, c, bestLocalDistance,
                    localNormalTmp, localHitTmp, barycentrics
                )

                if (localDistance < bestLocalDistance) {
                    // could swap pointers as well
                    localHit.set(localHitTmp)
                    localNormal.set(localNormalTmp)
                    // barycentric is only set, if an improvement is found;
                    // find smooth normals using barycentrics
                    barycentrics.div(barycentrics.x + barycentrics.y + barycentrics.z)
                    localNormal2.set(
                        barycentrics.x * normals[ai] + barycentrics.y * normals[bi] + barycentrics.z * normals[ci],
                        barycentrics.x * normals[ai + 1] + barycentrics.y * normals[bi + 1] + barycentrics.z * normals[ci + 1],
                        barycentrics.x * normals[ai + 2] + barycentrics.y * normals[bi + 2] + barycentrics.z * normals[ci + 2],
                    )
                    hit.distance = localDistance.toDouble()
                    hit.geometryNormalWS.set(localNormal)
                    hit.shadingNormalWS.set(localNormal2)
                    hit.barycentric.set(barycentrics)
                    hit.triangleIndex = i3 / 3
                    return true
                }
            }
        }
        return false
    }

    override fun findClosestHit(group: RayGroup) {
        group.blasCtr++
        if (group.intersects(bounds)) {
            group.trisCtr += length

            val vs = group.tmpVector3fs
            val a = vs[0]
            val b = vs[1]
            val c = vs[2]

            val localHit = vs[3]
            val localNormal = vs[4]

            val ab = vs[5]
            val bc = vs[6]
            val ca = vs[7]

            val positions = geometry.positions
            val indices = geometry.indices
            val normals = geometry.normals

            val pos = group.pos
            val maxDistance = group.maxDistance

            var i3 = start * 3
            val j3 = i3 + length * 3
            while (i3 < j3) {

                val ai = indices[i3] * 3
                val bi = indices[i3 + 1] * 3
                val ci = indices[i3 + 2] * 3
                a.set(positions, ai)
                b.set(positions, bi)
                c.set(positions, ci)
                i3 += 3

                // inlined, optimized calculation with dx and dy

                val triN = Triangles.subCross(a, b, c, localNormal)
                val dd = triN.dot(a) - triN.dot(pos)
                if (dd >= 0f) continue

                val ndd0 = triN.dot(group.dir)
                val nddX = triN.dot(group.dx)
                val nddY = triN.dot(group.dy)
                val nddXY = nddX + nddY - ndd0

                val minNdd = min(min(ndd0, nddXY), min(nddX, nddY))
                val maxNdd = max(max(ndd0, nddXY), max(nddX, nddY))

                // uvs (barycentric coordinates) are perspective, and cannot be simply linearly interpolated
                // detect all critical scenarios (where the perspective effect is too strong)
                // both are negative; higher factor = faster, but worse quality
                if (maxNdd * group.tolerance < minNdd) {

                    val dist0 = dd / ndd0
                    var distX = dd / nddX
                    var distY = dd / nddY

                    // slight perspective error will occur, but it will be relatively fine
                    if (
                        max(dist0, max(distX, distY)) >= 0f &&
                        min(dist0, min(distX, distY)) < maxDistance
                    ) {

                        ab.set(b).sub(a)
                        bc.set(c).sub(b)
                        ca.set(a).sub(c)

                        group.dir.mulAdd(dist0, pos, localHit)

                        val d00 = halfSubCrossDot(ab, a, localHit, triN)
                        val d10 = halfSubCrossDot(bc, b, localHit, triN)
                        val d20 = halfSubCrossDot(ca, c, localHit, triN)

                        group.dx.mulAdd(distX, pos, localHit)

                        val d0x = halfSubCrossDot(ab, a, localHit, triN) - d00
                        val d1x = halfSubCrossDot(bc, b, localHit, triN) - d10
                        val d2x = halfSubCrossDot(ca, c, localHit, triN) - d20

                        group.dy.mulAdd(distY, pos, localHit)

                        val d0y = halfSubCrossDot(ab, a, localHit, triN) - d00
                        val d1y = halfSubCrossDot(bc, b, localHit, triN) - d10
                        val d2y = halfSubCrossDot(ca, c, localHit, triN) - d20

                        // whole group can be skipped, if all rays are out of bounds
                        if (d00 + max(0f, d0x) + max(0f, d0y) > 0f &&
                            d10 + max(0f, d1x) + max(0f, d1y) > 0f &&
                            d20 + max(0f, d2x) + max(0f, d2y) > 0f
                        ) {

                            // change into gradient mode
                            distX -= dist0
                            distY -= dist0

                            val dxs = group.dxs
                            val dys = group.dys
                            for (j in 0 until group.size) {

                                val dxi = dxs[j]
                                val dyi = dys[j]

                                val w = d00 + dxi * d0x + dyi * d0y // ab
                                val u = d10 + dxi * d1x + dyi * d1y // bc
                                val v = d20 + dxi * d2x + dyi * d2y // ca

                                val bestLocalDistance = group.depths[j]
                                val dist = dist0 + dxi * distX + dyi * distY
                                val distance = if (min(w, min(u, v)) >= 0f) dist else bestLocalDistance
                                if (distance < bestLocalDistance) {
                                    group.depths[j] = distance
                                    group.normalGX[j] = localNormal.x
                                    group.normalGY[j] = localNormal.y
                                    group.normalGZ[j] = localNormal.z
                                    group.normalSX[j] = normals[ai] * u + normals[bi] * v + normals[ci] * w
                                    group.normalSY[j] = normals[ai + 1] * u + normals[bi + 1] * v + normals[ci + 1] * w
                                    group.normalSZ[j] = normals[ai + 2] * u + normals[bi + 2] * v + normals[ci + 2] * w
                                }
                            }
                        }
                    }
                } else {

                    val dxs = group.dxs
                    val dys = group.dys

                    ab.set(b).sub(a)
                    bc.set(c).sub(b)
                    ca.set(a).sub(c)

                    val dir = vs[8]
                    for (j in 0 until group.size) {

                        dir.set(group.dir)
                        group.dxm.mulAdd(dxs[j], dir, dir)
                        group.dym.mulAdd(dys[j], dir, dir)
                        dir.normalize()

                        val ndd = triN.dot(dir)
                        val dist = dd / ndd

                        if (ndd < 0f && dist >= 0f && dist < maxDistance) {

                            dir.mulAdd(dist, pos, localHit)

                            val w = halfSubCrossDot(ab, a, localHit, triN)
                            val u = halfSubCrossDot(bc, b, localHit, triN)
                            val v = halfSubCrossDot(ca, c, localHit, triN)

                            val bestLocalDistance = group.depths[j]
                            val distance = if (min(w, min(u, v)) >= 0f) dist else bestLocalDistance
                            if (distance < bestLocalDistance) {
                                group.depths[j] = distance
                                group.normalGX[j] = localNormal.x
                                group.normalGY[j] = localNormal.y
                                group.normalGZ[j] = localNormal.z
                                group.normalSX[j] = normals[ai] * u + normals[bi] * v + normals[ci] * w
                                group.normalSY[j] = normals[ai + 1] * u + normals[bi + 1] * v + normals[ci + 1] * w
                                group.normalSZ[j] = normals[ai + 2] * u + normals[bi + 2] * v + normals[ci + 2] * w
                            }
                        }
                    }
                }
            }
        }
    }

    override fun findGeometryData() = geometry
}