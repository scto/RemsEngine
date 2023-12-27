package me.anno.gpu.shader

import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import org.joml.Matrix4x3f
import org.joml.Vector3i
import org.joml.Vector4f
import kotlin.math.sqrt

@Suppress("MemberVisibilityCanBePrivate", "unused")
object ShaderLib {

    /**
     * This function is intended for shaders to be used for matrix-vector multiplication.
     * It makes writing a DirectX backend easier.
     * */
    const val matMul = "" +
            "#ifndef matMul\n" +
            "   #ifdef HLSL\n" +
            "       #define matMul(a,b) mul(a,b)\n" +
            "   #else\n" +
            "       #define matMul(a,b) (a)*(b)\n" +
            "   #endif\n" +
            "#endif\n"

    const val loadMat4x3 = "" +
            "mat4x3 loadMat4x3(vec4 a, vec4 b, vec4 c){\n" +
            "   return mat4x3(\n" +
            "       a.xyz,\n" +
            "       vec3(a.w, b.xy),\n" +
            "       vec3(b.zw, c.x),\n" +
            "       c.yzw\n" +
            "   );\n" +
            "}\n"

    val y = Vector4f(0.299f, 0.587f, 0.114f, 0f)
    val u = Vector4f(-0.169f, -0.331f, 0.500f, 0.5f)
    val v = Vector4f(0.500f, -0.419f, -0.081f, 0.5f)
    val mi = Matrix4x3f(
        y.x, u.x, v.x,
        y.y, u.y, v.y,
        y.z, u.z, v.z,
        y.w, u.w, v.w,
    )
    val m = mi.invert(Matrix4x3f())

    val coordsList = listOf(Variable(GLSLType.V2F, "coords", VariableMode.ATTR))

    const val coordsVertexShader = "" +
            "void main(){\n" +
            "   gl_Position = vec4(coords*2.0-1.0,0.5,1.0);\n" +
            "}"

    const val coordsUVVertexShader = "" +
            "void main(){\n" +
            "   gl_Position = vec4(coords*2.0-1.0,0.5,1.0);\n" +
            "   uv = coords;\n" +
            "}"

    val uvList = listOf(Variable(GLSLType.V2F, "uv"))
    val uiVertexShaderList = coordsList + listOf(
        Variable(GLSLType.V4F, "posSize"),
        Variable(GLSLType.V4F, "tiling"),
        Variable(GLSLType.M4x4, "transform"),
    )

    const val uiVertexShader = "" +
            "void main(){\n" +
            "   gl_Position = matMul(transform, vec4((posSize.xy + coords * posSize.zw)*2.0-1.0, 0.0, 1.0));\n" +
            "   uv = (coords-0.5) * tiling.xy + 0.5 + tiling.zw;\n" +
            "}"

    val dither2x2 = "" +
            "bool dither2x2(float brightness, vec2 uvf) {\n" +
            "  ivec2 uvi = ivec2(floor(uvf)) & ivec2(1);\n" +
            "  int index = (uvi.x + uvi.y * 2 + gl_SampleID) & 3;\n" +
            "  float limit = 0.20;\n" +
            "  if (index == 1) limit = 0.60;\n" +
            "  if (index == 2) limit = 0.80;\n" +
            "  if (index == 3) limit = 0.40;\n" +
            "  return brightness < limit;\n" +
            "}\n" +
            "bool dither2x2(float brightness) { return dither2x2(brightness, gl_FragCoord.xy); }\n"

    val brightness = "" +
            "float brightness(vec3 color){\n" +
            "   return sqrt(${y.x}*color.r*color.r + ${y.y}*color.g*color.g + ${y.z}*color.b*color.b);\n" +
            "}\n" +
            "float brightness(vec4 color){\n" +
            "   return sqrt(${y.x}*color.r*color.r + ${y.y}*color.g*color.g + ${y.z}*color.b*color.b);\n" +
            "}\n"

