package me.anno.mesh

import me.anno.utils.pooling.JomlPools
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector3d
import org.joml.Vector3f
import org.the3deers.util.EarCut

object Triangulation {

    @JvmStatic
    @Suppress("unused")
    fun ringToTriangles2f(points: FloatArray): FloatArray {
        val indices = EarCut.earcut(points, 2) ?: return FloatArray(0)
        val result = FloatArray(indices.size * 2)
        for (i in indices.indices) {
            val index = indices[i] * 2
            result[i * 2] = points[index]
            result[i * 2 + 1] = points[index + 1]
        }
        return result
    }

    @JvmStatic
    fun ringToTrianglesVec2f(points: List<Vector2f>): List<Vector2f> {
        val joint = FloatArray(points.size * 2)
        for (index in points.indices) {
            val v = points[index]
            joint[index * 2] = v.x
            joint[index * 2 + 1] = v.y
        }
        val indices = EarCut.earcut(joint, 2) ?: return emptyList()
        return indices.toList().map { index -> points[index] }
    }

    @JvmStatic
    @Suppress("unused")
    fun ringToTrianglesVec2d(points: List<Vector2d>): List<Vector2d> {
        val map = HashMap<Vector2f, Vector2d>()
        val points2f = points.map {
            val newVec = JomlPools.vec2f.create().set(it)
            map[newVec] = it
            newVec
        }
        val result = ringToTrianglesVec2f(points2f).map { map[it]!! }
        JomlPools.vec2f.sub(points.size)
        return result
    }

    @JvmStatic
    fun ringToTrianglesVec3f(points: List<Vector3f>): List<Vector3f> {
        val map = HashMap<Vector3d, Vector3f>()
        val points3d = points.map { vec3f ->
            val vec3d = JomlPools.vec3d.create().set(vec3f)
            map[vec3d] = vec3f
            vec3d
        }
        val result = ringToTrianglesVec3d(points3d).map { vec3d -> map[vec3d]!! }
        JomlPools.vec3d.sub(points.size)
        return result
    }

    @JvmStatic
    fun ringToTrianglesVec3d(points: List<Vector3d>): List<Vector3d> {
        if (points.size > 2) {
            val normal = JomlPools.vec3d.create().set(0.0)
            val tmp1 = JomlPools.vec3d.create()
            val tmp2 = JomlPools.vec3d.create()
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                val c = points[(i + 2) % points.size]
                tmp1.set(a).sub(b)
                tmp2.set(b).sub(c)
                normal.add(tmp1.cross(tmp2))
            }
            normal.normalize()
            if (normal.length() < 0.5) {
                JomlPools.vec3d.sub(3)
                return emptyList()
            }
            // find 2d coordinate system
            val xAxis = normal.findSecondAxis(JomlPools.vec3d.create())
            val yAxis = normal.cross(xAxis)
            val projected = points.map {
                JomlPools.vec2f.create()
                    .set(it.dot(xAxis), it.dot(yAxis))
            }
            val reverseMap = HashMap<Vector2f, Vector3d>()
            for (index in points.indices) {
                reverseMap[projected[index]] = points[index]
            }
            val triangles2d = ringToTrianglesVec2f(projected)
            val result = triangles2d.map { reverseMap[it]!! }
            JomlPools.vec2f.sub(projected.size)
            JomlPools.vec3d.sub(4)
            return result
        } else return emptyList()
    }

    @JvmStatic
    fun ringToTrianglesPoint(points: List<Point>): List<Point> {
        if (points.size > 2) {
            val normal = JomlPools.vec3f.create().set(0f)
            val tmp1 = JomlPools.vec3f.create()
            val tmp2 = JomlPools.vec3f.create()
            for (i in points.indices) {
                val a = points[i].position
                val b = points[(i + 1) % points.size].position
                val c = points[(i + 2) % points.size].position
                tmp1.set(a).sub(b)
                tmp2.set(b).sub(c)
                normal.add(tmp1.cross(tmp2))
            }
            normal.normalize()
            if (normal.length() < 0.5f) {
                JomlPools.vec3f.sub(3)
                return emptyList()
            }
            // find 2d coordinate system
            val xAxis = normal.findSecondAxis(JomlPools.vec3f.create())
            val yAxis = normal.cross(xAxis)
            val projected = points.map {
                JomlPools.vec2f.create()
                    .set(it.position.dot(xAxis), it.position.dot(yAxis))
            }
            val reverseMap = HashMap<Vector2f, Point>()
            for (index in points.indices) {
                reverseMap[projected[index]] = points[index]
            }
            val triangles2f = ringToTrianglesVec2f(projected)
            val result = triangles2f.map { reverseMap[it]!! }
            JomlPools.vec2f.sub(projected.size)
            JomlPools.vec3f.sub(4)
            return result
        } else return emptyList()
    }
}