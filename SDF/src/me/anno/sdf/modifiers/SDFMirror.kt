package me.anno.sdf.modifiers

import me.anno.ecs.components.mesh.material.utils.TypeValue
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.maths.Maths.sq
import me.anno.sdf.SDFComponent.Companion.appendVec
import me.anno.sdf.SDFComponent.Companion.defineUniform
import me.anno.sdf.SDFComponent.Companion.globalDynamic
import me.anno.sdf.VariableCounter
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.AABBf
import org.joml.Planef
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.sqrt

class SDFMirror() : PositionMapper() {

    // todo edit planes using gizmos
    // todo also mark vectors as potential positions

    constructor(position: Vector3f, normal: Vector3f) : this() {
        plane.set(normal.x, normal.y, normal.z, -normal.dot(position))
        plane.normalize3()
    }

    // proper smoothness would require two sdf evaluations
    // considering this effect probably would be stacked, it would get too expensive
    // (+ our pipeline currently does not support that)

    var plane = Planef(0f, 1f, 0f, 0f)
        set(value) {
            if (dynamicPlane || globalDynamic) invalidateBounds()
            else invalidateShader()
            field.set(value)
            field.normalize3()
        }

    var dynamicPlane = false
        set(value) {
            if (field != value) {
                field = value
                if (!globalDynamic) invalidateShader()
            }
        }

    // idk how performance behaves, try it yourself ^^
    var useBranch = false
        set(value) {
            if (field != value) {
                field = value
                invalidateShader()
            }
        }

    override fun applyTransform(bounds: AABBf) {
        // first: intersect bounds with plane
        // then: mirror remaining points onto other side
        // effectively, we just test all border points:
        // if they are on the active side, they will get added on both sides,
        // if they are on the inactive side, just discard them
        val imx = bounds.minX
        val imy = bounds.minY
        val imz = bounds.minZ
        val ixx = bounds.maxX
        val ixy = bounds.maxY
        val ixz = bounds.maxZ
        bounds.clear()
        val normal = plane
        for (i in 0 until 8) {
            val x = if (i.and(1) == 0) imx else ixx
            val y = if (i.and(2) == 0) imy else ixy
            val z = if (i.and(4) == 0) imz else ixz
            val dot = 2f * (normal.dot(x, y, z))
            if (dot >= 0f) {
                bounds.union(x, y, z)
                bounds.union(x - dot * normal.dirX, y - dot * normal.dirY, z - dot * normal.dirZ)
            }
        }
    }

    override fun buildShader(
        builder: StringBuilder,
        posIndex: Int,
        nextVariableId: VariableCounter,
        uniforms: HashMap<String, TypeValue>,
        functions: HashSet<String>,
        seeds: ArrayList<String>
    ): String? {
        // reflect(I,N): I - 2.0 * dot(N, I) * N
        val tmpIndex = nextVariableId.next()
        val dynamicPlane = dynamicPlane || globalDynamic
        val normal = if (dynamicPlane) defineUniform(uniforms, plane) else {
            val name = "nor${nextVariableId.next()}"
            builder.append("vec4 ").append(name)
            builder.append("=")
            builder.appendVec(plane)
            builder.append(";\n")
            name
        }
        builder.append("float tmp").append(tmpIndex).append("=dot(vec4(")
        builder.append("pos").append(posIndex)
        builder.append(",1.0),").append(normal)
        builder.append(");\n")
        if (useBranch) {
            builder.append("if(tmp").append(tmpIndex).append("<0.0) pos").append(posIndex)
            builder.append("-=2.0*tmp").append(tmpIndex).append("*").append(normal).append(".xyz;\n")
        } else {
            builder.append("pos").append(posIndex)
            builder.append("-=((tmp").append(tmpIndex).append(" < 0.0 ? 2.0 : 0.0)*tmp")
            builder.append(tmpIndex).append(")*").append(normal).append(".xyz;\n")
        }
        return null
    }

    override fun calcTransform(pos: Vector4f, seeds: IntArrayList) {
        val normal = plane
        val dot = 2f * normal.dot(pos.x, pos.y, pos.z)
        if (dot < 0f) pos.sub(dot * normal.dirX, dot * normal.dirY, dot * normal.dirZ, 0f)
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        if (dst !is SDFMirror) return
        dst.plane = plane
        dst.dynamicPlane = dynamicPlane
        dst.useBranch = useBranch
    }

    companion object {
        fun Planef.normalize3(): Planef {
            val sq = sq(dirX, dirY, dirZ)
            if (sq > 0f) {
                val factor = 1f / sqrt(sq)
                dirX *= factor
                dirY *= factor
                dirZ *= factor
                distance *= factor
            } else {
                dirX = 0f
                dirY = 1f
                dirZ = 0f
            }
            if (distance.isNaN()) distance = 0f
            return this
        }
    }
}