    val blendColor = "" +
            "vec4 blendColor(vec4 front, vec4 back){\n" +
            "   return vec4(mix(back.rgb,front.rgb,front.a),1.0-(1.0-front.a)*(1.0-back.a));\n" +
            "}\n"

    fun brightness(r: Float, g: Float, b: Float) = sqrt(y.dot(r * r, g * g, b * b, 1f))

    // https://community.khronos.org/t/quaternion-functions-for-glsl/50140/3
    const val quatRot = "" +
            "mat2 rot(float angle){\n" +
            "   float c = cos(angle), s = sin(angle);\n" +
            "   return mat2(c,-s,+s,c);\n" +
            "}\n" +
            "vec3 quatRot(vec3 v, vec4 q){\n" +
            "   return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);\n" +
            "}\n" +
            "vec3 quatRotInv(vec3 v, vec4 q){\n" +
            "   return v - 2.0 * cross(q.xyz, q.w * v - cross(q.xyz, v));\n" +
            "}\n"

    // from http://www.java-gaming.org/index.php?topic=35123.0
    // https://stackoverflow.com/questions/13501081/efficient-bicubic-filtering-code-in-glsl
    private const val foreignBicubicInterpolation = "" +
            "vec4 cubic(float v){\n" +
            "    vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - v;\n" +
            "    vec4 s = n * n * n;\n" +
            "    float x = s.x;\n" +
            "    float y = s.y - 4.0 * s.x;\n" +
            "    float z = s.z - 4.0 * s.y + 6.0 * s.x;\n" +
            "    float w = 6.0 - x - y - z;\n" +
            "    return vec4(x, y, z, w) * (1.0/6.0);\n" +
            "}\n" +
            "vec4 textureBicubic(sampler2D tex, vec2 texCoords){\n" +

            "   vec2 texSize = vec2(textureSize(tex, 0));\n" +
            "   vec2 invTexSize = 1.0 / texSize;\n" +

            "   texCoords = texCoords * texSize - 0.5;\n" +

            "    vec2 fxy = fract(texCoords);\n" +
            "    texCoords -= fxy;\n" +

            "    vec4 xCubic = cubic(fxy.x);\n" +
            "    vec4 yCubic = cubic(fxy.y);\n" +

            "    vec4 c = texCoords.xxyy + vec2(-0.5, +1.5).xyxy;\n" +

            "    vec4 s = vec4(xCubic.xz + xCubic.yw, yCubic.xz + yCubic.yw);\n" +
            "    vec4 offset = c + vec4(xCubic.yw, yCubic.yw) / s;\n" +

            "    offset *= invTexSize.xxyy;\n" +

            "    vec4 sample0 = texture(tex, offset.xz);\n" +
            "    vec4 sample1 = texture(tex, offset.yz);\n" +
            "    vec4 sample2 = texture(tex, offset.xw);\n" +
            "    vec4 sample3 = texture(tex, offset.yw);\n" +
            "    float sx = s.x / (s.x + s.y);\n" +
            "    float sy = s.z / (s.z + s.w);\n" +
            "    return mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);\n" +
            "}"

    const val bicubicInterpolation = "" +
            // no more artifacts, but much smoother
            foreignBicubicInterpolation +
            "vec4 bicubicInterpolation(sampler2D tex, vec2 uv, vec2 duv){\n" +
            "   return textureBicubic(tex, uv);\n" +
            "}\n"

    val rgb2yuv = "" +
            "vec3 rgb2yuv(vec3 rgb){\n" +
            "   vec4 rgba = vec4(rgb,1);\n" +
            "   return vec3(\n" +
            "       dot(rgb,  vec3(${y.x}, ${y.y}, ${y.z})),\n" +
            "       dot(rgba, vec4(${u.x}, ${u.y}, ${u.z}, 0.5)),\n" +
            "       dot(rgba, vec4(${v.x}, ${v.y}, ${v.z}, 0.5))\n" +
            "   );\n" +
            "}\n"

