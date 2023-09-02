package me.anno.ecs.components.mesh.terrain

import me.anno.ecs.components.mesh.Mesh
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Arrays.resize
import org.joml.Vector3f

object TerrainUtils {

    fun interface ColorMap {
        operator fun get(index: Int): Int
    }

    fun interface HeightMap {
        operator fun get(index: Int): Float
    }

    fun interface NormalMap {
        fun get(x: Int, y: Int, i: Int, dst: Vector3f)
    }

    @Suppress("unused")
    fun generateRegularQuadHeightMesh(
        width: Int,
        height: Int,
        offset: Int,
        stride: Int,
        flipY: Boolean,
        cellSizeMeters: Float,
        mesh: Mesh,
        heightMap: HeightMap,
        normalMap: NormalMap,
        colorMap: ColorMap
    ) {

        generateQuadIndices(width, height, flipY, mesh)

        ///////////////////////
        // generate vertices //
        ///////////////////////

        val vertexCount = width * height
        val numCoords = vertexCount * 3
        val positions = mesh.positions.resize(numCoords)
        val normals = mesh.normals.resize(numCoords)
        val colors = mesh.color0.resize(vertexCount)

        mesh.positions = positions
        mesh.normals = normals
        mesh.color0 = colors

        // center mesh
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        var j = 0
        var l = 0

        // define mesh positions
        val normal = JomlPools.vec3f.borrow()
        for (y in 0 until height) {
            var i = y * stride + offset
            for (x in 0 until width) {
                normalMap.get(x, y, i, normal)
                normals[j] = normal.x
                positions[j++] = (x - centerX) * cellSizeMeters
                normals[j] = normal.y
                positions[j++] = heightMap[i]
                normals[j] = normal.z
                positions[j++] = (y - centerY) * cellSizeMeters
                colors[l++] = colorMap[i]
                i++
            }
        }

        mesh.invalidateGeometry()

    }

    fun generateQuadIndices(width: Int, height: Int, flipY: Boolean, mesh: Mesh) {
        var k = 0
        val indexCount = (width - 1) * (height - 1) * 6
        val indices = mesh.indices.resize(indexCount)
        mesh.indices = indices
        for (y in 0 until height - 1) {
            var i00 = y * width
            for (x in 0 until width - 1) {

                val i01 = i00 + 1
                val i10 = i00 + width
                val i11 = i01 + width

                if (flipY) {

                    indices[k++] = i00
                    indices[k++] = i11
                    indices[k++] = i10

                    indices[k++] = i01
                    indices[k++] = i11
                    indices[k++] = i00

                } else {

                    indices[k++] = i00
                    indices[k++] = i10
                    indices[k++] = i11

                    indices[k++] = i11
                    indices[k++] = i01
                    indices[k++] = i00

                }
                i00++
            }
        }
    }


    @Suppress("unused")
    fun generateRegularQuadHeightMesh(
        width: Int,
        height: Int,
        offset: Int,
        stride: Int,
        flipY: Boolean,
        cellSizeMeters: Float,
        mesh: Mesh,
        heightMap: HeightMap,
        colorMap: ColorMap
    ) {

        generateQuadIndices(width, height, flipY, mesh)

        ///////////////////////
        // generate vertices //
        ///////////////////////

        val vertexCount = width * height
        val numCoords = vertexCount * 3
        val positions = mesh.positions.resize(numCoords)
        val normals = mesh.normals.resize(numCoords)
        val colors = mesh.color0.resize(vertexCount)

        mesh.positions = positions
        mesh.normals = normals
        mesh.color0 = colors

        normals.fill(0f)

        // center mesh
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        var j = 0
        var l = 0

        // define mesh positions
        for (y in 0 until height) {
            var i = y * stride + offset
            for (x in 0 until width) {
                positions[j++] = (x - centerX) * cellSizeMeters
                positions[j++] = heightMap[i]
                positions[j++] = (y - centerY) * cellSizeMeters
                colors[l++] = colorMap[i]
                i++
            }
        }

        mesh.invalidateGeometry()

    }

    fun fillUVs(mesh: Mesh) {
        val pos = mesh.positions!!
        val bs = mesh.getBounds()
        val uvs = mesh.uvs.resize(pos.size / 3 * 2)
        var j = 0
        val fx = +1f / bs.deltaX
        val fz = -1f / bs.deltaZ
        val mx = bs.minX
        val mz = bs.maxZ
        for (i in pos.indices step 3) {
            uvs[j++] = (pos[i] - mx) * fx
            uvs[j++] = (pos[i + 2] - mz) * fz
        }
        mesh.uvs = uvs
    }

}