package me.anno.utils.types

import org.joml.*

object AnyToFloat {

    fun getFloat(any: Any, index: Int, defaultValue: Float = 0f): Float {
        return any[index, defaultValue]
    }

    operator fun Any.get(index: Int, defaultValue: Float = 0f): Float {
        return when (this) {
            is Int -> when (index) {
                0 -> this.toFloat()
                else -> defaultValue
            }
            is Long -> when (index) {
                0 -> this.toFloat()
                else -> defaultValue
            }
            is Float -> when (index) {
                0 -> this
                else -> defaultValue
            }
            is Double -> when (index) {
                0 -> this.toFloat()
                else -> defaultValue
            }
            is Vector2f -> when (index) {
                0 -> x
                1 -> y
                else -> defaultValue
            }
            is Vector3f -> when (index) {
                0 -> x
                1 -> y
                2 -> z
                else -> defaultValue
            }
            is Vector4f -> when (index) {
                0 -> x
                1 -> y
                2 -> z
                3 -> w
                else -> defaultValue
            }
            is Quaternionf -> when (index) {
                0 -> x
                1 -> y
                2 -> z
                3 -> w
                else -> defaultValue
            }
            is Quaterniond -> when (index) {
                0 -> x.toFloat()
                1 -> y.toFloat()
                2 -> z.toFloat()
                3 -> w.toFloat()
                else -> defaultValue
            }
            else -> defaultValue
        }
    }
}