    val rgb2uv = "" +
            "vec2 RGBtoUV(vec3 rgb){\n" +
            "   vec4 rgba = vec4(rgb,1.0);\n" +
            "   return vec2(\n" +
            "       dot(rgba, vec4(${u.x}, ${u.y}, ${u.z}, 0.5)),\n" +
            "       dot(rgba, vec4(${v.x}, ${v.y}, ${v.z}, 0.5))\n" +
            "   );\n" +
            "}\n"

    val yuv2rgb = "" +
            "vec3 yuv2rgb(vec3 yuv){\n" +
            "   return vec3(\n" +
            "       dot(yuv, vec3(${m.m00}, ${m.m10}, ${m.m20}))+${m.m30},\n" +
            "       dot(yuv, vec3(${m.m01}, ${m.m11}, ${m.m21}))+${m.m31},\n" +
            "       dot(yuv, vec3(${m.m02}, ${m.m12}, ${m.m22}))+${m.m32}\n" +
            "   );\n" +
            "}\n"

    const val anisotropic16 = "" +
            // anisotropic filtering from https://www.shadertoy.com/view/4lXfzn
            "vec4 textureAnisotropic(sampler2D T, vec2 p, mat2 J) {\n" +
            // "    mat2 J = inverse(mat2(dFdx(p),dFdy(p))); // dFdxy: pixel footprint in texture space\n" +
            "   J = transpose(J)*J;\n" + // quadratic form
            "   vec2 R = textureSize(T,0);\n" +
            "   float d = determinant(J), t = J[0][0]+J[1][1],\n" + // find ellipse: eigenvalues, max eigenvector
            "         D = sqrt(abs(t*t-4.*d)),\n" + // abs() fix a bug: in weird view angles 0 can be slightly negative
            "         V = (t-D)/2., v = (t+D)/2.,\n" + // eigenvalues
            "         M = inversesqrt(V), m = inversesqrt(v), l = log2(m*R.y);\n" + // = 1./radii^2
            "  // if (M/m>16.) l = log2(M/16.*R.y);\n" + // optional
            "    vec2 A = M * normalize(vec2(-J[0][1] , J[0][0]-V));\n" + // max eigenvector = main axis
            "    vec4 O = vec4(0);\n" +
            "    for (float i = -7.5; i<8.; i++) \n" + // sample x16 along main axis at LOD min-radius
            "        O += textureLod(T, p+(i/16.)*A, l);\n" +
            "    return O/16.;\n" +
            "}\n" +
            "vec4 textureAnisotropic(sampler2D T, vec2 p, vec2 u) {\n" +
            "   return textureAnisotropic(T, p, inverse(mat2(dFdx(u),dFdy(u))));\n" +
            "}\n" +
            "vec4 textureAnisotropic(sampler2D T, vec2 p) { return textureAnisotropic(T, p, p); }\n"

    const val flatNormal = "" +
            "   normal = vec3(0.0, 0.0, 1.0);\n"

    val v3Dl = listOf(
        Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
        Variable(GLSLType.V2F, "uvs", VariableMode.ATTR),
        Variable(GLSLType.M4x4, "transform"),
        Variable(GLSLType.V4F, "tiling")
    )

    const val v3D = "" +
            "void main(){\n" +
            "   finalPosition = coords;\n" +
            "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
            "   uv = (uvs-0.5) * tiling.xy + 0.5 + tiling.zw;\n" +
            "   uvw = coords;\n" +
            flatNormal +
            "}"

    val y2D = uvList

    val y3D = listOf(
        Variable(GLSLType.V2F, "uv"),
        Variable(GLSLType.V3F, "uvw"),
        Variable(GLSLType.V3F, "finalPosition"),
        Variable(GLSLType.V3F, "normal")
    )

    val f3Dl = listOf(
        Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
        Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
        Variable(GLSLType.S2D, "tex")
    )

    val v3DlMasked = listOf(
        Variable(GLSLType.M4x4, "transform"),
        Variable(GLSLType.V2F, "coords", VariableMode.ATTR),
    )

