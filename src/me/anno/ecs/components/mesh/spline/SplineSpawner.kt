package me.anno.ecs.components.mesh.spline

import me.anno.ecs.EntityQuery.getComponent
import me.anno.ecs.Transform
import me.anno.ecs.annotations.Docs
import me.anno.ecs.annotations.Range
import me.anno.ecs.components.mesh.IMesh
import me.anno.ecs.components.mesh.MeshCache
import me.anno.ecs.components.mesh.MeshSpawner
import me.anno.ecs.components.mesh.material.Material
import me.anno.ecs.components.mesh.material.MaterialCache
import me.anno.ecs.components.mesh.spline.Splines.generateSplinePoints
import me.anno.engine.debug.DebugPoint
import me.anno.engine.debug.DebugShapes
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.maths.Maths.TAU
import me.anno.maths.Maths.posMod
import me.anno.utils.structures.lists.WeightedList
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.roundToIntOr
import me.anno.utils.types.Floats.toIntOr
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round

class SplineSpawner : MeshSpawner() {

    var pointsPerRadian = 10.0

    var piecewiseLinear = false
    var isClosed = false

    var distance = 1.0

    // todo scale meshes to be toughing when length is e.g. 5.5 * distance
    var scaleIfNeeded = false

    @Docs("Use offsetX = 0 for length calculation, so they are spaced evenly on both sides")
    var useCenterLength = false

    var offsetX = 0.0
    var offsetY = 0.0

    var meshFile: FileReference = InvalidRef
    var materialOverride: FileReference = InvalidRef

    // decide which points to use as anchors for angle
    //  (useful for gates)
    @Range(-1.0, +1.0)
    var normalDt = 0.0

    var rotation = 0.0

    private fun getPoints(controlPoints: List<SplineControlPoint>, offsetX: Double): List<Vector3d> {
        return if (piecewiseLinear) {
            controlPoints.map { pt ->
                pt.getLocalPosition(Vector3d(), offsetX)
            }
        } else {
            val list = generateSplinePoints(controlPoints, pointsPerRadian, isClosed)
            val t = offsetX * 0.5 + 0.5
            (list.indices step 2).map { i ->
                list[i].mix(list[i + 1], t)
            }
        }
    }

    private fun getWeightedPoints(splinePoints: List<Vector3d>): WeightedList<Vector3d> {
        val weightedList = WeightedList<Vector3d>(splinePoints.size - 1)
        for (i in 1 until splinePoints.size) {
            weightedList.add(splinePoints[i - 1], splinePoints[i - 1].distance(splinePoints[i]))
        }
        weightedList.add(splinePoints.last(), 0.0)
        return weightedList
    }

    private fun getDt(length: Double): Double {
        var dt = normalDt
        if (abs(dt) < 0.01) dt = 0.01
        return dt / length
    }

    override fun forEachMesh(run: (IMesh, Material?, Transform) -> Unit) {

        val entity = entity ?: return
        val mesh = MeshCache[meshFile] ?: return
        val material = MaterialCache[materialOverride]
        if (distance <= 0.0) return

        val controlPoints = entity.children.mapNotNull {
            it.getComponent(SplineControlPoint::class)
        }
        val splinePoints = getPoints(controlPoints, offsetX)
        val lengthPoints = if (useCenterLength) {
            getPoints(controlPoints, 0.0)
        } else splinePoints
        val length = calculateLength(lengthPoints)
        val numPoints0 = length / distance
        val numPoints = round(numPoints0).toIntOr()
        val dt = getDt(length)
        val weightedList = getWeightedPoints(splinePoints)
        val scale = if (scaleIfNeeded) {
            if (useCenterLength) {
                calculateLength(splinePoints) / (distance * numPoints)
            } else {
                numPoints0 / numPoints
            }
        } else 1.0
        for (i in 0 until numPoints) {
            val transform = getTransform(i)

            // calculate position and rotation
            val t = (i + 0.5) / numPoints
            val p0 = interpolate(weightedList, t, transform.localPosition)
            val p1 = interpolate(weightedList, t + dt, Vector3d())
            transform.localRotation = transform.localRotation
                .identity().rotateY(atan2((p1.x - p0.x) * dt, (p1.z - p0.z) * dt) + rotation)
            p0.y += offsetY
            transform.localPosition = p0
            if (scaleIfNeeded) {
                val sc = transform.localScale.set(1.0)
                val scaleX = posMod((rotation * 4.0 / TAU).roundToIntOr(), 2) == 1
                sc[scaleX.toInt(0, 2)] = scale
                transform.localScale = sc
            }

            DebugShapes.debugPoints.add(DebugPoint(p0, -1, 0f))

            transform.smoothUpdate() // smooth or teleport?
            run(mesh, material, transform)
        }
    }

    private fun interpolate(list: WeightedList<Vector3d>, t: Double, dst: Vector3d): Vector3d {
        return list.getInterpolated(t) { a, b, ti -> a.mix(b, ti, dst) }!!
    }

    private fun calculateLength(list: List<Vector3d>): Double {
        return (1 until list.size).sumOf {
            list[it - 1].distance(list[it])
        }
    }
}