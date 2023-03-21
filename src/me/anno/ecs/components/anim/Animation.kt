package me.anno.ecs.components.anim

import me.anno.Engine
import me.anno.animation.LoopingState
import me.anno.cache.ICacheData
import me.anno.ecs.Entity
import me.anno.ecs.annotations.DebugProperty
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.interfaces.Renderable
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.gpu.pipeline.Pipeline
import me.anno.gpu.texture.Texture2D
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.serialization.SerializedProperty
import me.anno.maths.Maths.fract
import org.joml.Matrix4x3f

// todo allow procedural animations; for that we'd need more knowledge about the model
abstract class Animation : PrefabSaveable, Renderable, ICacheData {

    constructor() : super()

    constructor(name: String, duration: Float) : super() {
        this.name = name
        this.duration = duration
    }

    final override var name: String = ""

    @SerializedProperty
    var duration = 1f

    @SerializedProperty
    var skeleton: FileReference = InvalidRef

    @DebugProperty
    abstract val numFrames: Int

    fun calculateMonotonousTime(index: Float, frameCount: Int): Triple<Float, Int, Int> {

        val timeF = fract(index / frameCount) * frameCount

        val index0 = timeF.toInt() % frameCount
        val index1 = (index0 + 1) % frameCount

        val fraction = fract(timeF)

        return Triple(fraction, index0, index1)
    }

    abstract fun getMatrices(index: Float, dst: Array<Matrix4x3f>): Array<Matrix4x3f>?
    abstract fun getMatrices(index: Int, dst: Array<Matrix4x3f>): Array<Matrix4x3f>?

    fun getMappedAnimation(skel: FileReference): BoneByBoneAnimation {
        val dstSkel = SkeletonCache[skel] ?: throw IllegalStateException("Missing Skeleton $skel for retargeting")
        return AnimationCache.getMappedAnimation(this, dstSkel)
    }

    fun getMappedMatrices(
        frameIndex: Float,
        dst: Array<Matrix4x3f>,
        dstSkeleton: FileReference
    ): Array<Matrix4x3f>? {
        if (dstSkeleton == skeleton) return getMatrices(frameIndex, dst)
        return getMappedAnimation(dstSkeleton).getMappedMatrices(frameIndex, dst, dstSkeleton)
    }

    fun getMappedMatrices(
        frameIndex: Int,
        dst: Array<Matrix4x3f>,
        dstSkeleton: FileReference
    ): Array<Matrix4x3f>? {
        if (dstSkeleton == skeleton) return getMatrices(frameIndex, dst)
        return getMappedAnimation(dstSkeleton).getMappedMatrices(frameIndex, dst, dstSkeleton)
    }

    fun getMappedMatricesSafely(
        frameIndex: Float,
        dst: Array<Matrix4x3f>,
        dstSkeleton: FileReference
    ): Array<Matrix4x3f> {
        val base = getMappedMatrices(frameIndex, dst, dstSkeleton)
        if (base != null) return base
        for (i in dst.indices) dst[i].identity()
        return dst
    }

    fun getMappedMatricesSafely(
        frameIndex: Int,
        dst: Array<Matrix4x3f>,
        dstSkeleton: FileReference
    ): Array<Matrix4x3f> {
        val base = getMappedMatrices(frameIndex, dst, dstSkeleton)
        if (base != null) return base
        for (i in dst.indices) dst[i].identity()
        return dst
    }

    class PreviewData(skeleton: Skeleton, animation: Animation) {
        // todo why is there no animation playing?

        val bones = skeleton.bones
        val mesh = Mesh()
        val renderer = AnimRenderer()
        val state = AnimationState(animation.ref, 1f, 0f, 1f, LoopingState.PLAY_LOOP)

        init {
            val size = (bones.size - 1) * Skeleton.boneMeshVertices.size
            mesh.positions = Texture2D.floatArrayPool[size, false, true]
            mesh.normals = Texture2D.floatArrayPool[size, true, true]
            mesh.boneIndices = Texture2D.byteArrayPool[size * 4 / 3, true, true]
            Skeleton.generateSkeleton(
                bones, Array(bones.size) { bones[it].bindPosition },
                mesh.positions!!, mesh.boneIndices!!
            )
            renderer.mesh = mesh.ref
            renderer.skeleton = skeleton.ref
            renderer.animations = listOf(state)
        }

        fun destroy() {
            Texture2D.floatArrayPool.returnBuffer(mesh.positions)
            Texture2D.floatArrayPool.returnBuffer(mesh.normals)
            Texture2D.byteArrayPool.returnBuffer(mesh.boneIndices)
            mesh.positions = null
            mesh.normals = null
            mesh.boneIndices = null
            mesh.destroy()
        }

        override fun toString(): String {
            return state.progress.toString()
        }

    }

    @DebugProperty
    private var previewData: PreviewData? = null

    override fun fill(
        pipeline: Pipeline,
        entity: Entity,
        clickId: Int
    ): Int {
        val skeleton = SkeletonCache[skeleton] ?: return clickId
        if (previewData == null) previewData = PreviewData(skeleton, this)
        return previewData!!.run {
            if (renderer.prevTime != Engine.gameTime) {
                state.update(renderer, Engine.deltaTime, false)
                renderer.updateAnimState()
            }
            renderer.fill(pipeline, entity, clickId)
        }
    }

    // todo add all debug information to UI

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFloat("duration", duration)
        writer.writeFile("skeleton", skeleton)
    }

    override fun readDouble(name: String, value: Double) {
        when (name) {
            "duration" -> duration = value.toFloat()
            else -> super.readDouble(name, value)
        }
    }

    override fun readFloat(name: String, value: Float) {
        when (name) {
            "duration" -> duration = value
            else -> super.readFloat(name, value)
        }
    }

    override fun readFile(name: String, value: FileReference) {
        when (name) {
            "skeleton" -> skeleton = value
            else -> super.readFile(name, value)
        }
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as Animation
        clone.skeleton = skeleton
        clone.duration = duration
    }

    override fun onDestroy() {
        super.onDestroy()
        previewData?.destroy()
        previewData = null
    }

    override fun destroy() {
        super.destroy()
        onDestroy()
    }

    override val approxSize get() = 100

}