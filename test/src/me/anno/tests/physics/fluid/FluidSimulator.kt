package me.anno.tests.physics.fluid

import me.anno.gpu.GFXState
import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import kotlin.math.exp

object FluidSimulator {

    private const val calculateNeighborUVs = "" +
            "   vec2 vL = uv - vec2(texelSize.x, 0.0);\n" +
            "   vec2 vR = uv + vec2(texelSize.x, 0.0);\n" +
            "   vec2 vT = uv - vec2(0.0, texelSize.y);\n" +
            "   vec2 vB = uv + vec2(0.0, texelSize.y);\n"

    val splatShader = Shader(
        "splat", ShaderLib.uiVertexShaderList, ShaderLib.uiVertexShader, ShaderLib.uvList,
        listOf(
            Variable(GLSLType.V4F, "color"),
            Variable(GLSLType.V4F, "result", VariableMode.OUT)
        ), "" +
                "void main(){\n" +
                "   vec2 uv1 = uv*2.0-1.0;\n" +
                "   float effect = max(1.0-length(uv1),0.0);\n" +
                "   result = effect * color;\n" +
                "}\n"
    )

    val splashShader = Shader(
        "splash", ShaderLib.uiVertexShaderList, ShaderLib.uiVertexShader, ShaderLib.uvList,
        listOf(
            Variable(GLSLType.V1F, "strength"),
            Variable(GLSLType.V4F, "result", VariableMode.OUT)
        ), "" +
                "void main() {\n" +
                "   vec2 uv1 = uv*2.0-1.0;\n" +
                "   float dist = dot(uv1,uv1);\n" +
                "   result = vec4(uv1 * exp(-dist*10.0) * strength, 0.0, 0.0);\n" +
                "}\n"
    )

