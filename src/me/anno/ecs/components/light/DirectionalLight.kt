package me.anno.ecs.components.light

import me.anno.ecs.annotations.Range
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.debug.DebugLine
import me.anno.engine.debug.DebugShapes
import me.anno.engine.ui.LineShapes.drawArrowZ
import me.anno.engine.ui.LineShapes.drawBox
import me.anno.gpu.pipeline.Pipeline
import me.anno.mesh.Shapes
import org.joml.*

class DirectionalLight : LightComponent(LightType.DIRECTIONAL) {

    /**
     * typically a directional light will be the sun;
     * it's influence should be over the whole scene, while its shadows may not
     *
     * with cutoff != 0, it is cutoff, as if it was a plane light
     * */
    @Range(-1.0, 1.0)
    var cutoff = 0f

    override fun fillSpace(globalTransform: Matrix4x3d, aabb: AABBd): Boolean {
        if (cutoff == 0f) {
            aabb.all()
        } else {
            getLightPrimitive()
                .getBounds()
                .transformUnion(globalTransform, aabb)
        }
        return true
    }

    override fun updateShadowMap(
        cascadeScale: Double,
        worldScale: Double,
        dstCameraMatrix: Matrix4f,
        dstCameraPosition: Vector3d,
        cameraRotation: Quaterniond,
        cameraDirection: Vector3d,
        drawTransform: Matrix4x3d,
        pipeline: Pipeline,
        resolution: Int
    ) {

        // todo allow to set the focus point non-centered?

        // cascade style must only influence xy, not z
        dstCameraMatrix.set(drawTransform).invert()
        dstCameraMatrix.setTranslation(0f, 0f, 0f)
        val sx = (1.0 / (cascadeScale * worldScale))
        val sz = (1.0 / (worldScale))

        // z must be mapped from [-1,1] to [0,1]
        // additionally it must be scaled to match the world size
        dstCameraMatrix.scaleLocal(sx.toFloat(), sx.toFloat(), (sz * 0.5).toFloat())
        dstCameraMatrix.m32((1.0 / cascadeScale).toFloat()) // w

        // is this correct if cascadeScale != 1.0?
        // should be
        pipeline.frustum.defineOrthographic(
            drawTransform, resolution,
            dstCameraPosition, cameraRotation
        )

        // offset camera position accordingly
        cameraDirection.mulAdd(
            -(2.0 / cascadeScale - 1.0) / (worldScale),
            dstCameraPosition, dstCameraPosition
        )

        // reconstructMatrixBoundsForTesting(dstCameraMatrix, worldScale, dstCameraPosition)
    }

    /**
     * tests whether the calculated matrix is correct:
     * visualizes the bounds of what will be rendered
     * */
    fun reconstructMatrixBoundsForTesting(dstCameraMatrix: Matrix4f, worldScale: Double, dstCameraPosition: Vector3d) {
        val pts = ArrayList<Vector3f>(8)
        for (x in listOf(-1f, 1f)) {
            for (y in listOf(-1f, 1f)) {
                for (z in listOf(0f, 1f)) {
                    pts.add(Vector3f(x, y, z))
                }
            }
        }

        val inv = dstCameraMatrix.invert(Matrix4f())
        for (pt in pts) {
            inv.transformPosition(pt)
            pt.mul(1f / worldScale.toFloat())
            pt.add(Vector3f(dstCameraPosition))
        }

        for (i in 0 until 8) {
            for (j in i + 1 until 8) {
                val d = j - i
                if (d.and(d - 1) == 0) {
                    val line = DebugLine(Vector3d(pts[i]), Vector3d(pts[j]), -1, 0f)
                    DebugShapes.debugLines.add(line)
                }
            }
        }
    }

    override fun getLightPrimitive(): Mesh = Shapes.cube11Smooth

    // v0 is not used
    override fun getShaderV1(): Float = shadowMapPower.toFloat()
    override fun getShaderV2(): Float = if (cutoff == 0f) 0f else 1f / cutoff

    override fun drawShape() {
        drawBox(entity)
        drawArrowZ(entity, +1.0, -1.0)
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as DirectionalLight
        dst.cutoff = cutoff
    }

    override val className: String get() = "DirectionalLight"

