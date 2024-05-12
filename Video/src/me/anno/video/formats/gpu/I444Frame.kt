package me.anno.video.formats.gpu

import me.anno.gpu.GFX
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.Texture2D
import me.anno.io.Streams.readNBytes2
import me.anno.utils.Sleep
import java.io.InputStream

class I444Frame(iw: Int, ih: Int) : GPUFrame(iw, ih, 3) {

    companion object {
        val yuvStage = ShaderStage(
            "YUV", listOf(
                Variable(GLSLType.V2F, "finalUV"),
                Variable(GLSLType.V2F, "uvCorrection"),
                Variable(GLSLType.S2D, "texY"),
                Variable(GLSLType.S2D, "texUV"),
                Variable(GLSLType.V4F, "color", VariableMode.OUT)
            ), "" +
                    "   vec2 correctedUV = finalUV * uvCorrection;\n" +
                    "   vec2 correctedDUV = textureDeltaUV*uvCorrection;\n" +
                    "   vec3 yuv = vec3(" +
                    "       getTexture(texY, finalUV).r, " +
                    "       getTexture(texUV, correctedUV, correctedDUV).rg);\n" +
                    "   color = vec4(yuv2rgb(yuv), 1.0);\n"
        )
    }

    private val y = Texture2D("i444-y-frame", width, height, 1)
    private val uv = Texture2D("i444-uv-frame", width, height, 1)

    override fun load(input: InputStream) {
        if (isDestroyed) return

        val s0 = width * height
        val yData = input.readNBytes2(s0, Texture2D.bufferPool)
        blankDetector.putChannel(yData, 0)
        val uData = input.readNBytes2(s0, Texture2D.bufferPool)
        blankDetector.putChannel(uData, 1)
        val vData = input.readNBytes2(s0, Texture2D.bufferPool)
        blankDetector.putChannel(vData, 2)

        // merge the u and v planes
        val interlaced = interlaceReplace(uData, vData)

        Sleep.acquire(true, creationLimiter) {
            GFX.addGPUTask("I444-Y", width, height) {
                if (!isDestroyed && !y.isDestroyed && !uv.isDestroyed) {
                    y.createMonochrome(yData, false)
                    uv.createRG(interlaced, false)
                } else warnAlreadyDestroyed()
                creationLimiter.release()
            }
        }
    }

    override fun getShaderStage() = yuvStage
    override fun getTextures(): List<Texture2D> = listOf(y, uv)

    override fun bindUVCorrection(shader: Shader) {
        // all buffers are the same size, no correction required
        shader.v2f("uvCorrection", 1f, 1f)
    }
}