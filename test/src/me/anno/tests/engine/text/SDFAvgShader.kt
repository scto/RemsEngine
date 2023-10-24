package me.anno.tests.engine.text

import me.anno.engine.ui.render.ECSMeshShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable

// averaging filter to get better results?
// kills small details and corners, but the edges look better :)
object SDFAvgShader : ECSMeshShader("SDF-AVG") {
    override fun createFragmentStages(flags: Int): List<ShaderStage> {
        return listOf(
            ShaderStage(
                "material",
                createFragmentVariables(flags) +
                        listOf(
                            Variable(GLSLType.V4F, "cameraRotation"),
                            Variable(GLSLType.V1B, "invertSDF")
                        ),
                createDefines(flags).toString() +
                        discardByCullingPlane +
                        // step by step define all material properties
                        "vec2 duv = 0.5/textureSize(diffuseMap,0), duv1 = vec2(duv.x,-duv.y);\n" +
                        "float sdf = texture(diffuseMap,uv+duv).x + texture(diffuseMap,uv+duv1).x +\n" +
                        "            texture(diffuseMap,uv-duv).x + texture(diffuseMap,uv-duv1).x;\n" +
                        "finalAlpha = step(sdf,2.0);\n" +
                        "if(invertSDF) finalAlpha = 1.0 - finalAlpha;\n" +
                        "if(finalAlpha < 0.5) discard;\n" +
                        "finalColor = vertexColor0.rgb * diffuseBase.rgb;\n" +
                        normalTanBitanCalculation +
                        normalMapCalculation +
                        emissiveCalculation +
                        occlusionCalculation +
                        metallicCalculation +
                        roughnessCalculation +
                        v0 + sheenCalculation +
                        clearCoatCalculation +
                        reflectionCalculation +
                        finalMotionCalculation
            ).add(ShaderLib.quatRot).add(ShaderLib.brightness).add(ShaderLib.parallaxMapping)
        )
    }
}
