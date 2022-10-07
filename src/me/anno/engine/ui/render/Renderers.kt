package me.anno.engine.ui.render

import me.anno.ecs.components.camera.effects.CameraEffect
import me.anno.ecs.components.light.DirectionalLight
import me.anno.ecs.components.light.LightType
import me.anno.ecs.components.light.PointLight
import me.anno.ecs.components.light.SpotLight
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoDivInlined2
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoDivInlined2End
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoDivInlined2Start
import me.anno.gpu.GFX
import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01
import me.anno.gpu.deferred.DeferredLayerType
import me.anno.gpu.deferred.DeferredSettingsV2
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Renderer
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderFuncLib.noiseFunc
import me.anno.gpu.shader.ShaderLib.coordsList
import me.anno.gpu.shader.ShaderLib.coordsVShader
import me.anno.gpu.shader.ShaderLib.uvList
import me.anno.gpu.shader.SimpleRenderer
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.maths.Maths.length
import me.anno.utils.pooling.ByteBufferPool
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.maps.LazyMap
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

object Renderers {

    // reinhard tonemapping works often, but not always: {0,1}³ does not work, add spilling somehow
    // also maybe it should be customizable...

    var tonemapGLSL = "" +
            "vec3 tonemap(vec3 color){ return color / (1.0 + max(max(color.r, color.g), max(color.b, 0.0))); }\n" +
            "vec4 tonemap(vec4 color){ return vec4(tonemap(color.rgb), color.a); }\n"

    var tonemapKt = { color: Vector3f ->
        color.div(1f + max(max(color.x, color.y), max(color.z, 0f)))
    }

    var tonemapInvKt = { color: Vector3f ->
        color.div(1f - max(max(color.x, color.y), max(color.z, 0f)))
    }

    fun tonemapKt(color: Vector4f): Vector4f {
        val tmp = JomlPools.vec3f.create()
        tmp.set(color.x, color.y, color.z)
        tonemapKt(tmp)
        color.set(tmp.x, tmp.y, tmp.z)
        JomlPools.vec3f.sub(1)
        return color
    }

    fun tonemapInvKt(color: Vector4f): Vector4f {
        val tmp = JomlPools.vec3f.create()
        tmp.set(color.x, color.y, color.z)
        tonemapInvKt(tmp)
        color.set(tmp.x, tmp.y, tmp.z)
        JomlPools.vec3f.sub(1)
        return color
    }

    val overdrawRenderer = SimpleRenderer(
        "overdraw", ShaderStage(
            "overdraw", listOf(Variable(GLSLType.V4F, "finalOverdraw", VariableMode.OUT)),
            "finalOverdraw = vec4(0.125);\n"
        )
    )

    // same functionality :D
    val cheapRenderer = overdrawRenderer

