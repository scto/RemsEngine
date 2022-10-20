package me.anno.gpu.framebuffer

import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.shader.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture3D
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL45C

class Framebuffer3D(
    override var name: String,
    override var w: Int,
    override var h: Int,
    val d: Int,
    val targets: Array<TargetType>,
    val depthBufferType: DepthBufferType
) : IFramebuffer {

    override var pointer = 0
    var session = 0

    override val samples = 1
    override val numTextures = 1
    override var depthTexture: Texture3D? = null

    lateinit var textures: Array<Texture3D>

    var depthAttachedPtr = 0
    var depthAttachment: Framebuffer3D? = null

    fun create() {
        Frame.invalidate()
        GFX.check()
        val pointer = GL30C.glGenFramebuffers()
        if (pointer <= 0) throw OutOfMemoryError("Could not generate OpenGL framebuffer")
        session = GFXState.session
        // if (Build.isDebug) DebugGPUStorage.fbs.add(this)
        Framebuffer.bindFramebuffer(GL30C.GL_FRAMEBUFFER, pointer)
        Frame.lastPtr = pointer
        val w = w
        val h = h
        val d = d
        if (w * h * d < 1) throw RuntimeException("Invalid framebuffer size $w x $h x $d")
        GFX.check()
        textures = Array(targets.size) { index ->
            val texture = Texture3D("$name-tex[$index]", w, h, d)
            // texture.autoUpdateMipmaps = autoUpdateMipmaps
            texture.create(targets[index])
            GFX.check()
            texture
        }
        GFX.check()
        val textures = textures
        for (index in targets.indices) {
            val texture = textures[index]
            GL30C.glFramebufferTexture3D(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_COLOR_ATTACHMENT0 + index,
                texture.target,
                texture.pointer,
                0,
                0 // todo change the layer dynamically like for cubemaps
            )
        }
        GFX.check()
        when (targets.size) {
            0 -> GL30C.glDrawBuffer(GL30C.GL_NONE)
            1 -> GL30C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0)
            else -> GL30C.glDrawBuffers(textures.indices.map { GL30C.GL_COLOR_ATTACHMENT0 + it }.toIntArray())
        }
        GFX.check()
        when (depthBufferType) {
            DepthBufferType.NONE -> {
            }
            DepthBufferType.ATTACHMENT -> {
                val texPointer = depthAttachment?.depthTexture?.pointer
                    ?: throw IllegalStateException("Depth Attachment was not found in $name, ${depthAttachment}.${depthAttachment?.depthTexture}")
                GL30C.glFramebufferTexture3D(
                    GL30C.GL_FRAMEBUFFER,
                    GL30C.GL_DEPTH_ATTACHMENT,
                    GL30C.GL_TEXTURE_3D,
                    texPointer,
                    0, 0
                )
                depthAttachedPtr = texPointer
            }
            DepthBufferType.INTERNAL -> throw NotImplementedError()// createDepthBuffer()
            DepthBufferType.TEXTURE, DepthBufferType.TEXTURE_16 -> {
                val depthTexture = Texture3D("$name-depth", w, h, d)
                // depthTexture.autoUpdateMipmaps = autoUpdateMipmaps
                depthTexture.create(if (depthBufferType == DepthBufferType.TEXTURE_16) TargetType.DEPTH16 else TargetType.DEPTH32F)
                GL30C.glFramebufferTexture3D(
                    GL30C.GL_FRAMEBUFFER,
                    GL30C.GL_DEPTH_ATTACHMENT,
                    depthTexture.target,
                    depthTexture.pointer,
                    0, 0
                )
                this.depthTexture = depthTexture
            }
        }
        GFX.check()
        check(pointer)
        this.pointer = pointer
    }

    fun check(pointer: Int) {
        val state = GL45C.glCheckNamedFramebufferStatus(pointer, GL30C.GL_FRAMEBUFFER)
        if (state != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is incomplete: ${GFX.getErrorTypeName(state)}")
        }
    }

    override fun ensure() {
        checkSession()
        if (pointer <= 0) create()
    }

    override fun checkSession() {
        if (pointer > 0 && session != GFXState.session) {
            GFX.check()
            session = GFXState.session
            pointer = -1
            // needsBlit = true
            // ssBuffer?.checkSession()
            // depthTexture?.checkSession()
            for (texture in textures) {
                texture.checkSession()
            }
            GFX.check()
            // validate it
            create()
        }
    }

    override fun bindDirectly() {
        bindDirectly(w, h)
    }

    override fun bindDirectly(w: Int, h: Int) {

    }

    override fun destroy() {
        if (pointer > 0) {
            GFX.checkIsGFXThread()
            // ssBuffer?.destroy()
            destroyFramebuffer()
            destroyInternalDepth()
            destroyTextures(true)
        }
    }

    fun destroyFramebuffer() {
        if (pointer > -1) {
            GL30C.glDeleteFramebuffers(pointer)
            Frame.invalidate()
            // if (Build.isDebug) DebugGPUStorage.fbs.remove(this)
            pointer = -1
        }
    }

    fun destroyInternalDepth() {
        // not implemented
        /*if (internalDepthTexture > -1) {
            GL30C.glDeleteRenderbuffers(internalDepthTexture)
            depthAllocated = Texture2D.allocate(depthAllocated, 0L)
            internalDepthTexture = -1
        }*/
    }

    fun destroyTextures(deleteDepth: Boolean) {
        for (tex in textures) tex.destroy()
        if (deleteDepth) destroyDepthTexture()
    }

    fun destroyDepthTexture() {
        depthTexture?.destroy()
    }

    override fun bindTextureI(index: Int, offset: Int, nearest: GPUFiltering, clamping: Clamping) {
        textures[index].bind(offset, nearest, clamping)
    }

    override fun bindTextures(offset: Int, nearest: GPUFiltering, clamping: Clamping) {
        for (index in textures.indices) {
            textures[index].bind(index + offset, nearest, clamping)
        }
    }

    fun draw(renderer: Renderer, render: (side: Int) -> Unit) {
        GFXState.useFrame(this, renderer) {
            Frame.bind()
            for (slice in 0 until d) {
                // update all attachments, updating the framebuffer texture targets
                updateAttachments(slice)
                val status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw IllegalStateException("Framebuffer incomplete $status")
                render(slice)
            }
        }
    }

    fun updateAttachments(layer: Int) {
        val target = GL30.GL_TEXTURE_3D
        val textures = textures
        for (index in textures.indices) {
            val texture = textures[index]
            GL30.glFramebufferTexture3D(
                GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index,
                target, texture.pointer, 0, layer
            )
        }
        GFX.check()
        when (targets.size) {
            0 -> GL30.glDrawBuffer(GL30.GL_NONE)
            1 -> GL30.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0)
            else -> GL30.glDrawBuffers(textures.indices.map { it + GL30.GL_COLOR_ATTACHMENT0 }.toIntArray())
        }
        GFX.check()
        if (depthBufferType == DepthBufferType.TEXTURE || depthBufferType == DepthBufferType.TEXTURE_16) {
            val depthTexture = depthTexture!!
            GL30.glFramebufferTexture3D(
                GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                target, depthTexture.pointer, 0, layer
            )
        }
        GFX.check()
    }

    fun destroyExceptTextures(deleteDepth: Boolean) {
        /*if (ssBuffer != null) {
            ssBuffer?.destroyExceptTextures(deleteDepth)
            ssBuffer = null
            destroy()
        } else {*/
        destroyFramebuffer()
        destroyInternalDepth()
        if (deleteDepth) destroyDepthTexture()
        //}
    }

    override fun getTexture0() = textures[0]
    override fun getTextureI(index: Int) = textures[index]

    override fun attachFramebufferToDepth(targetCount: Int, fpTargets: Boolean): IFramebuffer {
        throw NotImplementedError()
    }

    override fun attachFramebufferToDepth(targets: Array<TargetType>): IFramebuffer {
        throw NotImplementedError()
    }

}