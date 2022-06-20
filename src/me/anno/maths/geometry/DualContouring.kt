package me.anno.maths.geometry

import me.anno.ecs.components.mesh.sdf.SDFGroup
import me.anno.ecs.components.mesh.sdf.shapes.SDFBox
import me.anno.ecs.components.mesh.sdf.shapes.SDFSphere
import me.anno.fonts.signeddistfields.edges.LinearSegment
import me.anno.image.ImageWriter
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.fract
import me.anno.maths.Maths.length
import me.anno.maths.Maths.max
import me.anno.maths.Maths.unmix
import me.anno.maths.Optimization
import me.anno.maths.geometry.DualContouring.Func2d
import me.anno.maths.geometry.DualContouring.Grad2d
import me.anno.maths.geometry.MarchingSquares.findZero
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Booleans.toInt
import org.joml.Matrix2f
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.*

/**
 * theoretically nice, practically pretty bad :/
 * use MarchingSquares instead
 * */
object DualContouring {

    fun interface Func2d {
        fun calc(x: Float, y: Float): Float
    }

    fun interface Grad2d {
        fun calc(x: Float, y: Float, dst: Vector2f)
    }

    class QEF2d(val w: Float) {

        val m = Matrix2f()
        val v = Vector2f()
        val avg = Vector3f()

        fun reset() {
            m.zero()
            v.zero()
            avg.zero()
            // half weight for bias towards zero
            add(0.5f, 0.5f, +w, 0f)
            add(0.5f, 0.5f, 0f, +w)
        }

        fun add(px: Float, py: Float, g: Vector2f) {
            g.normalize()
            add(px, py, g.x, g.y)
        }

        /**
         * add ((x-px)*dx + (y-py)*dy)² as terms to the equation
         * */
        fun add(px: Float, py: Float, dx: Float, dy: Float, w: Float = length(dx, dy)) {

            avg.add(px * w, py * w, w)

            val dx2 = dx * dx
            val dy2 = dy * dy
            // x²
            m.m00 += 2f * dx2
            v.x += 2f * dx2 * px
            // y²
            m.m11 += 2f * dy2
            v.y += 2f * dy2 * py
            // 2xy
            val dxy = dx * dy * 2f
            m.m01 += dxy
            v.x += py * dxy
            v.y += px * dxy
            // constants don't matter
        }

        /**
         * find minimum point of equation within cell boundaries
         * */
        fun findExtremum(dst: Vector2f = Vector2f()): Vector2f {

            // solve linear system
            // minimize ax²+by²+cxy+dx+ey
            // -> solve df/dx = 0 and df/dy = 0
            // -> solve
            //      2ax + cy = -d
            //      2by + cx = -e

            m.m10 = m.m01 // required
            m.invert().transform(v, dst)

            // if out of bounds, use average... not
            // the best solution, but at least sth
            if (!(dst.x in 0f..1f && dst.y in 0f..1f)) {
                dst.set(avg.x, avg.y).div(avg.z)
            }

            return dst
        }
    }

    fun gradient(func: Func2d): Grad2d {
        return Grad2d { x, y, dst ->
            val e = 0.01f
            val vx = func.calc(x + e, y) - func.calc(x - e, y)
            val vy = func.calc(x, y + e) - func.calc(x, y - e)
            dst.set(vx, vy)
        }
    }

    fun findBestVertex2d(
        i0: Int, di: Int,
        values: FloatArray,
        fn: Func2d, gradient: Grad2d,
        x0: Float, x1: Float,
        y0: Float, y1: Float,
        g: Vector2f, qef: QEF2d,
        wi: Int, vertices: Array<Vector2f?>,
    ) {
        val v00 = values[i0]
        val v01 = values[i0 + di]
        val v10 = values[i0 + 1]
        val v11 = values[i0 + di + 1]
        val b00 = v00 > 0f
        val b01 = v01 > 0f
        val b10 = v10 > 0f
        val b11 = v11 > 0f
        val ctr = b00.toInt() + b01.toInt() + b10.toInt() + b11.toInt()
        if ((ctr and 3) != 0) {

            qef.reset()

            // add edge value for every edge that changes
            if (b00 != b01) {
                val dy = findZero(v00, v01)
                gradient.calc(x0, y0 + dy, g)
                qef.add(0f, dy, g)
            }
            if (b10 != b11) {
                val dy = findZero(v10, v11)
                gradient.calc(x1, y0 + dy, g)
                qef.add(1f, dy, g)
            }
            if (b00 != b10) {
                val dx = findZero(v00, v10)
                gradient.calc(x0 + dx, y0, g)
                qef.add(dx, 0f, g)
            }
            if (b01 != b11) {
                val dx = findZero(v01, v11)
                gradient.calc(x0 + dx, y1, g)
                qef.add(dx, 1f, g)
            }

            qef.findExtremum(g)

            val s = Optimization.simplexAlgorithm(
                floatArrayOf(g.x + x0, g.y + y0),
                0.25f, 0f, 32
            ) {
                Maths.sq(fn.calc(it[0], it[1])) +
                        10f * (0f +
                        max(0f, x0 - it[0]) +
                        max(0f, y0 - it[1]) +
                        max(0f, it[0] - x1) +
                        max(0f, it[1] - y1)
                        )
            }

            vertices[wi] = Vector2f(s)

        }
    }