    val pbrRenderer = object : Renderer("pbr") {
        override fun getPostProcessing(): ShaderStage {
            return ShaderStage("pbr", listOf(
                // rendering
                Variable(GLSLType.V1B, "applyToneMapping"),
                // light data
                Variable(GLSLType.V3F, "ambientLight"),
                Variable(GLSLType.V1I, "numberOfLights"),
                Variable(GLSLType.V1B, "receiveShadows"),
                Variable(GLSLType.M4x3, "invLightMatrices", RenderView.MAX_FORWARD_LIGHTS),
                Variable(GLSLType.V4F, "lightData0", RenderView.MAX_FORWARD_LIGHTS),
                Variable(GLSLType.V4F, "lightData1", RenderView.MAX_FORWARD_LIGHTS),
                Variable(GLSLType.V4F, "shadowData", RenderView.MAX_FORWARD_LIGHTS),
                // light maps for shadows
                // - spot lights, directional lights
                Variable(GLSLType.S2D, "shadowMapPlanar", MAX_PLANAR_LIGHTS),
                // - point lights
                Variable(GLSLType.SCube, "shadowMapCubic", MAX_CUBEMAP_LIGHTS),
                // reflection plane for rivers or perfect mirrors
                Variable(GLSLType.V1B, "hasReflectionPlane"),
                Variable(GLSLType.S2D, "reflectionPlane"),
                // reflection cubemap or irradiance map
                Variable(GLSLType.SCube, "reflectionMap"),
                // material properties
                Variable(GLSLType.V3F, "finalEmissive"),
                Variable(GLSLType.V1F, "finalMetallic"),
                Variable(GLSLType.V1F, "finalRoughness"),
                Variable(GLSLType.V1F, "finalOcclusion"),
                Variable(GLSLType.V1F, "finalSheen"),
                // Variable(GLSLType.V3F, "finalSheenNormal"),
                // Variable(GLSLType.V4F, "finalClearCoat"),
                // Variable(GLSLType.V2F, "finalClearCoatRoughMetallic"),
                // if the translucency > 0, the normal map probably should be turned into occlusion ->
                // no, or at max slightly, because the surrounding area will illuminate it
                Variable(GLSLType.V1F, "finalTranslucency"),
                Variable(GLSLType.V1F, "finalAlpha"),
                Variable(GLSLType.V3F, "finalPosition"),
                Variable(GLSLType.V3F, "finalNormal"),
                Variable(GLSLType.V3F, "finalColor"),
                Variable(GLSLType.V4F, "finalResult", VariableMode.OUT)
            ), "" +
                    // define all light positions, radii, types and colors
                    // use the lights to illuminate the model
                    // light data
                    // a try of depth dithering, which can be used for plants, but is really expensive...
                    // "   gl_FragDepth = 1.0/(1.0+zDistance) * (1.0 + 0.001 * random(finalPosition.xy));\n" +
                    // shared pbr data
                    "#ifndef SKIP_LIGHTS\n" +
                    "   vec3 V = normalize(-finalPosition);\n" +
                    // light calculations
                    "   float NdotV = abs(dot(finalNormal,V));\n" +
                    "   vec3 diffuseColor = finalColor * (1.0 - finalMetallic);\n" +
                    "   vec3 specularColor = finalColor * finalMetallic;\n" +
                    "   vec3 diffuseLight = ambientLight, specularLight = vec3(0.0);\n" +
                    "   bool hasSpecular = dot(specularColor,vec3(1.0)) > 0.001;\n" +
                    "   bool hasDiffuse = dot(diffuseColor,vec3(1.0)) > 0.001;\n" +
                    "   if(hasDiffuse || hasSpecular){\n" +
                    specularBRDFv2NoDivInlined2Start +
                    "       for(int i=0;i<numberOfLights;i++){\n" +
                    "           mat4x3 WStoLightSpace = invLightMatrices[i];\n" +
                    "           vec3 dir = invLightMatrices[i] * vec4(finalPosition,1.0);\n" + // local coordinates for falloff
                    // "       if(!hasSpecular && dot(dir,dir) >= 1.0) continue;\n" +
                    "           vec4 data0 = lightData0[i];\n" + // color, type
                    "           vec4 data1 = lightData1[i];\n" + // point: position, radius, spot: position, angle
                    "           vec4 data2 = shadowData[i];\n" +
                    "           vec3 lightColor = data0.rgb;\n" +
                    "           int lightType = int(data0.a);\n" +
                    "           vec3 lightPosition, lightDirWS, localNormal, effectiveSpecular, effectiveDiffuse;\n" +
                    "           lightDirWS = effectiveDiffuse = effectiveSpecular = vec3(0.0);\n" + // making Nvidia GPUs happy
                    "           localNormal = normalize(mat3x3(WStoLightSpace) * finalNormal);\n" +
                    "           float NdotL = 0.0;\n" + // normal dot light
                    "           int shadowMapIdx0 = int(data2.r);\n" +
                    "           int shadowMapIdx1 = int(data2.g);\n" +
                    // local coordinates of the point in the light "cone"
                    "           switch(lightType){\n" +
                    LightType.values().joinToString("") {
                        val core = when (it) {
                            LightType.DIRECTIONAL -> DirectionalLight
                                .getShaderCode("continue", true)
                            LightType.POINT -> PointLight
                                .getShaderCode("continue", true, hasLightRadius = true)
                            LightType.SPOT -> SpotLight
                                .getShaderCode("continue", true)
                        }
                        if (it != LightType.values().last()) {
                            "case ${it.id}:\n${core}break;\n"
                        } else {
                            "default:\n${core}break;\n"
                        }
                    } +
                    "           }\n" +
                    "           if(hasSpecular && dot(effectiveSpecular, vec3(NdotL)) > ${0.5 / 255.0}){\n" +
                    "               vec3 H = normalize(V + lightDirWS);\n" +
                    specularBRDFv2NoDivInlined2 +
                    "               specularLight += effectiveSpecular * computeSpecularBRDF;\n" +
                    "           }\n" +
                    // translucency; looks good and approximately correct
                    // sheen is a fresnel effect, which adds light
                    "           NdotL = mix(NdotL, 0.23, finalTranslucency) + finalSheen;\n" +
                    "           diffuseLight += effectiveDiffuse * clamp(NdotL, 0.0, 1.0);\n" +
                    "       }\n" +
                    specularBRDFv2NoDivInlined2End +
                    "   }\n" +
                    "   finalColor = diffuseColor * diffuseLight + specularLight;\n" +
                    "   finalColor = finalColor * (1.0 - finalOcclusion) + finalEmissive;\n" +
                    "#endif\n" +
                    "   if(applyToneMapping) finalColor = tonemap(finalColor);\n" +
                    "   finalResult = vec4(finalColor, finalAlpha);\n"
            ).add(noiseFunc).add(tonemapGLSL)
        }
    }

