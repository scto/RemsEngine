package org.joml

import kotlin.math.hypot

@Suppress("unused")
open class Vector2f {

    @JvmField
    var x = 0f

    @JvmField
    var y = 0f

    constructor()

    @JvmOverloads
    constructor(x: Float, y: Float = x) {
        this.x = x
        this.y = y
    }

    constructor(v: Vector2f) : this(v.x, v.y)
    constructor(v: Vector2i) : this(v.x.toFloat(), v.y.toFloat())
    constructor(xy: FloatArray) : this(xy[0], xy[1])

    @JvmOverloads
    fun set(x: Float, y: Float = x): Vector2f {
        this.x = x
        this.y = y
        return this
    }

    @JvmOverloads
    fun set(x: Double, y: Double = x) = set(x.toFloat(), y.toFloat())

    fun set(v: Vector2f) = set(v.x, v.y)
    fun set(v: Vector2i) = set(v.x.toFloat(), v.y.toFloat())
    fun set(v: Vector2d) = set(v.x.toFloat(), v.y.toFloat())
    fun set(xy: FloatArray) = set(xy[0], xy[1])

    operator fun get(component: Int): Float {
        return when (component) {
            0 -> x
            1 -> y
            else -> throw IllegalArgumentException()
        }
    }

    fun get(dst: Vector2f) = dst.set(x, y)
    fun get(dst: Vector2d): Vector2d = dst.set(x.toDouble(), y.toDouble())

    operator fun set(component: Int, value: Float) = setComponent(component, value)
    fun setComponent(component: Int, value: Float): Vector2f {
        when (component) {
            0 -> x = value
            1 -> y = value
            else -> throw IllegalArgumentException()
        }
        return this
    }

    fun perpendicular(): Vector2f {
        val tmp = y
        y = -x
        x = tmp
        return this
    }

    @JvmOverloads
    fun sub(v: Vector2f, dst: Vector2f = this) = sub(v.x, v.y, dst)

    @JvmOverloads
    fun sub(x: Float, y: Float, dst: Vector2f = this): Vector2f {
        dst.x = this.x - x
        dst.y = this.y - y
        return dst
    }

    fun dot(v: Vector2f) = x * v.x + y * v.y
    fun angle(v: Vector2f): Float {
        val dot = x * v.x + y * v.y
        val det = x * v.y - y * v.x
        return kotlin.math.atan2(det, dot)
    }

    fun lengthSquared() = x * x + y * y
    fun length() = hypot(x, y)
    fun distance(v: Vector2f) = hypot(x - v.x, y - v.y)
    fun distanceSquared(v: Vector2f) = distanceSquared(v.x, v.y)
    fun distance(x: Float, y: Float) = hypot(this.x - x, this.y - y)
    fun distanceSquared(x: Float, y: Float) = lengthSquared(this.x - x, this.y - y)

    @JvmOverloads
    fun normalize(dst: Vector2f = this) = mul(1f / length(), dst)

    @JvmOverloads
    fun normalize(length: Float, dst: Vector2f = this) = mul(length / length(), dst)

    @JvmOverloads
    fun add(v: Vector2f, dst: Vector2f = this) = add(v.x, v.y, dst)

    @JvmOverloads
    fun add(x: Float, y: Float, dst: Vector2f = this) = dst.set(this.x + x, this.y + y)

    fun zero() = set(0f, 0f)

    @JvmOverloads
    fun negate(dst: Vector2f = this) = dst.set(-x, -y)

    @JvmOverloads
    fun mul(scalar: Float, dst: Vector2f = this) = dst.set(x * scalar, y * scalar)

    @JvmOverloads
    fun mul(x: Float, y: Float, dst: Vector2f = this) = dst.set(this.x * x, this.y * y)

    @JvmOverloads
    fun mul(v: Vector2f, dst: Vector2f = this) =
        dst.set(x * v.x, y * v.y)

    @JvmOverloads
    fun div(v: Vector2f, dst: Vector2f = this) =
        dst.set(x / v.x, y / v.y)

    @JvmOverloads
    fun div(scalar: Float, dst: Vector2f = this) = dst.mul(1f / scalar)

    @JvmOverloads
    fun div(x: Float, y: Float, dst: Vector2f = this) =
        dst.set(this.x / x, this.y / y)

    @JvmOverloads
    fun mul(mat: Matrix2f, dst: Vector2f = this) =
        dst.set(mat.m00 * x + mat.m10 * y, mat.m01 * x + mat.m11 * y)