    fun contour2d(
        sx: Int, sy: Int,
        func: Func2d,
        grad: Grad2d = gradient(func)
    ): List<Vector2f> {
        val pointsInGrid = (sx + 1) * (sy + 1)
        // calculate all positions and all gradients
        val values = FloatArray(pointsInGrid)
        // fill positions & gradients
        var vIndex = 0
        for (y in 0..sy) {
            val py = y.toFloat()
            for (x in 0..sx) {
                val px = x.toFloat()
                values[vIndex++] = func.calc(px, py)
            }
        }
        return contour2d(sx, sy, values, func, grad)
    }

    fun contour2d(
        sx: Int, sy: Int,
        values: FloatArray, func: Func2d, gradient: Grad2d
    ): List<Vector2f> {
        val vertices = arrayOfNulls<Vector2f>(sx * sy)
        var writeIndex = 0
        var vIndex = 0
        val di = sx + 1
        val qef = QEF2d(0.01f)
        val tmp = Vector2f()
        for (y in 0 until sy) {
            val y0 = y.toFloat()
            val y1 = y0 + 1f
            for (x in 0 until sx) {
                val x0 = x.toFloat()
                val x1 = x0 + 1f
                findBestVertex2d(
                    vIndex++, di, values, func, gradient,
                    x0, x1, y0, y1, tmp, qef,
                    writeIndex++, vertices
                )
            }
            vIndex++
        }
        val edges = ArrayList<Vector2f>()
        // dx edges
        val sx1 = sx + 1
        for (y in 0 until sy) {
            for (x in 1 until sx) {
                val vi = x + sx1 * y
                val vj = vi + sx1
                if ((values[vi] > 0f) != (values[vj] > 0f)) {
                    // find correct vertex indices
                    val vk = x + sx * y
                    edges += vertices[vk - 1]!!
                    edges += vertices[vk]!!
                }
            }
        }
        // dx edges
        for (y in 1 until sy) {
            for (x in 0 until sx) {
                val vi = x + sx1 * y
                val vj = vi + 1
                if ((values[vi] > 0f) != (values[vj] > 0f)) {
                    val vk = x + sx * y
                    edges += vertices[vk - sx]!!
                    edges += vertices[vk]!!
                }
            }
        }
        return edges
    }

    @JvmStatic
    fun main(args: Array<String>) {

        val sx = 32
        val sy = 32
        val s = 3.5f
        val comp = SDFGroup()
        val d = 0.43f
        comp.addChild(SDFSphere().apply {
            position.sub(d, d, 0f)
        })
        comp.addChild(SDFBox().apply {
            position.add(d, d, 0f)
        })
        comp.style = SDFGroup.Style.STAIRS
        comp.smoothness = 1f
        comp.type = SDFGroup.CombinationMode.TONGUE
        val values = Func2d { xi, yi ->
            val pos = JomlPools.vec4f.create()
            val x = (xi / sx - 0.5f) * s
            val y = (yi / sy - 0.5f) * s
            val value = comp.computeSDF(pos.set(x, y, 0f, 0f))
            JomlPools.vec4f.sub(1)
            value
        }
        val edges = contour2d(sx, sy, values)
        val scale = 24
        ImageWriter.writeImageFloat(
            sx * scale, sy * scale,
            "dualContouring.png", 32, false
        ) { x, y, _ ->
            val px = x.toFloat() / scale
            val py = y.toFloat() / scale
            var dsq = Float.POSITIVE_INFINITY
            for (i in edges.indices step 2) {
                val p0 = edges[i]
                val p1 = edges[i + 1]
                val v = LinearSegment.signedDistanceSq(px, py, p0.x, p0.y, p1.x, p1.y)
                dsq = min(dsq, v)
            }
            val dist1 = sqrt(dsq)
            val dist2 = clamp(unmix(0f, 0.1f, dist1))
            val grid = 0.1f * clamp(unmix(0.48f, 0.5f, max(abs(px - round(px)), abs(py - round(py)))))
            val value = values.calc(px, py) * 5f
            Maths.mix(1f, sign(value) * fract(value), dist2) + grid
        }
    }

}