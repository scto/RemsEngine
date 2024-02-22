package me.anno.tests.engine.material

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.material.Material
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.mesh.shapes.PlaneModel
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.engine.ui.render.RendererLib
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.gpu.pipeline.PipelineStageImpl.Companion.TRANSPARENT_PASS
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.GPUShader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.maths.Maths.hasFlag
import me.anno.mesh.Shapes.flatCube
import me.anno.utils.OS.documents
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI

// todo support flat texture format, too
//  https://harpingtonbear.artstation.com/projects/581r9O

class ParallaxMaterial : Material() {
    init {
        shader = ParallaxRoomShader
    }

    var roomDepth = Vector4f(0.1f, 0.4f, 0.7f, 1f)
        set(value) {
            field.set(value)
        }

    override fun bind(shader: GPUShader) {
        super.bind(shader)
        shader.v4f("roomDepth", roomDepth)
    }
}

object ParallaxRoomShader : ECSMeshShader("ParallaxRoom") {
    // test alpha against front
    //  - if passes through, find collision with 2nd, 3rd and 4th wall
    //  - color is compressed HDR... -> emissive
    override fun createFragmentStages(key: ShaderKey): List<ShaderStage> {
        return key.vertexData.onFragmentShader + listOf(
            ShaderStage(
                "material",
                createFragmentVariables(key) + listOf(
                    Variable(GLSLType.V4F, "roomDepth"),
                    Variable(GLSLType.V2F, "uv", VariableMode.INOUT)
                ),
                concatDefines(key).toString() +
                        discardByCullingPlane +
                        // step by step define all material properties
                        (if (key.flags.hasFlag(NEEDS_COLORS)) {
                            "" +
                                    normalTanBitanCalculation +
                                    "uv = fract(uv);\n" +
                                    "vec4 color = texture(diffuseMap, vec2(0.0,2.0/3.0) + uv/3.0);\n" +
                                    "if(color.a < 1.0){\n" +
                                    // continue traversal
                                    "   vec3 pos = vec3(uv,0.0), pos0=pos;\n" +
                                    // find camera direction, and transform it into uv-space
                                    "   mat3 tbn = mat3(finalTangent, finalBitangent, finalNormal);\n" +
                                    "   vec3 dir = matMul(normalize(finalPosition), tbn);\n" +
                                    "   dir = vec3(dir.x,-dir.yz);\n" +
                                    // find the closest hit
                                    "   float distX = dir.x < 0.0 ? -pos.x/dir.x : (1.0-pos.x)/dir.x;\n" +
                                    "   float distY = dir.y < 0.0 ? -pos.y/dir.y : (1.0-pos.y)/dir.y;\n" +
                                    "   float invZ = 1.0/dir.z;\n" +
                                    "   float distZ = roomDepth.x*invZ;\n" +
                                    "   if(distZ < min(distX,distY)){\n" +
                                    "       pos = pos0 + dir * distZ;\n" +
                                    "       color = blendColor(color, texture(diffuseMap, pos.xy/3.0));\n" +
                                    "   }\n" +
                                    "   distZ = roomDepth.y*invZ;\n" +
                                    "   if(color.a < 1.0 && distZ < min(distX,distY)){\n" +
                                    "       pos = pos0 + dir * distZ;\n" +
                                    "       color = blendColor(color, texture(diffuseMap, vec2(2.0/3.0,0.0) + pos.xy/3.0));\n" +
                                    "   }\n" +
                                    "   distZ = roomDepth.z*invZ;\n" +
                                    "   if(color.a < 1.0 && distZ < min(distX,distY)){\n" +
                                    "       pos = pos0 + dir * distZ;\n" +
                                    "       color = blendColor(color, texture(diffuseMap, vec2(2.0/3.0) + pos.xy/3.0));\n" +
                                    "   }\n" +
                                    "   distZ = roomDepth.w*invZ;\n" +
                                    "   if(color.a < 1.0){\n" +
                                    "       vec4 color1;\n" +
                                    "       if(distX < min(distY,distZ)){\n" +
                                    "           pos = pos0 + dir * distX;\n" +
                                    "           pos.z /= roomDepth.w;\n" +
                                    "           color1 = texture(diffuseMap, vec2(dir.x < 0.0 ? 0.0 : 2.0/3.0,1.0/3.0) + pos.zy/3.0);\n" +
                                    "       } else if(distY < distZ){\n" +
                                    "           pos = pos0 + dir * distY;\n" +
                                    "           pos.z /= roomDepth.w;\n" +
                                    "           color1 = texture(diffuseMap, vec2(1.0/3.0,dir.y < 0.0 ? 0.0 : 2.0/3.0) + pos.xz/3.0);\n" +
                                    "       } else {\n" +
                                    "           pos = pos0 + dir * distZ;\n" +
                                    "           color1 = texture(diffuseMap, vec2(1.0/3.0,1.0/3.0) + pos.xy/3.0);\n" +
                                    "       }\n" +
                                    "       color = blendColor(color,color1);\n" +
                                    "   }\n" +
                                    "};\n" +
                                    "finalColor = vec3(0.0);\n" +
                                    "finalAlpha = diffuseBase.a;\n" +
                                    // normalMapCalculation +
                                    // emissiveCalculation +
                                    "finalEmissive = color.rgb/(1.05-max(color.r,max(color.g,color.b)));\n" +
                                    // occlusionCalculation +
                                    metallicCalculation +
                                    roughnessCalculation +
                                    v0 + // sheenCalculation +
                                    // clearCoatCalculation +
                                    reflectionCalculation
                        } else "") +
                        finalMotionCalculation
            ).add(ShaderLib.quatRot).add(ShaderLib.brightness)
                // .add(ShaderLib.parallaxMapping)
                .add(ShaderLib.blendColor)
                .add(RendererLib.getReflectivity)
        )
    }
}