    val divergenceShader = Shader(
        "divergence", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "result", VariableMode.OUT),
            Variable(GLSLType.V2F, "texelSize"),
            Variable(GLSLType.S2D, "velocityTex")
        ), "" +
                "void main(){\n" +
                calculateNeighborUVs +
                "   float L = texture(velocityTex, vL).x;\n" +
                "   float R = texture(velocityTex, vR).x;\n" +
                "   float T = texture(velocityTex, vT).y;\n" +
                "   float B = texture(velocityTex, vB).y;\n" +
                "   vec2 C = texture(velocityTex, uv).xy;\n" +
                // if on edge, set value to negative of center (?)
                "   if(vL.x < 0.0) { L = -C.x; }\n" +
                "   if(vR.x > 1.0) { R = -C.x; }\n" +
                "   if(vT.y > 1.0) { T = -C.y; }\n" +
                "   if(vB.y < 0.0) { B = -C.y; }\n" +
                "   result = vec4(0.5*(R-L+T-B),0.0,0.0,1.0);\n" +
                "}"
    )

    val scalingDownProgram = Shader(
        "scale", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "result", VariableMode.OUT),
            Variable(GLSLType.V1F, "scale"),
            Variable(GLSLType.S2D, "pressureTex")
        ), "" +
                "void main(){\n" +
                "   result = scale * texture(pressureTex,uv);\n" +
                "}"
    )

    val pressureProgram = Shader(
        "scale", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "result", VariableMode.OUT),
            Variable(GLSLType.V2F, "texelSize"),
            Variable(GLSLType.V1F, "pressure"),
            Variable(GLSLType.S2D, "pressureTex"),
            Variable(GLSLType.S2D, "divergenceTex")
        ), "" +
                "void main() {\n" +
                calculateNeighborUVs +
                "   float L = texture2D(pressureTex, vL).x;\n" +
                "   float R = texture2D(pressureTex, vR).x;\n" +
                "   float T = texture2D(pressureTex, vT).x;\n" +
                "   float B = texture2D(pressureTex, vB).x;\n" +
                "   float C = texture2D(pressureTex, uv).x;\n" +
                "   float divergence = texture2D(divergenceTex, uv).x;\n" +
                "   float pressure = (L + R + B + T - divergence) * 0.25;\n" +
                "   result = vec4(pressure, 0.0, 0.0, 1.0);\n" +
                "}"
    )

    val findVelocityProgram = Shader(
        "gradientSub", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "result", VariableMode.OUT),
            Variable(GLSLType.V2F, "texelSize"),
            Variable(GLSLType.S2D, "velocityTex"),
            Variable(GLSLType.S2D, "pressureTex"),
        ), "" +
                "void main() {\n" +
                calculateNeighborUVs +
                "   float L = texture2D(pressureTex, vL).x;\n" +
                "   float R = texture2D(pressureTex, vR).x;\n" +
                "   float T = texture2D(pressureTex, vT).x;\n" +
                "   float B = texture2D(pressureTex, vB).x;\n" +
                "   vec2 velocity = texture2D(velocityTex, uv).xy - vec2(R - L, T - B);\n" +
                "   result = vec4(velocity, 0.0, 1.0);\n" +
                "}"
    )

    val fluidMotionProgram = Shader(
        "advection", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "result", VariableMode.OUT),
            Variable(GLSLType.V2F, "texelSize"),
            Variable(GLSLType.V1F, "dt"),
            Variable(GLSLType.V1F, "scale"),
            Variable(GLSLType.S2D, "velocityTex"),
            Variable(GLSLType.S2D, "sourceTex"),
        ), "" +
                "void main() {\n" +
                "   vec2 coord = uv - dt * texture2D(velocityTex, uv).xy * texelSize;\n" +
                "   result = texture2D(sourceTex, coord) * scale;\n" +
                "}"
    )

    val particlesProgram = Shader(
        "particles", emptyList(), ShaderLib.coordsUVVertexShader, ShaderLib.uvList, listOf(
            Variable(GLSLType.V4F, "resultPosition", VariableMode.OUT).apply { slot = 0 },
            Variable(GLSLType.V4F, "resultVelocity", VariableMode.OUT).apply { slot = 1 },
            Variable(GLSLType.V4F, "resultRotation", VariableMode.OUT).apply { slot = 2 },
            Variable(GLSLType.V4F, "resultMetadata", VariableMode.OUT).apply { slot = 3 },
            Variable(GLSLType.V2F, "texelSize"),
            Variable(GLSLType.V1F, "dt"),
            // particle data
            Variable(GLSLType.S2D, "positionTex"),
            Variable(GLSLType.S2D, "velocityTex"),
            Variable(GLSLType.S2D, "rotationTex"),
            Variable(GLSLType.S2D, "metadataTex"),
            // fluid data
            Variable(GLSLType.S2D, "fluidVelocityTex"),
            Variable(GLSLType.S2D, "fluidPressureTex"),
        ), "" +
                // todo flag whether to handle inter-particle collisions?
                //  doable for small numbers of particles like 20
                "void main() {\n" +
                // gather data
                "   vec3 position = texture(positionTex, uv).xyz;\n" +
                "   vec3 velocity = texture(velocityTex, uv).xyz;\n" +
                "   vec3 rotation = texture(rotationTex, uv).xyz;\n" +
                "   vec4 metadata = texture(metadataTex, uv);\n" +
                "   vec2 fluidUV = position.xz;\n" +
                "   vec2 fluidVelocity = texture(fluidVelocityTex, fluidUV).xy;\n" +
                "   float fluidHeight = texture(fluidPressureTex, fluidUV).x;\n" +
                "   float radius = metadata.y;\n" +
                "   float mass = metadata.z;\n" +
                "   float density = metadata.w;\n" +
                // process data
                "   float mixFactor = 0.5;\n" +
                "   float inWater = clamp((fluidHeight - position.y) / radius + 1.0, 0.0, 1.0);\n" +
                "   float accelerationY = mix(-9.81, (1.0 - density) / density, inWater) * ${1.0 / waveHeight};\n" +
                // calculate new velocity
                // todo if not deep enough in fluid, reduce effect of fluid, and add ground friction
                // todo calculate new rotation based on rotation and gradient of fluid
                "   float friction = 3.0;\n" +
                "   vec3 newVelocity = vec3(mix(velocity.xz, fluidVelocity, mixFactor), (velocity.y + accelerationY * dt) * exp(-dt * friction)).xzy;\n" +
                "   resultPosition = vec4(position + (velocity + newVelocity) * (0.5 * dt), 0.0);\n" +
                "   resultPosition.xyz = clamp(resultPosition.xyz, vec3(0.0), vec3(1.0,20.0,1.0));\n" +
                "   if(resultPosition.x == 0.0) newVelocity.x = +abs(newVelocity.x);\n" +
                "   if(resultPosition.z == 0.0) newVelocity.z = +abs(newVelocity.z);\n" +
                "   if(resultPosition.x == 1.0) newVelocity.x = -abs(newVelocity.x);\n" +
                "   if(resultPosition.z == 1.0) newVelocity.z = -abs(newVelocity.z);\n" +
                "   resultVelocity = vec4(newVelocity, 0.0);\n" +
                "   resultRotation = vec4(rotation, 0.0);\n" +
                "   resultMetadata = metadata;\n" +
                // todo particles clump too easily... why???
                "}"
    )

    fun bindRenderAndSwap(program: Shader, state: RWState<IFramebuffer>, bind: Shader.(IFramebuffer) -> Unit) {
        if (state.write.width > 0 && state.write.height > 0) {
            GFXState.useFrame(state.write) {
                program.use()
                program.bind(state.read)
                flat01.draw(program)
            }
            state.swap()
        }
    }

    fun bindRender(program: Shader, state: Framebuffer, bind: Shader.() -> Unit) {
        GFXState.useFrame(state) {
            program.use()
            program.bind()
            flat01.draw(program)
        }
    }

    fun step(dt: Float, sim: FluidSimulation) {

        bindRender(divergenceShader, sim.divergence) {
            v2f("texelSize", sim.texelSize)
            sim.velocity.read.getTexture0().bindTrulyNearest(this, "velocityTex")
        }

        if (sim.fluidScaling != 0f) {
            bindRenderAndSwap(scalingDownProgram, sim.pressure) { pressure ->
                pressure.getTexture0().bindTrulyNearest(this, "pressureTex")
                v1f("scale", exp(-dt * sim.fluidScaling))
            }
        }

        for (i in 0 until sim.numPressureIterations) {
            bindRenderAndSwap(pressureProgram, sim.pressure) { pressure ->
                v2f("texelSize", sim.texelSize)
                sim.divergence.getTexture0().bindTrulyNearest(this, "divergenceTex")
                pressure.getTexture0().bindTrulyNearest(this, "pressureTex")
            }
        }

        bindRenderAndSwap(findVelocityProgram, sim.velocity) { velocity ->
            v2f("texelSize", sim.texelSize)
            sim.pressure.read.getTexture0().bindTrulyNearest(this, "pressureTex")
            velocity.getTexture0().bindTrulyNearest(this, "velocityTex")
        }

        // if there are more attributes, like color, they should be transferred using the same program
        // set their dissipation to 1.0
        bindRenderAndSwap(fluidMotionProgram, sim.velocity) { velocity ->
            v2f("texelSize", sim.texelSize)
            v1f("dt", dt)
            v1f("scale", exp(-dt * sim.dissipation))
            // correct source? yes, both are bound to the same target
            velocity.getTexture0().bindTrulyLinear(this, "sourceTex")
            velocity.getTexture0().bindTrulyLinear(this, "velocityTex")
        }

        bindRenderAndSwap(particlesProgram, sim.particles) { particles ->
            v1f("dt", dt)
            sim.velocity.read.getTexture0().bindTrulyNearest(this, "fluidVelocityTex")
            sim.pressure.read.getTexture0().bindTrulyNearest(this, "fluidPressureTex")
            particles.getTextureI(0).bindTrulyNearest(this, "positionTex")
            particles.getTextureI(1).bindTrulyNearest(this, "velocityTex")
            particles.getTextureI(2).bindTrulyNearest(this, "rotationTex")
            particles.getTextureI(3).bindTrulyNearest(this, "metadataTex")
        }
    }
}