package me.anno.ecs.components.mesh.decal

import me.anno.ecs.components.mesh.decal.DecalMaterial.Companion.sett
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.gpu.deferred.DeferredLayerType
import me.anno.gpu.deferred.DeferredSettingsV2
import me.anno.gpu.shader.DepthTransforms.depthToPosition
import me.anno.gpu.shader.DepthTransforms.depthVars
import me.anno.gpu.shader.DepthTransforms.rawToDepth
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.GLSLType.Companion.floats
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.ShaderLib.matMul
import me.anno.gpu.shader.ShaderLib.quatRot
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.utils.structures.lists.Lists.any2
import java.util.*

class DecalShader(val layers: ArrayList<DeferredLayerType>) : ECSMeshShader("decal") {
    override fun createFragmentStages(flags: Int): List<ShaderStage> {
        val sett = sett ?: return super.createFragmentStages(flags)
        val loadPart2 = StringBuilder()
        val variables = ArrayList(depthVars)
        for (layer in sett.layers2) {
            variables.add(Variable(GLSLType.S2D, layer.name + "_in0"))
        }
        variables.addAll(
            listOf(
                Variable(GLSLType.S2D, "depth_in0"),
                Variable(GLSLType.M4x3, "invLocalTransform"),
                Variable(GLSLType.V2F, "windowSize"),
                Variable(GLSLType.V2F, "uv", VariableMode.OUT),
                Variable(GLSLType.V3F, "finalPosition", VariableMode.OUT),
                Variable(GLSLType.V3F, "localPosition", VariableMode.OUT),
                Variable(GLSLType.V1F, "alphaMultiplier", VariableMode.OUT),
            )
        )
        for (layer in sett.layers) {
            variables.add(Variable(floats[layer.type.workDims - 1], "${layer.type.glslName}_in2", VariableMode.OUT))
            layer.appendMapping(loadPart2, "_in2", "_in1", "_in0", "", null, null)
        }
        val original = super.createFragmentStages(flags)
        // can a decal modify the depth? it shouldn't ...
        return listOf(
            // inputs
            ShaderStage(
                "inputs", variables, "" +
                        "ivec2 uvz = ivec2(gl_FragCoord.xy);\n" +
                        // load all data
                        sett.layers2.joinToString("") {
                            "vec4 ${it.name}_in1 = texelFetch(${it.name}_in0, uvz, 0);\n"
                        } + loadPart2.toString() +
                        "finalPosition = rawDepthToPosition(gl_FragCoord.xy/windowSize, texelFetch(depth_in0, uvz, 0).x);\n" +
                        "localPosition = matMul(invLocalTransform, vec4(finalPosition, 1.0));\n" +
                        "uv = localPosition.xy * vec2(0.5,-0.5) + 0.5;\n" +
                        // automatic blending on edges? alpha should be zero there anyway
                        "alphaMultiplier = abs(uv.x-0.5) < 0.5 && abs(uv.y-0.5) < 0.5 ? max(1.0-abs(localPosition.z), 0.0) : 0.0;\n" +
                        "alphaMultiplier *= -dot(normal, finalNormal_in2);\n" +
                        "if(alphaMultiplier < 0.5/255.0) discard;\n"
            ).add(quatRot).add(rawToDepth).add(depthToPosition).add(ShaderLib.octNormalPacking),
        ) + original + listOf(
            ShaderStage(
                "effect-modulator", listOf(
                    Variable(GLSLType.V1F, "alphaMultiplier", VariableMode.INOUT),
                    Variable(GLSLType.V1F, "finalAlpha", VariableMode.INOUT)
                ), "" +
                        "finalAlpha *= alphaMultiplier;\n" +
                        "if(finalAlpha < 0.5/255.0) discard;\n" +
                        // blend all values with the loaded properties
                        layers.joinToString("") {
                            "${it.glslName} = mix(${it.glslName}_in2, ${it.glslName}, finalAlpha);\n"
                        } +
                        // for all other values, override them completely with the loaded values
                        // todo normal map needs to be applied properly on existing normal, not just copied
                        sett.layerTypes
                            .filter {
                                it !in layers && original.any2 { stage ->
                                    stage.variables.any2 { variable ->
                                        variable.name == it.glslName
                                    }
                                }
                            }
                            .joinToString("") {
                                "${it.glslName} = ${it.glslName}_in2;\n"
                            } +
                        "finalAlpha = 1.0;\n"
            )
        )
    }

    // forward shader isn't really supported

    fun getDisabledLayers(): BitSet? {
        val settings = sett ?: return null
        val disabled = BitSet(settings.layers2.size)
        for (i in settings.layers2.indices) disabled.set(i, true)
        for (layer in layers) {
            val layer1 = settings.findLayer(layer) ?: continue
            disabled.set(layer1.texIndex, false)
        }
        return disabled
    }

    override fun createDeferredShader(deferred: DeferredSettingsV2, flags: Int, postProcessing: ShaderStage?): Shader {
        val base = createBase(flags, postProcessing)
        base.outputs = deferred
        base.disabledLayers = getDisabledLayers()
        // build & finish
        val shader = base.create("dcl$flags")
        finish(shader)
        return shader
    }
}