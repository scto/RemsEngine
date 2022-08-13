package me.anno.gpu.framebuffer

import me.anno.gpu.GFX
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.ITexture2D
import org.lwjgl.opengl.GL30C

/**
 * standard, presentation framebuffer
 * */
object NullFramebuffer : IFramebuffer {

    override val name = "null"
    override val pointer = 0

    override val w get() = (GFX.activeWindow ?: GFX.someWindow).width
    override val h get() = (GFX.activeWindow ?: GFX.someWindow).height
    override val samples = 1
    override val numTextures = 1

    override fun ensure() {}

    override fun bindDirectly() {
        Framebuffer.bindFramebuffer(GL30C.GL_FRAMEBUFFER, 0)
        Frame.lastPtr = 0
    }

    override fun bindDirectly(w: Int, h: Int) {
        bindDirectly()
    }

    override fun destroy() {} // cannot really be destroyed

    override fun attachFramebufferToDepth(targetCount: Int, fpTargets: Boolean): IFramebuffer {
        throw UnsupportedOperationException()
    }

    override fun attachFramebufferToDepth(targets: Array<TargetType>): IFramebuffer {
        throw UnsupportedOperationException()
    }

    override fun checkSession() {}

    override fun bindTextureI(index: Int, offset: Int, nearest: GPUFiltering, clamping: Clamping) {
        throw UnsupportedOperationException()
    }

    override fun bindTextures(offset: Int, nearest: GPUFiltering, clamping: Clamping) {
        throw UnsupportedOperationException()
    }

    override fun getTextureI(index: Int): ITexture2D {
        throw UnsupportedOperationException()
    }

    override val depthTexture: ITexture2D?
        get() = throw UnsupportedOperationException()

}