    val frontBackRenderer = SimpleRenderer(
        "front-back", ShaderStage(
            "front-back", listOf(
                Variable(GLSLType.V3F, "finalNormal"),
                Variable(GLSLType.V4F, "finalResult", VariableMode.OUT),
            ), "finalResult = vec4(" +
                    "   (gl_FrontFacing ? vec3(0.0,0.3,1.0) : vec3(1.0,0.0,0.0)) * " +
                    "   (finalNormal.x * 0.4 + 0.6), 1.0);\n" // some simple shading
        )
    )

    // pbr rendering with a few fake lights (which have no falloff)
    val previewRenderer = object : Renderer("preview") {

        val previewLights = listOf(
            // direction, strength
            Vector4f(-.5f, +1f, .5f, 5f),
            Vector4f(1f, 1f, 0f, 2f),
            Vector4f(0f, 0f, 1f, 1f)
        )

        val tmpDefaultUniforms = ByteBufferPool
            .allocateDirect(previewLights.size * 4 * 4)
            .asFloatBuffer()

        override fun uploadDefaultUniforms(shader: Shader) {
            super.uploadDefaultUniforms(shader)
            GFX.check()
            shader.use()
            val uniform = shader["lightData"]
            if (uniform >= 0) {
                tmpDefaultUniforms.position(0)
                for (data in previewLights) {
                    val f = length(data.x, data.y, data.z)
                    tmpDefaultUniforms.put(data.x / f)
                    tmpDefaultUniforms.put(data.y / f)
                    tmpDefaultUniforms.put(data.z / f)
                    tmpDefaultUniforms.put(data.w)
                }
                tmpDefaultUniforms.flip()
                shader.v4Array(uniform, tmpDefaultUniforms)
                GFX.check()
            }
        }

        override fun getPostProcessing(): ShaderStage {
            return ShaderStage(
                "previewRenderer", listOf(
                    Variable(GLSLType.V4F, "lightData", previewLights.size),
                    Variable(GLSLType.V3F, "finalColor"),
                    Variable(GLSLType.V1F, "finalAlpha"),
                    Variable(GLSLType.V3F, "finalPosition"),
                    Variable(GLSLType.V1F, "finalRoughness", VariableMode.INOUT),
                    Variable(GLSLType.V1F, "finalMetallic", VariableMode.INOUT),
                    Variable(GLSLType.V1F, "finalSheen"),
                    Variable(GLSLType.V3F, "finalSheenNormal"),
                    Variable(GLSLType.V4F, "finalClearCoat"),
                    Variable(GLSLType.V2F, "finalClearCoatRoughMetallic"),
                    Variable(GLSLType.V3F, "finalNormal"),
                    Variable(GLSLType.V3F, "finalEmissive"),
                    Variable(GLSLType.V1F, "finalOcclusion"),
                    Variable(GLSLType.V4F, "finalResult", VariableMode.OUT)
                ), "" +
                        // shared pbr data
                        "vec3 V = normalize(-finalPosition);\n" +
                        // light calculations
                        "float NdotV = abs(dot(finalNormal,V));\n" +
                        // precalculate sheen
                        "float sheenFresnel = 1.0 - abs(dot(finalSheenNormal,V));\n" +
                        "float sheen = finalSheen * pow(sheenFresnel, 3.0);\n" +
                        // light calculation
                        "vec3 ambientLight = vec3(0.2);\n" +
                        "vec3 diffuseLight = ambientLight, specularLight = vec3(0.0);\n" +
                        "vec3 diffuseColor  = finalColor * (1.0 - finalMetallic);\n" +
                        "vec3 specularColor = finalColor * finalMetallic;\n" +
                        "bool hasSpecular = dot(specularColor, vec3(1.0)) > 0.0;\n" +
                        specularBRDFv2NoDivInlined2Start +
                        "for(int i=0;i<${previewLights.size};i++){\n" +
                        "   vec4 data = lightData[i];\n" +
                        "   vec3 lightDirection = data.xyz, lightColor = vec3(data.w);\n" +
                        "   float NdotL = dot(finalNormal, lightDirection);\n" +
                        "   if(NdotL > 0.0){\n" +
                        "       vec3 H = normalize(V + lightDirection);\n" +
                        "       if(hasSpecular){\n" +
                        specularBRDFv2NoDivInlined2 +
                        "           specularLight += lightColor * computeSpecularBRDF;\n" +
                        "       }\n" +
                        "       diffuseLight += lightColor * NdotL;\n" +
                        "   }\n" +
                        "}\n" +
                        specularBRDFv2NoDivInlined2End +
                        "finalColor = diffuseColor * diffuseLight + specularLight;\n" +
                        "finalColor = finalColor * (1.0 - finalOcclusion) + finalEmissive;\n" +
                        "finalColor = tonemap(finalColor);\n" +
                        "finalResult = vec4(finalColor, finalAlpha);\n"
            ).add(noiseFunc).add(tonemapGLSL)
        }
    }