    val v3DMasked = "" +
            "void main(){\n" +
            "   finalPosition = vec3(coords*2.0-1.0, 0.0);\n" +
            "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
            "   uv = gl_Position.xyw;\n" +
            "}"

    val y3DMasked = listOf(
        Variable(GLSLType.V3F, "uv"),
        Variable(GLSLType.V3F, "finalPosition")
    )

    // make this customizable?
    val blacklist = listOf(
        "cgSlope", "cgOffset", "cgPower", "cgSaturation",
        "forceFieldUVCount", "forceFieldColorCount"
    )

    // https://knarkowicz.wordpress.com/2014/04/16/octahedron-normal-vector-encoding/
    const val octNormalPacking = "" +
            "\n#ifndef PACKING_NORMALS\n" +
            "#define PACKING_NORMALS\n" +
            "vec2 PackNormal(vec3 n) {\n" +
            "   n /= max(1e-7, abs(n.x)+abs(n.y)+abs(n.z));\n" +
            "   if(n.z < 0.0) n.xy = (1.0-abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);\n" +
            "   return n.xy * 0.5 + 0.5;\n" +
            "}\n" +
            "vec3 UnpackNormal(vec2 f) {\n" +
            "   f = f * 2.0 - 1.0;\n" +
            // https://twitter.com/Stubbesaurus/status/937994790553227264
            "   vec3 n = vec3(f.xy, 1.0-abs(f.x)-abs(f.y));\n" +
            "   n.xy -= sign(n.xy) * max(-n.z,0.0);\n" +
            "   return normalize(n);\n" +
            "}\n" +
            "#endif\n"