    @JvmOverloads
    fun mul(mat: Matrix2d, dst: Vector2f = this) =
        dst.set((mat.m00 * x + mat.m10 * y).toFloat(), (mat.m01 * x + mat.m11 * y).toFloat())

    @JvmOverloads
    fun mulTranspose(mat: Matrix2f, dst: Vector2f = this) =
        dst.set(mat.m00 * x + mat.m01 * y, mat.m10 * x + mat.m11 * y)

    @JvmOverloads
    fun mulPosition(mat: Matrix3x2f, dst: Vector2f = this) =
        dst.set(mat.m00 * x + mat.m10 * y + mat.m20, mat.m01 * x + mat.m11 * y + mat.m21)

    @JvmOverloads
    fun mulDirection(mat: Matrix3x2f, dst: Vector2f = this) =
        dst.set(mat.m00 * x + mat.m10 * y, mat.m01 * x + mat.m11 * y)

    @JvmOverloads
    fun lerp(other: Vector2f, t: Float, dst: Vector2f = this): Vector2f {
        dst.x = x + (other.x - x) * t
        dst.y = y + (other.y - y) * t
        return dst
    }

    override fun hashCode(): Int {
        return 31 * (x).toBits() + (y).toBits()
    }

    override fun equals(other: Any?): Boolean {
        return if (this === other) {
            true
        } else if (other == null) {
            false
        } else if (this.javaClass != other.javaClass) {
            false
        } else {
            other as Vector2f
            if ((x) != (other.x)) {
                false
            } else {
                (y) == (other.y)
            }
        }
    }

    fun equals(v: Vector2f, delta: Float): Boolean {
        return if (this === v) {
            true
        } else if (!Runtime.equals(x, v.x, delta)) {
            false
        } else {
            Runtime.equals(y, v.y, delta)
        }
    }

    fun equals(x: Float, y: Float): Boolean {
        return this.x == x && this.y == y
    }

    override fun toString() = "($x,$y)"

    @JvmOverloads
    fun fma(a: Vector2f, b: Vector2f, dst: Vector2f = this): Vector2f {
        dst.x = x + a.x * b.x
        dst.y = y + a.y * b.y
        return dst
    }

    @JvmOverloads
    fun fma(a: Float, b: Vector2f, dst: Vector2f = this): Vector2f {
        dst.x = x + a * b.x
        dst.y = y + a * b.y
        return dst
    }

    @JvmOverloads
    fun min(v: Vector2f, dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.min(x, v.x)
        dst.y = kotlin.math.min(y, v.y)
        return dst
    }

    @JvmOverloads
    fun max(v: Vector2f, dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.max(x, v.x)
        dst.y = kotlin.math.max(y, v.y)
        return dst
    }

    fun maxComponent(): Int {
        val absX = kotlin.math.abs(x)
        val absY = kotlin.math.abs(y)
        return if (absX >= absY) 0 else 1
    }

    fun minComponent(): Int {
        val absX = kotlin.math.abs(x)
        val absY = kotlin.math.abs(y)
        return if (absX < absY) 0 else 1
    }

    @JvmOverloads
    fun floor(dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.floor(x)
        dst.y = kotlin.math.floor(y)
        return dst
    }

    @JvmOverloads
    fun ceil(dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.ceil(x)
        dst.y = kotlin.math.ceil(y)
        return dst
    }

    @JvmOverloads
    fun round(dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.round(x)
        dst.y = kotlin.math.round(y)
        return dst
    }

    val isFinite: Boolean
        get() = x.isFinite() && y.isFinite()

    @JvmOverloads
    fun absolute(dst: Vector2f = this): Vector2f {
        dst.x = kotlin.math.abs(x)
        dst.y = kotlin.math.abs(y)
        return dst
    }

    fun dot2(x: Float, y: Float) = this.x * x + this.y * y

    fun cross(other: Vector2f): Float {
        return x * other.y - y * other.x
    }

    fun mulAdd(f: Float, b: Vector2f, dst: Vector2f): Vector2f {
        return dst.set(x * f + b.x, y * f + b.y)
    }

    companion object {

        fun lengthSquared(x: Float, y: Float) = x * x + y * y
        fun length(x: Float, y: Float) = hypot(x, y)

        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x1 - x2
            val dy = y1 - y2
            return hypot(dx, dy)
        }

        fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x1 - x2
            val dy = y1 - y2
            return dx * dx + dy * dy
        }
    }
}