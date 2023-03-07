package me.anno.mesh.assimp

import me.anno.ecs.prefab.PrefabSaveable
import me.anno.io.base.BaseWriter
import org.joml.Matrix4f
import org.joml.Matrix4x3f
import org.joml.Vector3f

class Bone(var id: Int, var parentId: Int, name: String) : PrefabSaveable() {

    constructor() : this(-1, -1, "")

    init {
        this.name = name
    }

    // parent is unknown, maybe be indirect...
    // var parent: Bone? = null

    /**
     * transformation relative to parent
     * */
    val relativeTransform = Matrix4x3f()

    /**
     * bone space to mesh space in bind pose
     * */
    val originalTransform = Matrix4x3f()

    /**
     * offsetMatrix; inverse of bone position + rotation in mesh space
     * */
    val inverseBindPose = Matrix4x3f()

    /**
     * inverseBindPose.m30, inverseBindPose.m31, inverseBindPose.m32
     * */
    val offsetVector = Vector3f()

    /**
     * = inverseBindPose.invert()
     *
     * bone position + rotation in mesh space
     * */
    val bindPose = Matrix4x3f()

    /**
     * bindPose.m30, bindPose.m31, bindPose.m32
     * */
    val bindPosition = Vector3f()

    fun setBindPose(m: Matrix4f) {
        bindPose.set(m)
        bindPosition.set(m.m30, m.m31, m.m32)
        calculateInverseBindPose()
    }

    fun setBindPose(m: Matrix4x3f) {
        bindPose.set(m)
        bindPosition.set(m.m30, m.m31, m.m32)
        calculateInverseBindPose()
    }

    @Suppress("unused")
    fun setInverseBindPose(m: Matrix4f) {
        inverseBindPose.set(m)
        offsetVector.set(m.m30, m.m31, m.m32)
        calculateBindPose()
    }

    fun setInverseBindPose(m: Matrix4x3f) {
        inverseBindPose.set(m)
        offsetVector.set(m.m30, m.m31, m.m32)
        calculateBindPose()
    }

    fun calculateInverseBindPose() {
        inverseBindPose.set(bindPose).invert()
        offsetVector.set(inverseBindPose.m30, inverseBindPose.m31, inverseBindPose.m32)
    }

    fun calculateBindPose() {
        bindPose.set(inverseBindPose).invert()
        bindPosition.set(bindPose.m30, bindPose.m31, bindPose.m32)
    }

    override val className get() = "Bone"
    override val approxSize get() = 1

    override fun readInt(name: String, value: Int) {
        when (name) {
            "id" -> id = value
            "parentId" -> parentId = value
            else -> super.readInt(name, value)
        }
    }

    override fun readMatrix4x3f(name: String, value: Matrix4x3f) {
        when (name) {
            "offset" -> setInverseBindPose(value)
            "relativeTransform" -> relativeTransform.set(value)
            "originalTransform" -> originalTransform.set(value)
            else -> super.readMatrix4x3f(name, value)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeInt("id", id)
        writer.writeInt("parentId", parentId, true)
        writer.writeMatrix4x3f("offset", inverseBindPose)
        writer.writeMatrix4x3f("relativeTransform", relativeTransform)
        writer.writeMatrix4x3f("originalTransform", originalTransform)
    }

    override fun clone(): Bone {
        val clone = Bone()
        copy(clone)
        return clone
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as Bone
        clone.id = id
        clone.parentId = parentId
        clone.inverseBindPose.set(inverseBindPose)
        clone.relativeTransform.set(relativeTransform)
        clone.originalTransform.set(originalTransform)
    }

}
