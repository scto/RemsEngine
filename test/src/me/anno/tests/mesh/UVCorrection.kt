package me.anno.tests.mesh

import me.anno.ecs.components.mesh.MaterialCache
import me.anno.ecs.components.mesh.Mesh
import me.anno.engine.ECSRegistry
import me.anno.image.Image
import me.anno.image.ImageCPUCache
import me.anno.io.files.FileReference
import me.anno.io.files.inner.InnerFolder
import me.anno.io.files.inner.InnerPrefabFile
import me.anno.mesh.obj.OBJReader
import me.anno.utils.Clock
import me.anno.utils.Color.b
import me.anno.utils.Color.g
import me.anno.utils.Color.r
import me.anno.utils.OS
import me.anno.utils.types.Floats.f3
import me.anno.utils.types.Vectors
import org.apache.logging.log4j.LogManager
import kotlin.math.abs

/**
 * uv-sign detection implementation test
 * */
fun main() {
    ECSRegistry.init()
    @Suppress("SpellCheckingInspection")
    val samples = listOf(
        // path and ideal detection
        "ogldev-source/Content/jeep.obj", // y
        "ogldev-source/Content/hheli.obj", // y
        "ogldev-source/Content/spider.obj", // n
        "ogldev-source/Content/dragon.obj", // doesn't matter
        "ogldev-source/Content/buddha.obj", // doesn't matter
        "ogldev-source/Content/dabrovic-sponza/sponza.obj"
    )
    val clock = Clock()
    for (sample in samples) {
        val ref = FileReference.getReference(OS.downloads, sample)
        OBJReader.readAsFolder(ref) { folder, _ ->
            folder!!
            clock.start()
            UVCorrection.correct(folder)
            clock.stop("calc") // the first one is always extra long
        }
    }
}

/**
 * checks, and corrects y-flipped UVs;
 * could probably be removed from the engine
 * */
object UVCorrection {

    private val LOGGER = LogManager.getLogger(UVCorrection::class)

    fun correct(folder: InnerFolder) {
        val meshes = folder.getChild("meshes").listChildren()
        // weight them by triangle area
        var normalContrast = 0.0
        var invertedContrast = 0.0
        for (meshFile in meshes ?: emptyList()) {
            meshFile as InnerPrefabFile
            val mesh = meshFile.prefab.getSampleInstance() as Mesh
            val pos = mesh.positions
            val uvs = mesh.uvs
            val materials = mesh.materials.mapNotNull { MaterialCache[it] }
            val textures = materials.mapNotNull { ImageCPUCache[it.diffuseMap, false] }
            if (pos != null && uvs != null && textures.isNotEmpty()) {
                val image = textures.maxByOrNull { it.width * it.height }!!
                val w = image.width
                val h = image.height
                val hm1 = h - 1
                mesh.forEachTriangleIndex { a, b, c ->
                    val a3 = a * 3
                    val b3 = b * 3
                    val c3 = c * 3
                    val area = Vectors.crossLength(pos, a3, b3, c3)
                    if (area > 0f) {
                        val a2 = a * 2
                        val b2 = b * 2
                        val c2 = c * 2
                        val ua = (uvs[a2] * w).toInt()
                        val ub = (uvs[b2] * w).toInt()
                        val uc = (uvs[c2] * w).toInt()
                        val va = (uvs[a2 + 1] * h).toInt()
                        val vb = (uvs[b2 + 1] * h).toInt()
                        val vc = (uvs[c2 + 1] * h).toInt()
                        normalContrast +=
                            area * approximatedContrast(ua, ub, uc, va, vb, vc, image)
                        invertedContrast +=
                            area * approximatedContrast(ua, ub, uc, hm1 - va, hm1 - vb, hm1 - vc, image)
                    }
                }
            }
        }
        // if inverted is better, then inverse all vs
        if (normalContrast > 0.0) {
            if (normalContrast > 1.1 * invertedContrast) {
                LOGGER.info("Checked UVs of $folder: ${(normalContrast / invertedContrast).f3()} -> inverted Vs")
                for (meshFile in meshes ?: emptyList()) {
                    meshFile as InnerPrefabFile
                    val mesh = meshFile.prefab.getSampleInstance() as Mesh
                    val uvs = mesh.uvs
                    if (uvs != null) {// inverse v
                        for (i in 1 until uvs.size step 2) {
                            uvs[i] = 1f - uvs[i]
                        }
                    }
                }
            } else {
                LOGGER.info("Checked UVs of $folder: ${(normalContrast / invertedContrast).f3()} -> ok")
            }
        }
    }

    private fun approximatedContrast(ua: Int, ub: Int, uc: Int, va: Int, vb: Int, vc: Int, image: Image): Int {
        // as an approximation, just the three corners should be good enough
        val c0 = image.getSafeRGB(ua, va)
        val c1 = image.getSafeRGB(ub, vb)
        val c2 = image.getSafeRGB(uc, vc)
        return delta(c0, c1) + delta(c1, c2) + delta(c2, c0)
    }

    private fun delta(c0: Int, c1: Int): Int {
        return abs(c1.r() - c0.r()) + abs(c1.g() - c0.g()) + abs(c1.b() - c0.b())
    }


}