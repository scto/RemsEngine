package me.anno.sdf.shapes

import me.anno.ecs.components.mesh.material.utils.TypeValue
import me.anno.sdf.VariableCounter
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.gpu.shader.GLSLType
import me.anno.maths.Maths.length
import me.anno.maths.Maths.min
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.max

open class SDFCylinder : SDFSmoothShape() {

    private val params = Vector2f(1f)

    var radius
        get() = params.x
        set(value) {
            if (params.x != value) {
                if (dynamicSize || globalDynamic) invalidateBounds()
                else invalidateShader()
                params.x = value
            }
        }

    var halfHeight
        get() = params.y
        set(value) {
            if (params.y != value) {
                if (dynamicSize || globalDynamic) invalidateBounds()
                else invalidateShader()
                params.y = value
            }
        }

    override fun calculateBaseBounds(dst: AABBf) {
        val h = halfHeight
        val r = radius
        dst.setMin(-r, -h, -r)
        dst.setMax(+r, +h, +r)
    }

    override fun buildShader(
        builder: StringBuilder,
        posIndex0: Int,
        nextVariableId: VariableCounter,
        dstIndex: Int,
        uniforms: HashMap<String, TypeValue>,
        functions: HashSet<String>,
        seeds: ArrayList<String>
    ) {
        val trans = buildTransform(builder, posIndex0, nextVariableId, uniforms, functions, seeds)
        functions.add(sdCylinder)
        smartMinBegin(builder, dstIndex)
        builder.append("sdCylinder(pos").append(trans.posIndex).append(',')
        val dynamicSize = dynamicSize || globalDynamic
        if (dynamicSize) builder.appendUniform(uniforms, params)
        else builder.appendVec(params)
        appendSmoothnessParameter(builder, uniforms)
        builder.append(')')
        smartMinEnd(builder, dstIndex, nextVariableId, uniforms, functions, seeds, trans)
    }

    override fun computeSDFBase(pos: Vector4f, seeds: IntArrayList): Float {
        val h = params
        val k = smoothness
        val hkx = h.x - k
        val hky = h.y - k
        val dx = abs(length(pos.x, pos.z)) - hkx
        val dy = abs(pos.y) - hky
        return min(max(dx, dy), 0f) + length(max(dx, 0f), max(dy, 0f)) - k + pos.w
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        if (dst !is SDFCylinder) return
        dst.params.set(params)
    }

    companion object {
        // from https://www.shadertoy.com/view/Xds3zN, Inigo Quilez
        const val sdCylinder = "" +
                "float sdCylinder(vec3 p, vec2 h){\n" +
                "   vec2 d = vec2(length(p.xz),abs(p.y)) - h;\n" +
                "   return min(max(d.x,d.y),0.0) + length(max(d,0.0));\n" +
                "}\n" +
                "float sdCylinder(vec3 p, vec2 h, float k){\n" +
                "   return sdCylinder(p,h-k)-k;\n" +
                "}\n"
    }

}