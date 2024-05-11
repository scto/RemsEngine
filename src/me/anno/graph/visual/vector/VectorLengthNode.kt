package me.anno.graph.visual.vector

import me.anno.graph.visual.scalar.MathNodeData
import me.anno.graph.visual.scalar.TypedMathNode
import me.anno.utils.structures.maps.LazyMap
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4d
import org.joml.Vector4f
import kotlin.math.abs

val vectorTypes = "Vector2f,Vector3f,Vector4f,Vector2d,Vector3d,Vector4d".split(',')

enum class LengthMode(val id: Int, val glsl1: String, val glsl2: String) {
    LENGTH(0, "length(a)", "length(a-b)"),
    LENGTH_SQUARED(1, "dot(a,a)", "dot(a-b,a-b)"),
    NORM1(2, "dot(abs(a),vec4(1.0))", "dot(abs(a-b),vec4(1.0))")
}

val vectorLengthData = LazyMap { type: String ->
    MathNodeData(
        LengthMode.entries,
        listOf(type), if (type.endsWith('f')) "Float" else "Double",
        LengthMode::id, { enumType ->
            if (enumType != LengthMode.NORM1) enumType.glsl1
            else enumType.glsl1.replace('4', type[6])
        },
        listOf("$type Length", "$type Length Squared", "$type Norm1 Length")
    )
}

@Suppress("unused")
class VectorLengthNode : TypedMathNode<LengthMode>(vectorLengthData, vectorTypes) {
    override fun compute() {
        val a = getInput(0)
        val v = when (enumType) {
            LengthMode.LENGTH -> when (a) {
                is Vector2f -> a.length()
                is Vector3f -> a.length()
                is Vector4f -> a.length()
                is Vector2d -> a.length()
                is Vector3d -> a.length()
                is Vector4d -> a.length()
                else -> throw NotImplementedError()
            }
            LengthMode.LENGTH_SQUARED -> when (a) {
                is Vector2f -> a.lengthSquared()
                is Vector3f -> a.lengthSquared()
                is Vector4f -> a.lengthSquared()
                is Vector2d -> a.lengthSquared()
                is Vector3d -> a.lengthSquared()
                is Vector4d -> a.lengthSquared()
                else -> throw NotImplementedError()
            }
            LengthMode.NORM1 -> when (a) {
                is Vector2f -> abs(a.x) + abs(a.y)
                is Vector3f -> abs(a.x) + abs(a.y) + abs(a.z)
                is Vector4f -> abs(a.x) + abs(a.y) + abs(a.z) + abs(a.w)
                is Vector2d -> abs(a.x) + abs(a.y)
                is Vector3d -> abs(a.x) + abs(a.y) + abs(a.z)
                is Vector4d -> abs(a.x) + abs(a.y) + abs(a.z) + abs(a.w)
                else -> throw NotImplementedError()
            }
        }
        setOutput(0, v)
    }
}

val vectorDistanceData = LazyMap { type: String ->
    MathNodeData(
        LengthMode.entries,
        listOf(type, type), "Float",
        LengthMode::id, { enumType ->
            if (enumType != LengthMode.NORM1) enumType.glsl2
            else enumType.glsl2.replace('4', type[6])
        },
        listOf("$type Distance", "$type Distance Squared", "$type Norm1 Distance")
    )
}

@Suppress("unused")
class VectorDistanceNode : TypedMathNode<LengthMode>(vectorDistanceData, vectorTypes) {
    override fun compute() {
        val a = getInput(0)
        val b = getInput(1)
        val v = when (enumType) {
            LengthMode.LENGTH -> when (a) {
                is Vector2f -> a.distance(b as Vector2f)
                is Vector3f -> a.distance(b as Vector3f)
                is Vector4f -> a.distance(b as Vector4f)
                is Vector2d -> a.distance(b as Vector2d)
                is Vector3d -> a.distance(b as Vector3d)
                is Vector4d -> a.distance(b as Vector4d)
                else -> throw NotImplementedError()
            }
            LengthMode.LENGTH_SQUARED -> when (a) {
                is Vector2f -> a.distanceSquared(b as Vector2f)
                is Vector3f -> a.distanceSquared(b as Vector3f)
                is Vector4f -> a.distanceSquared(b as Vector4f)
                is Vector2d -> a.distanceSquared(b as Vector2d)
                is Vector3d -> a.distanceSquared(b as Vector3d)
                is Vector4d -> a.distanceSquared(b as Vector4d)
                else -> throw NotImplementedError()
            }
            LengthMode.NORM1 -> when (a) {
                is Vector2f -> {
                    b as Vector2f
                    abs(a.x - b.x) + abs(a.y - b.y)
                }
                is Vector3f -> {
                    b as Vector3f
                    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
                }
                is Vector4f -> {
                    b as Vector4f
                    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z) + abs(a.w - b.w)
                }
                is Vector2d -> {
                    b as Vector2d
                    abs(a.x - b.x) + abs(a.y - b.y)
                }
                is Vector3d -> {
                    b as Vector3d
                    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
                }
                is Vector4d -> {
                    b as Vector4d
                    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z) + abs(a.w - b.w)
                }
                else -> throw NotImplementedError()
            }
        }
        setOutput(0, v)
    }
}
