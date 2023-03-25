package me.anno.gpu.deferred

import me.anno.gpu.GFX
import me.anno.gpu.framebuffer.*
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.RandomEffect.randomFunc
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.builder.ShaderBuilder
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.texture.ITexture2D
import me.anno.utils.structures.lists.Lists.firstOrNull2
import org.joml.Vector4f
import kotlin.math.max

data class DeferredSettingsV2(
    val layerTypes: List<DeferredLayerType>,
    val samples: Int,
    val fpLights: Boolean
) {

    class Layer(val type: DeferredLayerType, val textureName: String, val index: Int, val mapping: String) {

        fun appendMapping(
            fragment: StringBuilder,
            suffix: String,
            uv: String,
            imported: MutableSet<String>,
            sampleVariableName: String?
        ) {
            if (imported.add(textureName)) {
                if (sampleVariableName != null) {
                    fragment.append("vec4 ").append(textureName).append(suffix)
                    fragment.append(" = texelFetch(").append(textureName)
                    fragment.append(", ivec2(textureSize(")
                        .append(textureName) // texture will be sampler2DMS, so no lod is used as parameter
                    fragment.append(")*").append(uv)
                    fragment.append("), ").append(sampleVariableName).append(");\n")
                } else {
                    fragment.append("vec4 ").append(textureName).append(suffix)
                    fragment.append(" = texture(").append(textureName)
                    fragment.append(", ").append(uv).append(");\n")
                }
            }
            fragment.append(glslTypes[type.dimensions - 1])
            fragment.append(' ')
            fragment.append(type.glslName)
            fragment.append(" = ")
            fragment.append(textureName)
            fragment.append(suffix)
            fragment.append('.')
            fragment.append(mapping)
            fragment.append(type.map10)
            fragment.append(";\n")
        }

        fun appendLayer(output: StringBuilder) {
            output.append(textureName)
            output.append('.')
            output.append(mapping)
            output.append(" = (")
            output.append(type.glslName)
            output.append(type.map01)
            // append random rounding
            output.append(")*(1.0+defRR*").append(textureName).append("RR.x")
            output.append(")+defRR*").append(textureName).append("RR.y")
            output.append(";\n")
        }

    }

    val layers = ArrayList<Layer>()
    val layers2: ArrayList<DeferredLayer>
    val emptySlots = ArrayList<Pair<String, String>>()

    init {

        val maxTextures = layerTypes.size
        val spaceInLayers = IntArray(maxTextures)
        spaceInLayers.fill(4)

        val needsHighPrecision = Array(maxTextures) { BufferQuality.LOW_8 }
        var usedTextures0 = -1

        for (layerType in layerTypes) {
            val dimensions = layerType.dimensions
            val layerIndex = spaceInLayers.indexOfFirst { it >= dimensions }
            val startIndex = 4 - spaceInLayers[layerIndex]
            val mapping = "rgba".substring(startIndex, startIndex + dimensions)
            val layer = Layer(layerType, "defLayer$layerIndex", layerIndex, mapping)
            layers.add(layer)
            spaceInLayers[layerIndex] -= dimensions
            usedTextures0 = max(usedTextures0, layerIndex)
            val op = needsHighPrecision[layerIndex]
            val np = layerType.minimumQuality
            needsHighPrecision[layerIndex] = if (op > np) op else np
        }
        usedTextures0++

        layers2 = ArrayList(usedTextures0)
        for (layerIndex in 0 until usedTextures0) {
            val layer2 = DeferredLayer(
                "defLayer$layerIndex", when (needsHighPrecision[layerIndex]) {
                    BufferQuality.LOW_8 -> TargetType.UByteTarget4
                    BufferQuality.MEDIUM_12 -> TargetType.Normal12Target4
                    BufferQuality.HIGH_16 -> TargetType.FP16Target4
                    else -> TargetType.FloatTarget4
                }
            )
            layers2.add(layer2)
            when (spaceInLayers[layerIndex]) {
                1 -> emptySlots += Pair(layer2.name, "a")
                2 -> emptySlots += Pair(layer2.name, "ba")
                3 -> emptySlots += Pair(layer2.name, "gba")
            }
        }

    }

    val settingsV1 = DeferredSettingsV1(layers2, fpLights)
    val layers1 = Array(layers2.size) { layers2[it].type }

    fun createBaseBuffer(): IFramebuffer {
        val layers = layers2
        val name = "DeferredBuffers-main"
        val depthBufferType = DepthBufferType.TEXTURE
        return if (layers.size <= GFX.maxColorAttachments) {
            Framebuffer(
                name, 1, 1, samples,
                layers1, depthBufferType
            )
        } else {
            MultiFramebuffer(
                name, 1, 1, samples,
                layers1, depthBufferType
            )
        }
    }

    fun createShader(
        shaderName: String,
        instanced: Boolean,
        vertexVariables: List<Variable>,
        vertexShader: String,
        varyings: List<Variable>,
        fragmentVariables: List<Variable>,
        fragmentShader: String,
        textures: List<String>?,
        postProcessing: ShaderStage?
    ): Shader {
        val vertex = if (instanced) "#define INSTANCED;\n$vertexShader" else vertexShader
        val builder = ShaderBuilder(shaderName, this)
        builder.addVertex(ShaderStage(vertexVariables + varyings, vertex))
        builder.addFragment(ShaderStage(fragmentVariables + varyings, fragmentShader))
        builder.addFragment(postProcessing)
        val shader = builder.create()
        shader.setTextureIndices(textures)
        return shader
    }

    fun appendLayerDeclarators(output: StringBuilder) {
        for ((index, type) in settingsV1.layers.withIndex()) {
            output.append("layout (location = ")
            output.append(index)
            output.append(") out vec4 ")
            output.append(type.name)
            output.append(";\n")
            output.append("uniform vec2 ").append(type.name).append("RR;\n")
        }
    }

    fun appendLayerWriters(output: StringBuilder) {
        output.append(randomFunc)
        output.append("float defRR = GET_RANDOM(0.001 * gl_FragCoord)-0.5;\n")
        for (layer in layers) {
            layer.appendLayer(output)
        }
        for ((name, map) in emptySlots) {
            val value = when (map.length) {
                1 -> " = finalAlpha;\n"
                2 -> " = vec2(0.0,finalAlpha);\n"
                else -> " = vec3(0.0,0.0,finalAlpha);\n"
            }
            output.append(name).append('.').append(map).append(value)
        }
    }

    fun findLayer(type: DeferredLayerType): Layer? {
        return layers.firstOrNull2 { it.type == type }
    }

    fun findMapping(type: DeferredLayerType): String? {
        return findLayer(type)?.mapping
    }

    fun findTexture(buffer: IFramebuffer, type: DeferredLayerType): ITexture2D? {
        val layer = layers.firstOrNull2 { it.type == type } ?: return null
        return findTexture(buffer, layer)
    }

    fun findTextureMS(buffer: IFramebuffer, type: DeferredLayerType): ITexture2D? {
        val layer = layers.firstOrNull2 { it.type == type } ?: return null
        return findTextureMS(buffer, layer)
    }

    fun findTexture(buffer: IFramebuffer, layer: Layer): ITexture2D {
        return buffer.getTextureI(layer.index)
    }

    fun findTextureMS(buffer: IFramebuffer, layer: Layer): ITexture2D {
        return buffer.getTextureIMS(layer.index)
    }

    fun split(index: Int, splitSize: Int): DeferredSettingsV2 {
        val index0 = index * splitSize
        val index1 = index0 + splitSize
        return DeferredSettingsV2(
            layerTypes.filter { type ->
                findLayer(type)!!.index in index0 until index1
            }, samples,
            settingsV1.fpLights
        )
    }

    companion object {

        val glslTypes = arrayOf(GLSLType.V1F, GLSLType.V2F, GLSLType.V3F, GLSLType.V4F)

        val singleToVector = mapOf(
            "r" to Vector4f(1f, 0f, 0f, 0f),
            "g" to Vector4f(0f, 1f, 0f, 0f),
            "b" to Vector4f(0f, 0f, 1f, 0f),
            "a" to Vector4f(0f, 0f, 0f, 1f)
        )

    }

}