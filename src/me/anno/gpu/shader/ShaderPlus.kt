package me.anno.gpu.shader

import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable

object ShaderPlus {

    fun create(name: String, vertex: String, varying: List<Variable>, fragment: String): Shader {
        return Shader(name, null, vertex, varying, makeFragmentShaderUniversal(varying, fragment))
    }

    fun create(name: String, geometry: String?, vertex: String, varying: List<Variable>, fragment: String): Shader {
        return Shader(name, geometry, vertex, varying, makeFragmentShaderUniversal(varying, fragment))
    }

    fun makeFragmentShaderUniversal(varyingSource: List<Variable>, fragmentSource: String): String {
        val hasFinalColor = "finalColor" in fragmentSource
        val hasZDistance = "zDistance" in varyingSource.map { it.name }
        val hasTint = "vec4 tint;" in fragmentSource || "tint" in varyingSource.map { it.name }
        val raw = fragmentSource.trim()
        if (!raw.endsWith("}")) throw RuntimeException()
        return "" +
                "uniform int drawMode;\n" +
                (if (hasTint) "" else "uniform vec4 tint;\n") +
                "" + raw.substring(0, raw.length - 1) + "" +
                (if (hasZDistance) "" else "float zDistance = 0.0;\n") +
                (if (hasFinalColor) "" else "vec3 finalColor = gl_FragColor.rgb;float finalAlpha = gl_FragColor.a;\n") +
                "switch(drawMode){\n" +
                "       case ${DrawMode.COLOR_SQUARED.id}:\n" +
                "           vec3 tmpCol = ${if (hasTint) "finalColor" else "finalColor * tint.rgb"};\n" +
                "           gl_FragColor = vec4(tmpCol * tmpCol, clamp(finalAlpha, 0.0, 1.0) * tint.a);\n" +
                "           break;\n" +
                "       case ${DrawMode.COLOR.id}:\n" +
                "           gl_FragColor = vec4(${if (hasTint) "finalColor" else "finalColor * tint.rgb"}, clamp(finalAlpha, 0.0, 1.0) * tint.a);\n" +
                "           break;\n" +
                "       case ${DrawMode.ID.id}:\n" +
                "           if(finalAlpha < 0.01) discard;\n" +
                "           gl_FragColor.rgb = tint.rgb;\n" +
                "           break;\n" +
                "       case ${DrawMode.DEPTH_LOG2_01.id}:\n" +
                "           if(finalAlpha < 0.01) discard;\n" +
                "           float depth01 = 0.5 + 0.04 * log2(zDistance);\n" +
                "           gl_FragColor = vec4(depth01, depth01, depth01*depth01, finalAlpha);\n" +
                "           break;\n" +
                "       case ${DrawMode.DEPTH_LOG2.id}:\n" +
                "           if(finalAlpha < 0.01) discard;\n" +
                "           gl_FragColor = vec4(zDistance, 0.0, zDistance * zDistance, finalAlpha);\n" +
                "           break;\n" +
                "       case ${DrawMode.COPY.id}:\n" +
                "           gl_FragColor = vec4(finalColor, finalAlpha);\n" +
                "           break;\n" +
                "       case ${DrawMode.TINT.id}:\n" +
                "           gl_FragColor = tint;\n" +
                "           break;\n" +
                "   }\n" +
                "}"

    }

    fun createShaderStage(hasTint: Boolean = true): ShaderStage {
        val callName = "applyShaderPlus"
        val variables = listOf(
            Variable(GLSLType.V1F, "zDistance"),
            Variable(GLSLType.V4F, "tint"),
            Variable(GLSLType.V3F, "finalColor"),
            Variable(GLSLType.V1F, "finalAlpha"),
            Variable(GLSLType.V1I, "drawMode"),
            Variable(GLSLType.V4F, "fragColor", false),
        )
        val code = "" +
                "switch(drawMode){\n" +
                "    case ${DrawMode.COLOR_SQUARED.id}:\n" +
                "        vec3 tmpCol = ${if (hasTint) "finalColor" else "finalColor * tint.rgb"};\n" +
                "        fragColor = vec4(tmpCol * tmpCol, clamp(finalAlpha, 0.0, 1.0) * tint.a);\n" +
                "        break;\n" +
                "    case ${DrawMode.COLOR.id}:\n" +
                "        fragColor = vec4(${if (hasTint) "finalColor" else "finalColor * tint.rgb"}, clamp(finalAlpha, 0.0, 1.0) * tint.a);\n" +
                "        break;\n" +
                "    case ${DrawMode.ID.id}:\n" +
                "        if(finalAlpha < 0.01) discard;\n" +
                "        fragColor.rgb = tint.rgb;\n" +
                "        break;\n" +
                "    case ${DrawMode.DEPTH_LOG2_01.id}:\n" +
                "        if(finalAlpha < 0.01) discard;\n" +
                "        float depth01 = 0.5 + 0.04 * log2(zDistance);\n" +
                "        fragColor = vec4(depth01, depth01, depth01*depth01, finalAlpha);\n" +
                "        break;\n" +
                "    case ${DrawMode.DEPTH_LOG2.id}:\n" +
                "        if(finalAlpha < 0.01) discard;\n" +
                "        fragColor = vec4(zDistance, 0.0, zDistance * zDistance, finalAlpha);\n" +
                "        break;\n" +
                "    case ${DrawMode.COPY.id}:\n" +
                "       fragColor = vec4(finalColor, finalAlpha);\n" +
                "       break;\n" +
                "    case ${DrawMode.TINT.id}:\n" +
                "       fragColor = tint;\n" +
                "       break;\n" +
                "}\n"
        return ShaderStage(callName, variables, code)
    }

    enum class DrawMode(val id: Int) {
        COLOR_SQUARED(0),
        COLOR(1),
        ID(2),
        DEPTH_LOG2_01(3), // does not need a float buffer
        DEPTH_LOG2(4), // needs a float buffer
        COPY(5),
        TINT(6)
    }

}