    lateinit var shader3DSVG: BaseShader
    fun init() {

        // with texture
        // somehow becomes dark for large |steps|-values

        val vSVGl = listOf(
            Variable(GLSLType.V3F, "aLocalPosition", VariableMode.ATTR),
            Variable(GLSLType.V2F, "aLocalPos2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aFormula0", VariableMode.ATTR),
            Variable(GLSLType.V1F, "aFormula1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor0", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor3", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aStops", VariableMode.ATTR),
            Variable(GLSLType.V1F, "aPadding", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform")
        )

        val vSVG = "" +
                "void main(){\n" +
                "   finalPosition = aLocalPosition;\n" +
                "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                flatNormal +
                "   color0 = aColor0;\n" +
                "   color1 = aColor1;\n" +
                "   color2 = aColor2;\n" +
                "   color3 = aColor3;\n" +
                "   stops = aStops;\n" +
                "   padding = aPadding;\n" +
                "   localPos2 = aLocalPos2;\n" +
                "   formula0 = aFormula0;\n" +
                "   formula1 = aFormula1;\n" +
                "}"

        val ySVG = y3D + listOf(
            Variable(GLSLType.V4F, "color0"),
            Variable(GLSLType.V4F, "color1"),
            Variable(GLSLType.V4F, "color2"),
            Variable(GLSLType.V4F, "color3"),
            Variable(GLSLType.V4F, "stops"),
            Variable(GLSLType.V4F, "formula0"), // pos, dir
            Variable(GLSLType.V1F, "formula1"), // is circle
            Variable(GLSLType.V1F, "padding"), // spread method / repetition type
            Variable(GLSLType.V2F, "localPos2"), // position for gradient
        )

        val fSVGl = listOf(
            Variable(GLSLType.V4F, "uvLimits"),
            Variable(GLSLType.S2D, "tex"),
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
        )

        val fSVG = "" +
                "bool isInLimits(float value, vec2 minMax){\n" +
                "   return value >= minMax.x && value <= minMax.y;\n" +
                "}\n" + // sqrt and ² for better color mixing
                "vec4 mix2(vec4 a, vec4 b, float stop, vec2 stops){\n" +
                "   float f = clamp((stop-stops.x)/(stops.y-stops.x), 0.0, 1.0);\n" +
                "   return vec4(sqrt(mix(a.rgb*a.rgb, b.rgb*b.rgb, f)), mix(a.a, b.a, f));\n" +
                "}\n" +
                "void main(){\n" +
                // apply the formula; polynomial of 2nd degree
                "   vec2 delta = localPos2 - formula0.xy;\n" +
                "   vec2 dir = formula0.zw;\n" +
                "   float stopValue = formula1 > 0.5 ? length(delta * dir) : dot(dir, delta);\n" +

                "   if(padding < 0.5){\n" + // clamp
                "       stopValue = clamp(stopValue, 0.0, 1.0);\n" +
                "   } else if(padding < 1.5){\n" + // repeat mirrored, and yes, it looks like magic xD
                "       stopValue = 1.0 - abs(fract(stopValue*0.5)*2.0-1.0);\n" +
                "   } else {\n" + // repeat
                "       stopValue = fract(stopValue);\n" +
                "   }\n" +

                // find the correct color
                "   vec4 color = \n" +
                "       stopValue <= stops.x ? color0:\n" +
                "       stopValue >= stops.w ? color3:\n" +
                "       stopValue <  stops.y ? mix2(color0, color1, stopValue, stops.xy):\n" +
                "       stopValue <  stops.z ? mix2(color1, color2, stopValue, stops.yz):\n" +
                "                              mix2(color2, color3, stopValue, stops.zw);\n" +
                "   if(isInLimits(uv.x, uvLimits.xz) && isInLimits(uv.y, uvLimits.yw)){" +
                "       vec4 color2 = color * texture(tex, uv * 0.5 + 0.5);\n" +
                "       finalColor = color2.rgb;\n" +
                "       finalAlpha = color2.a;\n" +
                "   } else {" +
                "       finalColor = vec3(0);\n" +
                "       finalAlpha = 0.0;\n" +
                "   }" +
                "}"

        shader3DSVG = createShader("3d-svg", vSVGl, vSVG, ySVG, fSVGl, fSVG, listOf("tex"))
    }

    val shader3DPlanar = createShader(
        "3d", v3Dl, v3D, y3D, f3Dl, "" +
                "void main(){\n" +
                "   vec4 color = texture(tex, uv);\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = color.a;\n" +
                "}", listOf("tex"),
        "normals", "uvs", "tangents", "colors", "drawMode", "tint",
        "finalNormal", "finalEmissive"
    )

    val shader3DTiledCubemap = createShader(
        "3d", v3Dl, v3D, y3D, f3Dl, "" +
                "void main(){\n" +
                "   vec4 color = texture(tex, uv);\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = color.a;\n" +
                "}", listOf("tex"),
        "normals", "uvs", "tangents", "colors", "drawMode", "tint",
        "finalNormal", "finalEmissive"
    )

    val shader3DSimple = createShader(
        "3d", v3Dl, v3D, y3D, f3Dl, "" +
                "void main(){\n" +
                "   finalColor = vec3(1.0);\n" +
                "   finalAlpha = 1.0;\n" +
                "}", listOf("tex"),
        "normals", "uvs", "tangents", "colors", "tint",
        "finalNormal", "finalEmissive"
    )

    val textShader = BaseShader(
        "textShader", coordsList + listOf(
            Variable(GLSLType.V4F, "posSize"),
            // not really supported, since subpixel layouts would be violated for non-integer translations, scales, skews or perspective
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V2F, "windowSize"),
        ),
        "" +
                "void main(){\n" +
                "   vec2 localPos = posSize.xy + coords * posSize.zw;\n" +
                "   gl_Position = matMul(transform, vec4(localPos*2.0-1.0, 0.0, 1.0));\n" +
                "   position = localPos * windowSize;\n" +
                "   uv = coords;\n" +
                "}",
        listOf(
            Variable(GLSLType.V2F, "uv"),
            Variable(GLSLType.V2F, "position")
        ),
        listOf(
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
            Variable(GLSLType.V4F, "textColor"),
            Variable(GLSLType.V4F, "backgroundColor"),
            Variable(GLSLType.V2F, "windowSize"),
            Variable(GLSLType.S2D, "tex")
        ), "" +
                brightness +
                "void main(){\n" +
                "   float mixing = brightness(texture(tex, uv).rgb) * textColor.a;\n" +
                "   vec4 color = mix(backgroundColor, textColor, mixing);\n" +
                "   if(color.a < 0.001) discard;\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = color.a;\n" +
                "}"
    )

