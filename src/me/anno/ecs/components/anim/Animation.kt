package me.anno.ecs.components.anim

import me.anno.Time
import me.anno.animation.LoopingState
import me.anno.cache.ICacheData
import me.anno.ecs.Entity
import me.anno.ecs.annotations.DebugProperty
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.interfaces.Renderable
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.serialization.SerializedProperty
import me.anno.gpu.pipeline.Pipeline
import me.anno.gpu.texture.Texture2D
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.maths.Maths.fract
import me.anno.utils.types.AnyToFloat
import org.joml.Matrix4x3f

/**
 * skeletal animation base class
 * */
abstract class Animation : PrefabSaveable, Renderable, ICacheData {

    constructor() : super()

    constructor(name: String, duration: Float) : super() {
        this.name = name
        this.duration = duration
    }

    final override var name: String = ""

    @SerializedProperty
    var duration = 1f

    @Type("Skeleton/Reference")
    @SerializedProperty
    var skeleton: FileReference = InvalidRef

    @DebugProperty
    abstract val numFrames: Int

    fun calculateMonotonousTime(frameIndex: Float, frameCount: Int): Triple<Float, Int, Int> {

        val timeF = fract(frameIndex / frameCount) * frameCount

        val index0 = timeF.toInt() % frameCount
        val index1 = (index0 + 1) % frameCount

        val fraction = fract(timeF)

        return Triple(fraction, index0, index1)
    }

    abstract fun getMatrices(frameIndex: Float, dst: List<Matrix4x3f>): List<Matrix4x3f>?
    abstract fun getMatrices(frameIndex: Int, dst: List<Matrix4x3f>): List<Matrix4x3f>?

    abstract fun getMatrix(frameIndex: Float, boneId: Int, dst: List<Matrix4x3f>): Matrix4x3f?
    abstract fun getMatrix(frameIndex: Int, boneId: Int, dst: List<Matrix4x3f>): Matrix4x3f?

    fun getMappedAnimation(skel: FileReference): Animation? {
        if (skel == skeleton) return this
        val dstSkel = SkeletonCache[skel] ?: throw IllegalStateException("Missing Skeleton $skel for retargeting")
        return AnimationCache.getMappedAnimation(this, dstSkel)
    }

    fun getMappedMatrices(
        frameIndex: Float,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference
    ): List<Matrix4x3f>? {
        return getMappedAnimation(dstSkeleton)
            ?.getMatrices(frameIndex, dst)
    }

    fun getMappedMatrix(
        frameIndex: Float,
        boneId: Int,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference
    ): Matrix4x3f? {
        return getMappedAnimation(dstSkeleton)
            ?.getMatrix(frameIndex, boneId, dst)
    }

    fun getMappedMatrices(
        frameIndex: Int,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference
    ): List<Matrix4x3f>? {
        return getMappedAnimation(dstSkeleton)
            ?.getMatrices(frameIndex, dst)
    }

    fun getMappedMatricesSafely(
        frameIndex: Float,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference
    ): List<Matrix4x3f> {
        val base = getMappedMatrices(frameIndex, dst, dstSkeleton)
        if (base != null) return base
        for (i in dst.indices) dst[i].identity()
        return dst
    }

    fun getMappedMatrixSafely(
        frameIndex: Float,
        boneId: Int,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference,
    ): Matrix4x3f {
        return getMappedMatrix(frameIndex, boneId, dst, dstSkeleton) ?: dst[0].identity()
    }

    fun getMappedMatricesSafely(
        frameIndex: Int,
        dst: List<Matrix4x3f>,
        dstSkeleton: FileReference
    ): List<Matrix4x3f> {
        val base = getMappedMatrices(frameIndex, dst, dstSkeleton)
        if (base != null) return base
        for (i in dst.indices) dst[i].identity()
        return dst
    }

    class PreviewData(skeleton: Skeleton, animation: Animation) {
        // todo why is there no animation playing?

        val bones = skeleton.bones
        val mesh = Mesh()
        val renderer = AnimMeshComponent()
        val state = AnimationState(animation.ref, 1f, 0f, 1f, LoopingState.PLAY_LOOP)

        init {
            val size = (bones.size - 1) * Skeleton.boneMeshVertices.size
            mesh.positions = Texture2D.floatArrayPool[size, false, true]
            mesh.normals = Texture2D.floatArrayPool[size, true, true]
            mesh.boneIndices = Texture2D.byteArrayPool[size * 4 / 3, true, true]
            Skeleton.generateSkeleton(
                bones, bones.map { it.bindPosition },
                mesh.positions!!, mesh.boneIndices!!
            )
            renderer.meshFile = mesh.ref
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
            if (renderer.prevTime != Time.gameTimeN) {
                state.update(renderer, Time.deltaTime.toFloat(), false)
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

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "duration" -> duration = AnyToFloat.getFloat(value, 0f)
            "skeleton" -> skeleton = value as? FileReference ?: InvalidRef
            else -> super.setProperty(name, value)
        }
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as Animation
        dst.skeleton = skeleton
        dst.duration = duration
    }

    override fun destroy() {
        super.destroy()
        previewData?.destroy()
        previewData = null
    }

    override val approxSize get() = 100
}