object PRGlassShader : ECSMeshShader("ParallaxRoom-Glass") {
    override fun createFragmentStages(key: ShaderKey): List<ShaderStage> {
        return key.vertexData.onFragmentShader + listOf(
            ShaderStage(
                "material",
                createFragmentVariables(key) + listOf(Variable(GLSLType.V4F, "roomDepth")),
                concatDefines(key).toString() +
                        discardByCullingPlane +
                        // step by step define all material properties
                        (if (key.flags.hasFlag(NEEDS_COLORS)) {
                            "" +
                                    normalTanBitanCalculation +
                                    "vec4 color = texture(diffuseMap, vec2(0.0,2.0/3.0) + fract(uv)/3.0);\n" +
                                    "finalColor = vec3(1.0);\n" +
                                    "finalAlpha = (1.0-color.a) * diffuseBase.a;\n" +
                                    normalMapCalculation +
                                    metallicCalculation +
                                    roughnessCalculation +
                                    v0 + reflectionCalculation
                        } else "") +
                        finalMotionCalculation
            ).add(ShaderLib.quatRot).add(ShaderLib.brightness)
                // .add(ShaderLib.parallaxMapping)
                .add(ShaderLib.blendColor)
        )
    }
}

/**
 * create Parallax Room / Parallax Interior Mapping material like on
 *  - https://wparallax.com/
 *  - in some current Spider-Man game
 *  - in some Unreal Engine tutorials
 *
 *  the texture looks like a cubemap
 *  1. +y 2.
 *  -x 4. +x
 *  0. -y 3.
 *  0.-4. are the wall-aligned layers, which are assigned roomDepth.0xyzw
 * */
fun main() {

    val xi = 3
    val yi = 15
    val pi = 0.1f
    val ph = yi * 2f / 2f

    val window = PlaneModel.createPlane(
        1, 1, Vector3f(),
        Vector3f(1f, 0f, 0f), Vector3f(0f, -ph, 0f)
    )
    window.uvs!!.apply { // scale u's
        for (i in indices step 2) {
            this[i + 1] *= yi.toFloat()
        }
    }
    window.indices = window.indices!! + window.indices!!
    window.materialIds = intArrayOf(0, 0, 1, 1)
    window.materials = listOf(ParallaxMaterial().apply {
        diffuseMap = documents.getChild("ParallaxRoom.png")
        roomDepth.set(0.05f, 0.2f, 0.5f, 1f)
    }.ref, Material().apply {
        diffuseMap = documents.getChild("ParallaxRoom.png")
        pipelineStage = TRANSPARENT_PASS
        shader = PRGlassShader
    }.ref)

    // create a little skyscraper :)
    val scene = Entity()
    val pillar = flatCube.scaled(Vector3f(pi, ph, pi)).front
    fun add(pos: Vector3d, mesh: Mesh, y: Int): Entity {
        val entity = Entity(scene)
        entity.position = pos
        entity.rotation = entity.rotation.rotateY(y * PI / 2)
        entity.add(MeshComponent(mesh))
        return entity
    }
    pillar.material = Material().apply {
        diffuseBase.set(0.135f, 0.135f, 0.135f, 1f)
    }.ref

    val sp = 2.0 * (1.0 + pi)
    for (r in 0 until 4) {
        for (x in -xi..xi) {
            val z = (xi + 0.5) * sp
            add(
                Vector3d(x * sp, ph.toDouble(), z)
                    .rotateY(r * PI / 2), window, r
            )
            add(
                Vector3d((x - 0.5) * sp, ph.toDouble(), z)
                    .rotateY(r * PI / 2), pillar, r
            )
        }
    }

    // roof
    add(
        Vector3d(0.0, ph * 2.0, 0.0),
        PlaneModel.createPlane().apply {
            material = pillar.material
        }, 0
    ).setScale(sp * (xi + 0.5))

    // street
    add(
        Vector3d(),
        PlaneModel.createPlane().apply {
            material = Material().apply {
                diffuseBase.set(0.3f, 0.3f, 0.3f, 1f)
            }.ref
        }, 0
    ).setScale(1.5 * sp * (xi + 0.5))

    testSceneWithUI("Parallax Room", scene)
}