    val subpixelCorrectTextShader = Array(2) {
        val instanced = it > 0
        val type = if (instanced) VariableMode.ATTR else VariableMode.IN
        BaseShader(
            "subpixelCorrectTextShader", listOf(
                Variable(GLSLType.V2F, "coords", VariableMode.ATTR),
                Variable(GLSLType.V3F, "instData", type),
                Variable(GLSLType.V4F, "color0", type),
                Variable(GLSLType.V4F, "color1", type),
                Variable(GLSLType.V4F, "posSize"),
                // not really supported, since subpixel layouts would be violated for non-integer translations, scales, skews or perspective
                Variable(GLSLType.M4x4, "transform"),
                Variable(GLSLType.V2F, "windowSize"),
            ), if (instanced) {
                "" +
                        "void main(){\n" +
                        "   vec2 localPos = coords * posSize.zw + instData.xy;\n" +
                        "   gl_Position = matMul(transform, vec4(localPos*2.0-1.0, 0.0, 1.0));\n" +
                        "   position = localPos * windowSize;\n" +
                        "   textColor = color0;\n" +
                        "   backgroundColor = color1;\n" +
                        "   uv = vec3(coords,instData.z);\n" +
                        "}"
            } else {
                "" +
                        "void main(){\n" +
                        "   vec2 localPos = coords * posSize.zw + posSize.xy;\n" +
                        "   gl_Position = matMul(transform, vec4(localPos*2.0-1.0, 0.0, 1.0));\n" +
                        "   position = localPos * windowSize;\n" +
                        "   uv = coords;\n" +
                        "}"
            },
            if (instanced) listOf(
                Variable(GLSLType.V3F, "uv"),
                Variable(GLSLType.V2F, "position"),
                Variable(GLSLType.V4F, "textColor"),
                Variable(GLSLType.V4F, "backgroundColor"),
            ) else listOf(
                Variable(GLSLType.V2F, "uv"),
                Variable(GLSLType.V2F, "position")
            ), listOf(
                Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
                Variable(GLSLType.V2F, "windowSize"),
                Variable(if (instanced) GLSLType.S2DA else GLSLType.S2D, "tex"),
            ) + (if (instanced) emptyList() else listOf(
                Variable(GLSLType.V4F, "textColor"),
                Variable(GLSLType.V4F, "backgroundColor"),
            )), "" +
                    brightness +
                    "void main(){\n" +
                    "#define IS_TINTED\n" +
                    // the conditions are a black border implementation for WebGL; they could be skipped on desktop
                    "   vec3 mixing = any(lessThan(uv.xy,vec2(0.0))) || any(greaterThan(uv.xy,vec2(1.0))) ? vec3(0.0) :\n" +
                    "                 texture(tex, uv).rgb * textColor.a;\n" +
                    "   float mixingAlpha = brightness(mixing);\n" +
                    // theoretically, we only need to check the axis, which is affected by subpixel-rendering, e.g., x on my screen
                    "   if(position.x < 1.0 || position.y < 1.0 || position.x > windowSize.x - 1.0 || position.y > windowSize.y - 1.0)\n" +
                    "       mixing = vec3(mixingAlpha);\n" + // on the border; color seams would become apparent here
                    "   vec4 color = mix(backgroundColor, textColor, vec4(mixing, mixingAlpha));\n" +
                    "   if(color.a < 0.001) discard;\n" +
                    "   finalColor = color.rgb;\n" +
                    "   finalAlpha = 1.0;\n" +
                    "}"
        )
    }

