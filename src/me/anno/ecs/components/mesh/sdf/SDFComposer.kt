package me.anno.ecs.components.mesh.sdf

import me.anno.ecs.components.cache.MaterialCache
import me.anno.ecs.components.mesh.Mesh.Companion.defaultMaterial
import me.anno.ecs.components.mesh.TypeValue
import me.anno.ecs.components.mesh.TypeValueV2
import me.anno.ecs.components.mesh.sdf.SDFComponent.Companion.appendUniform
import me.anno.ecs.components.mesh.sdf.SDFComponent.Companion.defineUniform
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.engine.ui.render.RenderView
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.shader.builder.Function
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.max

/**
 * builds shaders for signed distance function rendering
 * */
object SDFComposer {

    const val dot2 = "" +
            "float dot2(vec2 v){ return dot(v,v); }\n" +
            "float dot2(vec3 v){ return dot(v,v); }\n"

    // from https://www.shadertoy.com/view/Xds3zN, Inigo Quilez
    const val raycasting = "" +
            // input: vec3 ray origin, vec3 ray direction
            // output: vec2(distance, materialId)
            "vec2 raycast(in vec3 ro, in vec3 rd){\n" +
            // ray marching
            "   vec2 res = vec2(-1.0);\n" +
            "   float tMin = distanceBounds.x, tMax = distanceBounds.y;\n" +
            "   float t = tMin;\n" +
            "   for(int i=0; i<maxSteps && t<tMax; i++){\n" +
            "     vec2 h = map(ro+rd*t);\n" +
            "     if(abs(h.x)<(sdfMaxRelativeError*t)){\n" + // allowed error grows with distance
            "       res = vec2(t,h.y);\n" +
            "       break;\n" +
            "     }\n" +
            // sdfReliability: sometimes, we need more steps, because the sdf is not reliable
            "     t += sdfReliability * h.x;\n" +
            "   }\n" +
            "   return res;\n" +
            "}\n"

    // http://iquilezles.org/www/articles/normalsSDF/normalsSDF.htm
    const val normal = "" +
            "uniform int iFrame;\n" +
            "#define ZERO (min(iFrame,0))\n" +
            "vec3 calcNormal(in vec3 pos, float epsilon) {\n" +
            // inspired by tdhooper and klems - a way to prevent the compiler from inlining map() 4 times
            "  vec3 n = vec3(0.0);\n" +
            "  for(int i=ZERO;i<4;i++) {\n" +
            // 0.5773 is just a scalar factor
            "      vec3 e = 2.0*vec3((((i+3)>>1)&1),((i>>1)&1),(i&1))-1.0;\n" +
            "      n += e*map(pos+e*epsilon).x;\n" +
            "  }\n" +
            "  return normalize(n);\n" +
            "}\n"

