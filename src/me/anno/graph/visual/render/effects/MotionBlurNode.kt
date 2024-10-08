package me.anno.graph.visual.render.effects

import me.anno.gpu.GFX
import me.anno.gpu.GFXState.timeRendering
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.TargetType
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.shader.renderer.Renderer.Companion.copyRenderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.TextureLib.blackTexture
import me.anno.gpu.texture.TextureLib.missingTexture
import me.anno.graph.visual.render.Texture
import me.anno.graph.visual.render.Texture.Companion.texOrNull
import me.anno.maths.Maths.clamp

class MotionBlurNode : TimedRenderingNode(
    "Motion Blur",
    listOf(
        "Int", "Samples",
        "Float", "Shutter",
        "Texture", "Illuminated",
        "Texture", "Motion",
    ), listOf("Texture", "Illuminated")
) {

    init {
        setInput(1, 16)
        setInput(2, 0.5f)
    }

    override fun executeAction() {

        val samples = clamp(getIntInput(1), 1, GFX.maxSamples)
        val shutter = getFloatInput(2)
        val color = (getInput(3) as? Texture).texOrNull ?: missingTexture
        val motion = (getInput(4) as? Texture).texOrNull ?: blackTexture

        timeRendering(name, timer) {
            val framebuffer = FBStack[
                name, color.width, color.height, if (color.isHDR) TargetType.Float16x3 else TargetType.UInt8x3,
                samples, DepthBufferType.NONE
            ]
            framebuffer.isSRGBMask = 1
            useFrame(color.width, color.height, true, framebuffer, copyRenderer) {
                val shader = shader
                shader.use()
                GFX.check()
                color.bind(0, Filtering.TRULY_LINEAR, Clamping.CLAMP)
                motion.bindTrulyNearest(1)
                GFX.check()
                shader.v1i("maxSamples", samples)
                shader.v1f("shutter", shutter)
                GFX.check()
                SimpleBuffer.flat01.draw(shader)
                GFX.check()
            }
            setOutput(1, Texture.texture(framebuffer, 0))
        }
    }


    companion object {
        // definitely not ideal; we'd need to smear moving objects instead of smearing on moving objects
        private val shader = Shader(
            "MotionBlur", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList,
            listOf(
                Variable(GLSLType.V1I, "maxSamples"),
                Variable(GLSLType.V1F, "shutter"),
                Variable(GLSLType.S2D, "colorTex"),
                Variable(GLSLType.S2D, "motionTex"),
                Variable(GLSLType.V4F, "result", VariableMode.OUT)
            ), "" +
                    "void main(){\n" +
                    "   vec2 motion = 0.5 * shutter * texture(motionTex,uv).xy;\n" + // 0.5, because we sample from both sides
                    "   float length1 = length(motion * vec2(textureSize(motionTex,0)));\n" +
                    "   int samplesI = min(maxSamples, int(round(length1)));\n" +
                    "   vec4 res = texture(colorTex, uv);\n" +
                    "   if(samplesI > 1){\n" +
                    "       vec2 uv2 = uv, uv3 = uv;\n" +
                    "       vec2 duv = motion / float(samplesI-1);\n" +
                    "       res *= 2.0;\n" +
                    "       for(int i=1;i<samplesI;i++){\n" +
                    "           uv2 += duv;\n" +
                    "           uv3 -= duv;\n" +
                    "           res += texture(colorTex, uv2) + texture(colorTex, uv3);\n" +
                    "       }\n" +
                    "       res /= float(samplesI);\n" +
                    "       res *= 0.5;\n" +
                    "   }\n" +
                    "   result = res;\n" +
                    "}\n"
        ).setTextureIndices("colorTex", "motionTex") as Shader
    }
}