    val simpleNormalRenderer = object : Renderer("simple-color") {
        override fun getPostProcessing(): ShaderStage {
            return ShaderStage(
                "uiRenderer",
                listOf(
                    Variable(GLSLType.V3F, "finalColor"),
                    Variable(GLSLType.V1F, "finalAlpha"),
                    Variable(GLSLType.V3F, "finalNormal"),
                    Variable(GLSLType.V3F, "finalEmissive"),
                    Variable(GLSLType.V4F, "finalResult", VariableMode.OUT)
                ),
                "finalResult = vec4((finalColor * (0.6 - 0.4 * normalize(finalNormal).x)) + finalEmissive, finalAlpha);\n"
            )
        }
    }

    val attributeRenderers = LazyMap({ type: DeferredLayerType ->
        val variables = listOf(
            Variable(DeferredSettingsV2.glslTypes[type.dimensions - 1], type.glslName, VariableMode.IN),
            Variable(GLSLType.V4F, "finalResult", VariableMode.OUT)
        )
        val shaderCode = when (type) {
            DeferredLayerType.MOTION -> "" +
                    "finalResult = vec4(${type.glslName}${type.map01}, 1.0);" +
                    "finalResult.rgb *= 10.0 / (1.0 + abs(finalColor));\n" +
                    "finalResult.rgb += 0.5;\n"
            else -> {
                "finalResult = ${
                    when (type.dimensions) {
                        1 -> "vec4(vec3(${type.glslName}${type.map01}),1.0)"
                        2 -> "vec4(${type.glslName}${type.map01},1.0,1.0)"
                        3 -> "vec4(${type.glslName}${type.map01},1.0)"
                        4 -> "vec4(${type.glslName}${type.map01})"
                        else -> ""
                    }
                };\n" + if (type.highDynamicRange) {
                    "finalResult.rgb /= 1.0 + max(max(abs(finalColor).x,abs(finalColor).y),abs(finalColor).z);\n"
                } else ""
            }
        }
        val name = type.name
        val stage = ShaderStage(name, variables, shaderCode)
        SimpleRenderer(name, stage)
    }, DeferredLayerType.values.size)

    val rawAttributeRenderers = LazyMap({ type: DeferredLayerType ->
        val variables = listOf(
            Variable(DeferredSettingsV2.glslTypes[type.dimensions - 1], type.glslName, VariableMode.IN),
            Variable(GLSLType.V4F, "finalResult", VariableMode.OUT)
        )
        val shaderCode = "" +
                "finalResult = ${
                    when (type.dimensions) {
                        1 -> "vec4(vec3(${type.glslName}${type.map01}),1.0)"
                        2 -> "vec4(${type.glslName}${type.map01},1.0,1.0)"
                        3 -> "vec4(${type.glslName}${type.map01},1.0)"
                        4 -> "(${type.glslName}${type.map01})"
                        else -> ""
                    }
                };\n"
        val name = type.name
        val stage = ShaderStage(name, variables, shaderCode)
        SimpleRenderer(name, stage)
    }, DeferredLayerType.values.size)

    val attributeEffects: Map<Pair<DeferredLayerType, DeferredSettingsV2>, CameraEffect> =
        LazyMap({ (type, settings) ->
            val layer = settings.findLayer(type)
            if (layer != null) {
                val type2 = GLSLType.floats[type.dimensions - 1].glslName
                val shader = Shader(
                    type.name, coordsList, coordsVShader, uvList, listOf(
                        Variable(GLSLType.S2D, "source"),
                        Variable(GLSLType.V4F, "result", VariableMode.OUT)
                    ), "" +
                            "void main(){\n" +
                            "   $type2 data = texture(source,uv).${layer.mapping}${type.map01};\n" +
                            "   vec3 color = " +
                            when (type.dimensions) {
                                1 -> "vec3(data)"
                                2 -> "vec3(data,0.0)"
                                3 -> "data"
                                else -> "data.rgb;\n"
                            } + ";\n" +
                            (if (type.highDynamicRange) {
                                "color /= (1.0+abs(color));\n"
                            } else "") +
                            (if (type == DeferredLayerType.MOTION) {
                                "color += 0.5;\n"
                            } else "") +
                            "   result = vec4(color, 1.0);\n" +
                            "}"
                )
                object : CameraEffect() {
                    override fun listInputs() = listOf(type)
                    override fun clone() = throw NotImplementedError()
                    override fun render(
                        buffer: IFramebuffer,
                        format: DeferredSettingsV2,
                        layers: MutableMap<DeferredLayerType, IFramebuffer>
                    ) {
                        shader.use()
                        layers[type]!!.getTexture0()
                            .bindTrulyNearest(0)
                        flat01.draw(shader)
                    }
                }
            } else null
        }, DeferredLayerType.values.size)

    val MAX_PLANAR_LIGHTS = 8
    val MAX_CUBEMAP_LIGHTS = 8

}