    val subpixelCorrectTextShader2 = Array(2) {
        val instanced = it > 0
        ComputeShader(
            "subpixelCorrectTextShader2", Vector3i(16, 16, 1), listOf(
                Variable(GLSLType.V4F, "textColor"),
                Variable(GLSLType.V4F, "backgroundColor"),
                Variable(GLSLType.V1F, "uvZ"),
                Variable(GLSLType.V2I, "srcOffset"),
                Variable(GLSLType.V2I, "dstOffset"),
                Variable(GLSLType.V2I, "invokeSize"),
                Variable(if(instanced) GLSLType.S2DA else GLSLType.S2D, "tex"),
            ), "" +
                    brightness +
                    "layout(rgba8, binding = 1) restrict uniform image2D dst;\n" +
                    // the border isn't the most beautiful, but it ensures readability in front of bad backgrounds :)
                    "float read(vec2 uv){\n" +
                    (if (instanced)
                        "   vec3 col = textureLod(tex, vec3(uv,uvZ), 0.0).rgb;\n" else
                        "   vec3 col = textureLod(tex, uv, 0.0).rgb;\n") +
                    "   return uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0 ? (col.x + col.y + col.z) : 0.0;\n" +
                    "}\n" +
                    "float findBorder(ivec2 uv, vec2 invSizeM1){\n" +
                    "   float sum = 0.16 * (\n" +
                    "       read(vec2(uv.x,uv.y-1)*invSizeM1) +\n" +
                    "       read(vec2(uv.x,uv.y+1)*invSizeM1) +\n" +
                    "       read(vec2(uv.x+1,uv.y)*invSizeM1) +\n" +
                    "       read(vec2(uv.x-1,uv.y)*invSizeM1)) + 0.08 * (\n" +
                    "       read(vec2(uv.x-1,uv.y-1)*invSizeM1) +\n" +
                    "       read(vec2(uv.x-1,uv.y+1)*invSizeM1) +\n" +
                    "       read(vec2(uv.x+1,uv.y-1)*invSizeM1) +\n" +
                    "       read(vec2(uv.x+1,uv.y+1)*invSizeM1));\n" +
                    "   return min(sum, 1.0);\n" +
                    "}\n" +
                    "void main(){\n" +
                    "   ivec2 uv = ivec2(gl_GlobalInvocationID.xy), uv0 = uv;\n" +
                    "   if(uv.x >= invokeSize.x || uv.y >= invokeSize.y) return;\n" +
                    "   ivec2 size = textureSize(tex, 0).xy;\n" +
                    "   vec2 invSizeM1 = 1.0/vec2(size-1);\n" +
                    "   vec2 uv1 = vec2(uv + srcOffset)*invSizeM1;\n" +
                    (if (instanced)
                        "   vec3 mixingSrc = textureLod(tex, vec3(uv1,uvZ), 0.0).rgb;\n" else
                        "   vec3 mixingSrc = textureLod(tex, uv1, 0.0).rgb;\n") +
                    "   vec3 mixing = mixingSrc * textColor.a;\n" +
                    "   float mixingAlpha = brightness(mixing);\n" +
                    "   size = imageSize(dst);\n" +
                    // theoretically, we only need to check the axis, which is affected by subpixel-rendering, e.g., x on my screen
                    "   if(uv.x <= 0 || uv.y <= 0 || uv.x >= invokeSize.x-1 || uv.y >= invokeSize.y - 1)\n" +
                    "       mixing = vec3(mixingAlpha);\n" + // on the border; color seams would become apparent here
                    "   uv += dstOffset;\n" +
                    "   uv.y = size.y - 1 - uv.y;\n" +
                    "   vec4 backgroundColorI = imageLoad(dst, uv);\n" +
                    // todo there is awkward gray pieces around the text...
                    // "   if(mixingSrc.y < 1.0 && brightness(abs(textColor - backgroundColorI)) < 0.7)\n" +
                    "       backgroundColorI.rgb = mix(backgroundColorI.rgb, backgroundColor.rgb, findBorder(uv0+srcOffset, invSizeM1));\n" +
                    "   vec4 color = mix(backgroundColorI, textColor, vec4(mixing, mixingAlpha));\n" +
                    "   imageStore(dst, uv, color);\n" +
                    "}"
        ).apply { ignoreNameWarnings("windowSize") }
    }

