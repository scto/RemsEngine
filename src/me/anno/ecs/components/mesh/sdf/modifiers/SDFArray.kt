package me.anno.ecs.components.mesh.sdf.modifiers

import me.anno.ecs.components.mesh.TypeValue
import me.anno.ecs.components.mesh.sdf.SDFComponent.Companion.defineUniform
import me.anno.ecs.components.mesh.sdf.VariableCounter
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.maths.Maths.clamp
import org.joml.AABBf
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import kotlin.math.floor
import kotlin.math.round

// todo triangle grid array as well
class SDFArray : PositionMapper() {

    // we could beautify the result when the shapes are overlapping by repeatedly calling the child...
    // would be pretty expensive...

    /**
     * repetition count
     * */
    var count = Vector3i(3)
        set(value) {
            if ((!dynamicX && value.x > 0) || (!dynamicY && value.y > 0) || (!dynamicZ && value.z > 0)) {
                invalidateShader()
            } else invalidateBounds()
            field.set(value)
        }

    /**
     * how large a cell needs to be;
     * should never be zero
     * */
    var cellSize = Vector3f(1f)
        set(value) {
            if ((!dynamicX && value.x > 0) || (!dynamicY && value.y > 0) || (!dynamicZ && value.z > 0)) {
                invalidateShader()
            } else invalidateBounds()
            field.set(value)
        }

    var dynamicX = false
        set(value) {
            if (field != value) invalidateShader()
            field = value
        }

    var dynamicY = false
        set(value) {
            if (field != value) invalidateShader()
            field = value
        }

    var dynamicZ = false
        set(value) {
            if (field != value) invalidateShader()
            field = value
        }

    override fun buildShader(
        builder: StringBuilder,
        posIndex: Int,
        nextVariableId: VariableCounter,
        uniforms: HashMap<String, TypeValue>,
        functions: HashSet<String>
    ): String? {
        functions.add(sdArray)
        val rep = cellSize
        val lim = count
        if (dynamicX || dynamicY || dynamicZ) {
            val repUniform = defineUniform(uniforms, rep)
            val limUniform = defineUniform(uniforms, lim)
            if (dynamicX) repeat(builder, posIndex, repUniform, limUniform, 'x')
            else if (rep.x > 0f) repeat(builder, posIndex, rep.x, lim.x, 'x')
            if (dynamicY) repeat(builder, posIndex, repUniform, limUniform, 'y')
            else if (rep.y > 0f) repeat(builder, posIndex, rep.y, lim.y, 'y')
            if (dynamicZ) repeat(builder, posIndex, repUniform, limUniform, 'z')
            else if (rep.z > 0f) repeat(builder, posIndex, rep.z, lim.z, 'z')
        } else {
            if (rep.x > 0f) repeat(builder, posIndex, rep.x, lim.x, 'x')
            if (rep.y > 0f) repeat(builder, posIndex, rep.y, lim.y, 'y')
            if (rep.z > 0f) repeat(builder, posIndex, rep.z, lim.z, 'z')
        }
        return null
    }

    private fun mod2(p: Float, s: Float, l: Float, h: Float): Float {
        return p - s * clamp(floor(p / s + h), -l, +l)
    }

    private fun mod2(p: Float, s: Float, c: Int): Float {
        if (c == 1 || s <= 0f) return p
        if (c <= 0) return p - s * round(p / s)
        return mod2(p, s, (c - 1) * 0.5f, c.and(1) * 0.5f)
    }

    override fun calcTransform(pos: Vector4f) {
        val rep = cellSize
        val lim = count
        if (rep.x > 0f) pos.x = mod2(pos.x, rep.x, lim.x)
        if (rep.y > 0f) pos.y = mod2(pos.y, rep.y, lim.y)
        if (rep.z > 0f) pos.z = mod2(pos.z, rep.z, lim.z)
    }

    override fun applyTransform(bounds: AABBf) {
        val rep = cellSize
        val lim = count
        if (rep.x > 0f) {
            bounds.minX = minMod2(bounds.minX, rep.x, lim.x)
            bounds.maxX = maxMod2(bounds.maxX, rep.x, lim.x)
        }
        if (rep.y > 0f) {
            bounds.minY = minMod2(bounds.minY, rep.y, lim.y)
            bounds.maxY = maxMod2(bounds.maxY, rep.y, lim.y)
        }
        if (rep.z > 0f) {
            bounds.minZ = minMod2(bounds.minZ, rep.z, lim.z)
            bounds.maxZ = maxMod2(bounds.maxZ, rep.z, lim.z)
        }
    }

    fun minMod2(x: Float, s: Float, c: Int): Float {
        if (c == 1 && s <= 0f) return x
        if (c <= 0) return Float.NEGATIVE_INFINITY
        // -1, because 1 is included by default
        // *0.5f, because it's half only (min or max)
        return x - s * (c - 1f) * 0.5f
    }

    fun maxMod2(x: Float, s: Float, c: Int): Float {
        return -minMod2(-x, s, c)
    }

    fun repeat(
        builder: StringBuilder,
        posIndex: Int,
        size: Float,
        count: Int,
        component: Char,
    ) {
        when {
            count == 1 || size <= 0f -> {} // done
            count <= 0 -> {
                builder.append("pos").append(posIndex).append(".").append(component)
                builder.append("=")
                builder.append("mod2(pos").append(posIndex).append(".").append(component)
                builder.append(",")
                builder.append(size)
                builder.append(");\n")
            }
            else -> {
                builder.append("pos").append(posIndex).append(".").append(component)
                builder.append("=")
                builder.append("mod2(pos").append(posIndex).append(".").append(component)
                builder.append(",")
                builder.append(size)
                builder.append(",")// why -1?
                builder.append((count - 1) * 0.5f)
                builder.append(",${count.and(1) * 0.5f});\n")
            }
        }
    }

    fun repeat(
        builder: StringBuilder,
        posIndex: Int,
        size: String,
        count: String,
        component: Char
    ) {
        builder.append("pos").append(posIndex).append(".").append(component)
        builder.append("=")
        builder.append("mod2(pos").append(posIndex).append(".").append(component)
        builder.append(",")
        builder.append(size).append(".").append(component)
        builder.append(",")
        builder.append(count).append(".").append(component)
        builder.append(");\n")
    }

    override fun clone(): SDFArray {
        val clone = SDFArray()
        copy(clone)
        return clone
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as SDFArray
        clone.dynamicX = dynamicX
        clone.dynamicY = dynamicY
        clone.dynamicZ = dynamicZ
        clone.count = count
        clone.cellSize = cellSize
    }

    override val className: String = "SDFArray"

    companion object {
        const val sdArray = "" +
                "float mod2(float p, float s){\n" +
                "   return p-s*round(p/s);\n" +
                "}\n" +
                "float mod2(float p, float s, float l, float h){\n" +
                "   return p-s*clamp(floor(p/s+h)+.5-h,-l,l);\n" +
                "}\n" +
                "float mod2(float p, float s, int c){\n" +
                "   if(c == 1 || s <= 0.0) return p;\n" +
                "   if(c <= 0) return p-s*round(p/s);\n" + // unlimited
                "   return mod2(p,s,float(c-1)*0.5,c&1?0.5:0.0);\n" +
                "}\n"
    }

}