    fun createECSShader(tree: SDFComponent): Pair<HashMap<String, TypeValue>, ECSMeshShader> {
        // done compute approximate bounds on cpu, so we can save computations
        // done traverse with larger step size on normals? normals don't use traversal
        // done traverse over tree to assign material ids? no, the programmer does that to reduce cases
        val functions = LinkedHashSet<String>()
        val uniforms = HashMap<String, TypeValue>()
        val shapeDependentShader = StringBuilder()
        tree.buildShader(shapeDependentShader, 0, VariableCounter(1), "res", uniforms, functions)
        uniforms["localStart"] = object : TypeValue(GLSLType.V3F, Vector3f()) {
            override var value: Any
                get() {
                    val value = super.value as Vector3f
                    val dt = tree.transform?.drawTransform
                    if (dt != null) {
                        val cp = RenderView.camPosition
                        value.set(cp.x - dt.m30(), cp.y - dt.m31(), cp.z - dt.m32())
                    } else value.set(0f)
                    return value
                }
                set(_) = throw RuntimeException()
        }
        uniforms["sdfReliability"] = TypeValueV2(GLSLType.V1F) { tree.globalReliability }
        uniforms["sdfNormalEpsilon"] = TypeValue(GLSLType.V1F) { tree.normalEpsilon }
        uniforms["sdfMaxRelativeError"] = TypeValueV2(GLSLType.V1F) { tree.maxRelativeError }
        uniforms["maxSteps"] = TypeValueV2(GLSLType.V1I) { tree.maxSteps }
        uniforms["distanceBounds"] = TypeValue(GLSLType.V2F, Vector2f(0f, 1e5f))

        val materials = tree.sdfMaterials.map { MaterialCache[it] }
        val builder = StringBuilder(max(1, materials.size) * 128)

        val needsSwitch = materials.size > 1
        if (needsSwitch) builder
            .append("switch(clamp(int(ray.y),0,")
            .append(materials.lastIndex)
            .append(")){\n")
        for (index in 0 until max(materials.size, 1)) {
            if (needsSwitch) builder.append("case ").append(index).append(":\n")
            val material = materials.getOrNull(index) ?: defaultMaterial
            // todo support shading functions, textures and material interpolation
            // define all properties as uniforms, so they can be changed without recompilation
            val color = defineUniform(uniforms, material.diffuseBase)
            builder.append("finalColor = ").append(color).append(".xyz;\n")
            builder.append("finalAlpha = ").append(color).append(".w;\n")
            builder.append("finalMetallic = ").appendUniform(uniforms, material.metallicMinMax).append(".y;\n")
            builder.append("finalRoughness = ").appendUniform(uniforms, material.roughnessMinMax).append(".y;\n")
            builder.append("finalEmissive = ").appendUniform(uniforms, material.emissiveBase).append(";\n")
            if (needsSwitch) builder.append("break;\n")
        }
        if (needsSwitch) builder.append("}\n")

        val shader = object : ECSMeshShader("raycasting-${tree.hashCode()}") {
            override fun createFragmentStage(instanced: Boolean): ShaderStage {
                // instancing is not supported
                val fragmentVariables = listOf(
                    Variable(GLSLType.M4x4, "transform"),
                    Variable(GLSLType.M4x3, "localTransform"),
                    Variable(GLSLType.M3x3, "invLocalTransform"),
                    Variable(GLSLType.V1I, "maxSteps"),
                    Variable(GLSLType.V2F, "distanceBounds"), // todo compute them...
                    Variable(GLSLType.V3F, "localStart"),
                    Variable(GLSLType.V1F, "sdfReliability"),
                    Variable(GLSLType.V1F, "sdfNormalEpsilon"),
                    Variable(GLSLType.V1F, "sdfMaxRelativeError"),
                    // input varyings
                    Variable(GLSLType.V3F, "finalPosition"),
                    Variable(GLSLType.V2F, "roughnessMinMax"),
                    Variable(GLSLType.V2F, "metallicMinMax"),
                    Variable(GLSLType.V1F, "occlusionStrength"),
                    // outputs
                    Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
                    Variable(GLSLType.V3F, "finalPosition", VariableMode.OUT),
                    Variable(GLSLType.V3F, "finalNormal", VariableMode.OUT),
                    // Variable(GLSLType.V3F, "finalTangent", VariableMode.OUT),
                    // Variable(GLSLType.V3F, "finalBitangent", VariableMode.OUT),
                    Variable(GLSLType.V3F, "finalEmissive", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalMetallic", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalRoughness", VariableMode.OUT),
                    // we could compute occlusion
                    // disadvantage: we cannot prevent duplicate occlusion currently,
                    // and it would be applied twice... (here + ssao)
                    Variable(GLSLType.V1F, "finalSheen", VariableMode.OUT),
                    // just passed from uniforms
                    Variable(GLSLType.V1F, "finalTranslucency", VariableMode.INOUT),
                    Variable(GLSLType.V4F, "finalClearCoat", VariableMode.INOUT),
                    Variable(GLSLType.V2F, "finalClearCoatRoughMetallic", VariableMode.INOUT),
                    // for reflections;
                    // we could support multiple
                    Variable(GLSLType.BOOL, "hasReflectionPlane"),
                    Variable(GLSLType.V3F, "reflectionPlaneNormal"),
                    Variable(GLSLType.S2D, "reflectionPlane"),
                    Variable(GLSLType.V4F, "reflectionCullingPlane"),
                    Variable(GLSLType.V1F, "translucency"),
                    Variable(GLSLType.V1F, "sheen"),
                    Variable(GLSLType.V4F, "clearCoat"),
                    Variable(GLSLType.V2F, "clearCoatRoughMetallic"),
                    Variable(GLSLType.V1I, "drawMode"),
                    // todo support bridges for uniform -> fragment1 -> fragment2 (inout)
                    Variable(GLSLType.V4F, "tint", VariableMode.OUT),
                ) + uniforms.map { (k, v) -> Variable(v.type, k) }

                val stage = ShaderStage(
                    "material", fragmentVariables, "" +

                            // compute ray position & direction in local coordinates
                            // trace ray
                            // done materials
                            // convert tracing distance from local to global
                            // convert normals into global normals
                            // compute global depth

                            "vec3 globalDir = normalize(finalPosition);\n" +
                            "vec3 localDir = normalize(invLocalTransform * finalPosition);\n" +
                            "vec3 localPos = localStart;\n" +
                            "vec2 ray = map(localPos);\n" +
                            "if(ray.x >= 0.0){\n" + // not inside an object
                            "   ray = raycast(localPos, localDir);\n" +
                            "   if(ray.y < 0.0) discard;\n" + // sky
                            "   vec3 localHit = localPos + ray.x * localDir;\n" +
                            "   vec3 localNormal = calcNormal(localHit, sdfNormalEpsilon);\n" +
                            // convert localHit to global hit
                            "   finalPosition = localTransform * vec4(localHit, 1.0);\n" +
                            "   finalNormal = normalize(localTransform * vec4(localNormal, 0.0));\n" +

                            // respect reflection plane
                            "   if(dot(vec4(finalPosition, 1.0), reflectionCullingPlane) < 0.0) discard;\n" +
                            // calculate depth
                            "   vec4 newVertex = transform * vec4(finalPosition, 1.0);\n" +
                            "   gl_FragDepth = newVertex.z/newVertex.w;\n" +

                            // step by step define all material properties
                            builder.toString() +

                            "} else {\n" +// inside an object

                            "   finalColor = vec3(0.5);\n" +
                            "   finalAlpha = 0.5;\n" +
                            "   finalEmissive  = vec3(0.0);\n" +
                            "   finalMetallic  = 0.0;\n" +
                            "   finalRoughness = 1.0;\n" +

                            // distance must be set to slightly above 0, so we have valid z values
                            "   finalPosition = localTransform * vec4(localPos + localDir * 0.001, 1.0);\n" +
                            "   finalNormal = vec3(0.0);\n" +

                            // respect reflection plane
                            "   if(dot(vec4(finalPosition, 1.0), reflectionCullingPlane) < 0.0) discard;\n" +

                            // calculate depth
                            "   vec4 newVertex = transform * vec4(finalPosition, 1.0);\n" +
                            "   gl_FragDepth = newVertex.z/newVertex.w;\n" +

                            "}\n" +

                            // click ids of parts
                            "if(drawMode == ${ShaderPlus.DrawMode.ID.id} || drawMode == ${ShaderPlus.DrawMode.ID_VIS.id}){\n" +
                            "   int intId = int(ray.y);\n" +
                            "   tint = vec4(vec3(float(intId&255), float((intId>>8)&255), float((intId>>16)&255))/255.0, 1.0);\n" +
                            "}\n" +
                            ""

                )
                stage.functions.ensureCapacity(stage.functions.size + functions.size + 1)
                val builder2 = StringBuilder(100 + functions.sumOf { it.length } + shapeDependentShader.length + raycasting.length + normal.length)
                builder2.append("#define Infinity 1e20\n")
                for (func in functions) builder2.append(func)
                builder2.append("vec2 map(in vec3 pos0){\n")
                builder2.append("   vec2 res = vec2(1e20,-1.0);\n")
                builder2.append(shapeDependentShader)
                builder2.append("   return res;\n}\n")
                builder2.append(raycasting)
                builder2.append(normal)
                stage.functions.add(Function(builder2.toString()))
                return stage
            }
        }
        // why are those not ignored?
        shader.ignoreUniformWarnings(
            "sheenNormalMap", "occlusionMap", "metallicMap", "roughnessMap",
            "emissiveMap", "normalMap", "diffuseMap", "diffuseBase", "emissiveBase", "drawMode", "applyToneMapping",
            "ambientLight"
        )
        return uniforms to shader
    }

}