    // from https://learnopengl.com/Advanced-Lighting/Parallax-Mapping
    // https://learnopengl.com/code_viewer_gh.php?code=src/5.advanced_lighting/5.3.parallax_occlusion_mapping/5.3.parallax_mapping.fs
    val parallaxMapping = "" +
            "vec2 parallaxMapUVs(sampler2D depthMap, vec2 texCoords, vec3 viewDir, float heightScale) { \n" +
            // clamping? repeating? out-of-bounds-pixel-access?... -> repeating :)
            // number of depth layers
            "    const float minLayers = 8.0;\n" +
            "    const float maxLayers = 32.0;\n" +
            "    float numLayers = mix(maxLayers, minLayers, abs(viewDir.z));\n" +
            "    vec2 texSize = vec2(textureSize(depthMap,0));\n" +
            // calculate the size of each layer
            "    float layerDepth = 1.0 / numLayers;\n" +
            "    float currentLayerDepth = -0.5;\n" +
            // the amount to shift the texture coordinates per layer (from vector P)
            "    vec2 P = viewDir.xy / viewDir.z * heightScale; \n" +
            "    vec2 deltaTexCoords = texSize * P / numLayers;\n" +
            // get initial values
            "    vec2  currentTexCoords     = texSize * texCoords;\n" +
            "    float currentDepthMapValue = 0.5 - texelFetch(depthMap, ivec2(mod(currentTexCoords, texSize)), 0).r;\n" +
            "    while(currentLayerDepth < currentDepthMapValue) {\n" +
            // shift texture coordinates along direction of P
            "        currentTexCoords -= deltaTexCoords;\n" +
            // get depth map value at current texture coordinates
            "        currentDepthMapValue = 0.5 - texelFetch(depthMap, ivec2(mod(currentTexCoords, texSize)), 0).r;  \n" +
            // get depth of next layer
            "        currentLayerDepth += layerDepth;\n" +
            "    }\n" +
            // get texture coordinates before collision (reverse operations)
            "    vec2 prevTexCoords = currentTexCoords + deltaTexCoords;\n" +

            // get depth after and before collision for linear interpolation
            "    float afterDepth  = currentDepthMapValue - currentLayerDepth;\n" +
            "    float beforeDepth = 0.5 - texelFetch(depthMap, ivec2(mod(prevTexCoords, texSize)), 0).r - currentLayerDepth + layerDepth;\n" +

            // interpolation of texture coordinates
            "    float weight = afterDepth / (afterDepth - beforeDepth);\n" +
            "    return mix(currentTexCoords, prevTexCoords, weight) / texSize;\n" +
            "}\n"

    fun createShader(
        shaderName: String,
        vertexShader: String,
        varyings: List<Variable>,
        fragmentShader: String,
        textures: List<String>,
        vararg ignored: String
    ): BaseShader {
        val shader = BaseShader(shaderName, vertexShader, varyings, fragmentShader)
        shader.setTextureIndices(textures)
        shader.ignoreNameWarnings(ignored.toList())
        return shader
    }

    fun createShader(
        shaderName: String,
        vertexVariables: List<Variable>,
        vertexShader: String,
        varyings: List<Variable>,
        fragmentVariables: List<Variable>,
        fragmentShader: String,
        textures: List<String>,
        vararg ignored: String
    ): BaseShader {
        val shader = BaseShader(shaderName, vertexVariables, vertexShader, varyings, fragmentVariables, fragmentShader)
        shader.setTextureIndices(textures)
        shader.ignoreNameWarnings(ignored.toList())
        return shader
    }
}