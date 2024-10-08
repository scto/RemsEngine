package me.anno.tests.engine.material

import me.anno.ecs.components.mesh.ImagePlane
import me.anno.ecs.components.mesh.material.utils.TypeValue
import me.anno.ecs.components.mesh.material.utils.TypeValueTex
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.TextureLib.blackTexture
import me.anno.io.files.InvalidRef
import me.anno.utils.OS.res

/**
 * inspired by Jam2go, https://www.youtube.com/watch?v=LGkblrCmzlE
 * */
object ScreenShader : ECSMeshShader("Screen") {
    // todo lod-bias seems to not be supported by Intel drivers :/
    override fun createFragmentStages(key: ShaderKey): List<ShaderStage> {
        return super.createFragmentStages(key) + ShaderStage(
            "Screen", listOf(
                Variable(GLSLType.S2D, "screenTexture"),
                Variable(GLSLType.V1F, "screenLodBias"),
                Variable(GLSLType.V3F, "finalEmissive", VariableMode.INOUT),
                Variable(GLSLType.V2F, "uv"),
                Variable(GLSLType.S2D, "emissiveMap")
            ), colorToSRGB + // ensure sRGB space before multiplying, because our texture is sRGB
                    "ivec2 texSize = textureSize(emissiveMap,0);\n" +
                    "if(max(texSize.x,texSize.y) > 1) {\n" +
                    "   finalEmissive *= texture(screenTexture, uv * vec2(texSize), screenLodBias).rgb;\n" +
                    "}"
        )
    }
}

fun main() {
    // create image plane
    val mask = res.getChild("textures/RGBMask.png")
    val image = res.getChild("textures/dig8.png")
    val plane = ImagePlane()
    plane.material.apply {
        shader = ScreenShader
        diffuseBase.set(0f, 0f, 0f, 1f)
        diffuseMap = InvalidRef
        emissiveMap = image
        emissiveBase.set(7f)
        linearFiltering = false // we render pixels 😄
        shaderOverrides["screenLodBias"] = TypeValue(GLSLType.V1F, 1f)
        shaderOverrides["screenTexture"] =
            TypeValueTex(GLSLType.S2D, mask, Filtering.LINEAR, Clamping.REPEAT, blackTexture)
    }
    testSceneWithUI("Screen Material", plane)
}