package me.anno.ecs.components.anim

import me.anno.Engine
import me.anno.animation.LoopingState
import me.anno.ecs.Entity
import me.anno.ecs.annotations.Docs
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.anim.AnimTexture.Companion.useAnimTextures
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.raycast.RayHit
import me.anno.engine.raycast.Raycast
import me.anno.gpu.shader.Shader
import me.anno.gpu.texture.Texture2D
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.serialization.NotSerializedProperty
import me.anno.io.serialization.SerializedProperty
import me.anno.mesh.assimp.AnimGameItem
import org.joml.Matrix4x3f
import org.joml.Vector3d
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class AnimRenderer : MeshComponent() {

    // todo in debug mode, we could render the skeleton as well/instead :)

    @Docs("Maps bone indices to names & hierarchy")
    @Type("Skeleton/Reference")
    @SerializedProperty
    var skeleton: FileReference = InvalidRef

    // maybe not the most efficient way, but it should work :)
    @Docs("Maps time & bone index onto local transform")
    @Type("List<AnimationState>")
    @SerializedProperty
    var animations: List<AnimationState> = emptyList()

    @Docs("If no animation is set, use default?")
    var useDefaultAnimation = true

    // animation state for motion vectors
    @NotSerializedProperty
    var prevTime = 0L

    @NotSerializedProperty
    val prevWeights = Vector4f()

    @NotSerializedProperty
    val prevIndices = Vector4f()

    @NotSerializedProperty
    val currWeights = Vector4f()

    @NotSerializedProperty
    val currIndices = Vector4f()

    open fun onAnimFinished(anim: AnimationState) {
        val instance = AnimationCache[anim.source]
        if (instance != null) {
            val duration = instance.duration
            anim.progress = anim.repeat[anim.progress, duration]
        }
    }

    private var lastUpdate = 0L
    override fun onUpdate(): Int {
        // update all weights
        return if (lastUpdate != Engine.gameTime) {
            lastUpdate = Engine.gameTime
            val dt = Engine.deltaTime
            var anyIsRunning = false
            for (index in animations.indices) {
                val anim = animations[index]
                anim.update(this, dt, true)
                if (anim.speed != 0f) anyIsRunning = true
            }
            updateAnimState()
            if (anyIsRunning) 1 else 10
        } else 1
    }

    override val hasAnimation: Boolean
        get() {
            val skeleton = SkeletonCache[skeleton]
            return skeleton != null && (useDefaultAnimation || animations.isNotEmpty())
        }

    fun addState(state: AnimationState) {
        synchronized(this) {
            val animations = animations
            if (animations is MutableList) {
                animations.add(state)
            } else {
                val newList = ArrayList<AnimationState>(animations.size + 4)
                newList.addAll(animations)
                newList.add(state)
                this.animations = newList
            }
        }
    }

    override fun defineVertexTransform(shader: Shader, entity: Entity, mesh: Mesh): Boolean {

        val skeleton = SkeletonCache[skeleton]
        if (skeleton == null) {
            lastWarning = "Skeleton missing"
            return false
        }

        shader.use()

        // check whether the shader actually uses bones
        val location = shader[if (useAnimTextures) "animWeights" else "jointTransforms"]

        if (useDefaultAnimation && animations.isEmpty() && skeleton.animations.isNotEmpty()) {
            val sample = skeleton.animations.entries.firstOrNull()?.value
            if (sample != null) {
                addState(AnimationState(sample, 0f, 0f, 0f, LoopingState.PLAY_LOOP))
            } else {
                lastWarning = "No animation was found"
                return false
            }
        } else if (animations.isEmpty()) {
            lastWarning = "No animation is set"
            return false
        }

        if (location <= 0) {
            lastWarning = "Shader '${shader.name}' is missing location"
            return false
        }

        if (useAnimTextures) {

            updateAnimState()

            shader.v4f("prevAnimWeights", prevWeights)
            shader.v4f("prevAnimIndices", prevIndices)

            shader.v4f("animWeights", currWeights)
            shader.v4f("animIndices", currIndices)

            val animTexture = AnimationCache[skeleton]
            val animTexture2 = animTexture.texture
            if (animTexture2 == null) {
                if (lastWarning == null) lastWarning = "AnimTexture is invalid"
                return false
            }

            lastWarning = null
            animTexture2.bindTrulyNearest(shader, "animTexture")
            return true

        } else {
            // what if the weight is less than 1? change to T-pose? no, the programmer can define that himself with an animation
            // val weightNormalization = 1f / max(1e-7f, animationWeights.values.sum())
            val matrices = getMatrices() ?: return false
            // upload the matrices
            upload(shader, location, matrices)
            return true
        }
    }

    fun updateAnimState(): Boolean {
        val time = Engine.gameTime
        return if (time != prevTime) {
            prevTime = time
            prevWeights.set(currWeights)
            prevIndices.set(currIndices)
            getAnimState(currWeights, currIndices)
        } else true // mmh...
    }

    /**
     * gets the animation matrices; thread-unsafe, can only be executed on gfx thread
     * */
    fun getMatrices(): Array<Matrix4x3f>? {
        var matrices: Array<Matrix4x3f>? = null
        var sumWeight = 0f
        val animations = animations
        val skeleton = skeleton
        for (index in animations.indices) {
            val animSource = animations[index]
            val weight = animSource.weight
            val relativeWeight = weight / (sumWeight + weight)
            val animation = AnimationCache[animSource.source] ?: continue
            val frameIndex = (animSource.progress * animation.numFrames) / animation.duration
            if (matrices == null) {
                matrices = animation.getMappedMatricesSafely(frameIndex, tmpMapping0, skeleton)
            } else if (relativeWeight > 0f) {
                val matrix = animation.getMappedMatricesSafely(frameIndex, tmpMapping1, skeleton)
                for (j in matrices.indices) {
                    matrices[j].lerp(matrix[j], relativeWeight)
                }
            }
            sumWeight += max(0f, weight)
        }
        return matrices
    }

    open fun getAnimTexture(): Texture2D? {
        val skeleton = SkeletonCache[skeleton] ?: return null
        return AnimationCache[skeleton].texture
    }

    open fun getAnimState(
        dstWeights: Vector4f,
        dstIndices: Vector4f
    ): Boolean {

        val skeleton = SkeletonCache[skeleton]
        if (skeleton == null) {
            lastWarning = "Skeleton missing"
            return false
        }

        if (useDefaultAnimation && animations.isEmpty() && skeleton.animations.isNotEmpty()) {
            val sample = skeleton.animations.entries.firstOrNull()?.value
            if (sample != null) {
                addState(AnimationState(sample, 0f, 0f, 0f, LoopingState.PLAY_LOOP))
            } else {
                lastWarning = "No animation was found"
                return false
            }
        } else if (animations.isEmpty()) {
            lastWarning = "No animation is set"
            return false
        }


        // what if the weight is less than 1? change to T-pose? no, the programmer can define that himself with an animation
        // val weightNormalization = 1f / max(1e-7f, animationWeights.values.sum())
        val animations = animations

        dstWeights.set(1f, 0f, 0f, 0f)
        dstIndices.set(0f)

        // find major weights & indices in anim texture
        val animTexture = AnimationCache[skeleton]
        var writeIndex = 0
        for (index in animations.indices) {
            val animState = animations[index]
            val weight = animState.weight
            if (abs(weight) > abs(dstWeights[dstWeights.minComponent()])) {
                val animation = AnimationCache[animState.source] ?: continue
                val frameIndex = animState.progress / animation.duration * animation.numFrames
                val internalIndex = animTexture.getIndex(animation, frameIndex)
                if (writeIndex < 4) {
                    dstIndices.setComponent(writeIndex, internalIndex)
                    dstWeights.setComponent(writeIndex, weight)
                    writeIndex++
                } else {
                    val nextIndex = dstWeights.minComponent()
                    dstIndices.setComponent(nextIndex, internalIndex)
                    dstWeights.setComponent(nextIndex, weight)
                }
            }
        }

        return true

    }

    override fun raycast(
        entity: Entity, start: Vector3d, direction: Vector3d, end: Vector3d,
        radiusAtOrigin: Double, radiusPerUnit: Double,
        typeMask: Int, includeDisabled: Boolean, result: RayHit
    ): Boolean {
        val mesh = getMesh() ?: return false
        if (!mesh.hasBones) return false
        updateAnimState()
        val matrices = getMatrices() ?: return super.raycast(
            entity, start, direction, end,
            radiusAtOrigin, radiusPerUnit, typeMask, includeDisabled, result
        )
        val original = result.distance
        Raycast.globalRaycastByBones(
            result, entity.transform.globalTransform,
            mesh, start, direction, radiusAtOrigin,
            radiusPerUnit, typeMask,
            matrices
        )
        return if (Raycast.eval(result, start, direction, end, original)) {
            result.mesh = mesh
            result.component = this
            true
        } else false
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as AnimRenderer
        clone.skeleton = skeleton
        clone.animations = animations
        clone.useDefaultAnimation = useDefaultAnimation
        clone.prevIndices.set(prevIndices)
        clone.prevTime = prevTime
        clone.prevWeights.set(prevWeights)
        clone.currIndices.set(currIndices)
        clone.currWeights.set(currWeights)
    }

    override val className get() = "AnimRenderer"

    companion object {

        private val tmpMapping0 = Array(256) { Matrix4x3f() }
        private val tmpMapping1 = Array(256) { Matrix4x3f() }

        fun upload(shader: Shader, location: Int, matrices: Array<Matrix4x3f>) {
            val boneCount = min(matrices.size, AnimGameItem.maxBones)
            val buffer = AnimGameItem.matrixBuffer
            buffer.limit(AnimGameItem.matrixSize * boneCount)
            for (index in 0 until boneCount) {
                val matrix0 = matrices[index]
                buffer.position(index * AnimGameItem.matrixSize)
                AnimGameItem.get(matrix0, buffer)
            }
            buffer.position(0)
            shader.m4x3Array(location, buffer)
        }

    }

}