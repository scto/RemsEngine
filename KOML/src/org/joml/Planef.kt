package org.joml

open class Planef(
    @JvmField var dirX: Float,
    @JvmField var dirY: Float,
    @JvmField var dirZ: Float,
    @JvmField var distance: Float
) {

    constructor() : this(0f, 0f, 0f, 0f)

    constructor(pos: Vector3f, dir: Vector3f) :
            this(dir.x, dir.y, dir.z, -pos.dot(dir))

    fun set(x: Float, y: Float, z: Float, w: Float): Planef {
        dirX = x
        dirY = y
        dirZ = z
        distance = w
        return this
    }

    fun set(pos: Vector3f, dir: Vector3f) = set(dir.x, dir.y, dir.z, -pos.dot(dir))

    fun set(src: Planef): Planef = set(src.dirX, src.dirY, src.dirZ, src.distance)

    fun dot(x: Float, y: Float, z: Float): Float = x * dirX + y * dirY + z * dirZ + distance

    fun dot(v: Vector3f): Float = dot(v.x, v.y, v.z)

    override fun toString(): String {
        return "Plane($dirX, $dirY, $dirZ, $distance)"
    }
}