    companion object {
        fun getShaderCode(cutoffContinue: String?, withShadows: Boolean): String {
            return "" +
                    (if (cutoffContinue != null) {
                        "" +
                                "#define invCutoff shaderV2\n" +
                                "if(invCutoff > 0.0){\n" +
                                "   float cut = min(invCutoff * (1.0 - dot(lightPos,lightPos)), 1.0);\n" +
                                "   if(cut <= 0.0) { $cutoffContinue; }\n" +
                                "   lightColor *= cut;\n" +
                                "} else if(invCutoff < 0.0){\n" +
                                "   float cut = min(-invCutoff * (1.0 - max(max(abs(lightPos.x),abs(lightPos.y)),abs(lightPos.z))), 1.0);\n" +
                                "   if(cut <= 0.0) { $cutoffContinue; }\n" +
                                "   lightColor *= cut;\n" +
                                "}\n"
                    } else "") +
                    "NdotL = lightNor.z;\n" + // dot(lightDirWS, globalNormal) = dot(lightDirLS, localNormal)
                    "lightColor *= max(NdotL, 0.0);\n" + // light looks much better with it
                    (if (withShadows) "" +
                            "if(shadowMapIdx0 < shadowMapIdx1 && receiveShadows){\n" +
                            // when we are close to the edge, we blend in
                            "   float edgeFactor = min(20.0*(1.0-max(abs(lightPos.x),abs(lightPos.y))),1.0);\n" +
                            "   if(edgeFactor > 0.0){\n" +
                            "       #define shadowMapPower shaderV1\n" +
                            "       float invShadowMapPower = 1.0/shadowMapPower;\n" +
                            "       vec2 shadowDir = lightPos.xy;\n" +
                            "       vec2 nextDir = shadowDir * shadowMapPower;\n" +
                            // find the best shadow map
                            // blend between the two best shadow maps, if close to the border?
                            // no, the results are already very good this way :)
                            // at least at the moment, the seams are not obvious
                            "       float layerIdx=0.0;\n" +
                            "       while(abs(nextDir.x)<1.0 && abs(nextDir.y)<1.0 && shadowMapIdx0+1<shadowMapIdx1){\n" +
                            "           shadowMapIdx0++;\n" +
                            "           layerIdx++;\n" +
                            "           shadowDir = nextDir;\n" +
                            "           nextDir *= shadowMapPower;\n" +
                            "       }\n" +
                            "       float depthFromShader = lightPos.z*.5+.5;\n" +
                            // do the shadow map function and compare
                            "       float depthFromTex = texture_array_depth_shadowMapPlanar(shadowMapIdx0, vec3(shadowDir.xy,layerIdx), depthFromShader);\n" +
                            // todo this will become proportional to the distance to the shadow throwing surface
                            // "           float coc = 1.0 / texture_array_size_shadowMapPlanar(shadowMapIdx0, 0).x;\n" +
                            // "           float val = texture_array_shadowMapPlanar(shadowMapIdx0, shadowDir.xy).r;\n" +
                            // "           diffuseColor = vec3(val,val,dir.z);\n" + // nice for debugging
                            "       lightColor *= 1.0 - edgeFactor * (1.0 - depthFromTex);\n" +
                            "   }\n" +
                            "}\n"
                    else "") +
                    "effectiveDiffuse = lightColor;\n" +
                    "if(hasSpecular){\n" +
                    // good like that?
                    // NdotL kind of needs to be reset after this...
                    // todo sheen needs to influence this, too
                    "   float oldNdotL = NdotL;\n" +
                    "   NdotL = reflect(viewDir, lightNor).z;\n" +
                    "   float x = max(NdotL, 0.0), y = 1.0 + 256.0 * pow(1.0 - finalRoughness, 2.0);\n" +
                    // pow(x,y) is the shape of sharpness; the divider is the integral from x=0 to x=1 over pow(x,y)*(1-x)
                    "   float lightEffect = pow(x,y) / (1.0/(y+1.0) - 1.0/(y+2.0));\n" +
                    "   effectiveSpecular = lightColor * lightEffect;\n" +
                    "   NdotL = oldNdotL;\n" +
                    "}\n"
        }
    }
}