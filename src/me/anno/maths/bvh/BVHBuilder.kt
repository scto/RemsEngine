package me.anno.maths.bvh

import me.anno.ecs.Transform
import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.M4x3Delta.set4x3delta
import me.anno.gpu.pipeline.PipelineStageImpl
import me.anno.gpu.texture.Texture2D
import me.anno.maths.Maths
import me.anno.maths.Maths.log2i
import me.anno.maths.Maths.max
import me.anno.utils.Clock
import me.anno.utils.pooling.JomlPools
import me.anno.utils.search.Median.median
import me.anno.utils.structures.lists.Lists.partition1
import org.joml.AABBf
import org.joml.Matrix4x3f
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.sqrt

object BVHBuilder {

    // done build whole scene into TLAS+BLAS and then render it correctly
    // done how do we manage multiple meshes? connected buffer is probably the best...

    // we could reduce the number of materials, and draw the materials sequentially with separate TLASes...

    fun buildTLAS(
        scene: PipelineStageImpl, // filled with meshes
        cameraPosition: Vector3d, worldScale: Double,
        splitMethod: SplitMethod, maxNodeSize: Int
    ): TLASNode {
        val clock = Clock()
        val sizeGuess = scene.nextInsertIndex + scene.instanced.data.sumOf { it.size }
        val objects = ArrayList<TLASLeaf>(max(sizeGuess, 16))
        // add non-instanced objects
        val dr = scene.drawRequests
        fun add(mesh: Mesh, blas: BLASNode, transform: Transform) {
            val drawMatrix = transform.getDrawMatrix()
            val localToWorld = Matrix4x3f().set4x3delta(drawMatrix, cameraPosition, worldScale)
            val worldToLocal = Matrix4x3f()
            localToWorld.invert(worldToLocal)
            val centroid = Vector3f()
            val localBounds = mesh.getBounds()
            centroid.set(localBounds.centerX, localBounds.centerY, localBounds.centerZ)
            localToWorld.transformPosition(centroid)
            val globalBounds = AABBf()
            localBounds.transform(localToWorld, globalBounds)
            objects.add(TLASLeaf(centroid, localToWorld, worldToLocal, blas, globalBounds))
        }
        for (index in 0 until scene.nextInsertIndex) {
            val dri = dr[index]
            // to do theoretically, we'd need to respect the material override as well,
            // but idk how to do materials yet...
            val mesh = dri.mesh as? Mesh ?: continue
            val blas = mesh.raycaster ?: buildBLAS(mesh, splitMethod, maxNodeSize) ?: continue
            mesh.raycaster = blas
            val entity = dri.entity
            val transform = entity.transform
            add(mesh, blas, transform)
        }
        // add all instanced objects
        scene.instanced.data.forEach { mesh, _, _, stack ->
            if (mesh is Mesh) {
                val blas = mesh.raycaster ?: buildBLAS(mesh, splitMethod, maxNodeSize)
                if (blas != null) {
                    mesh.raycaster = blas
                    for (i in 0 until stack.size) {
                        val transform = stack.transforms[i]!!
                        add(mesh, blas, transform)
                    }
                }
            }
        }
        clock.stop("Creating BLASes")
        val tlas = buildTLAS(objects, splitMethod)
        clock.stop("Creating TLAS")
        return tlas
    }

    fun buildBLAS(mesh: Mesh, splitMethod: SplitMethod, maxNodeSize: Int): BLASNode? {
        val srcPos = mesh.positions ?: return null
        mesh.ensureNorTanUVs()
        val srcNor = mesh.normals!!
        val indices = mesh.indices ?: IntArray(srcPos.size / 3) { it }
        val geometryData = GeometryData(srcPos, srcNor, indices, mesh.color0)
        return recursiveBuildBLAS(srcPos, indices, 0, indices.size / 3, maxNodeSize, splitMethod, geometryData)
    }

    fun <V : TLASLeaf0> buildTLAS(
        objects: ArrayList<V>,
        splitMethod: SplitMethod,
    ) = buildTLAS(objects, splitMethod, 0, objects.size)

    @Suppress("DEPRECATION")
    fun <V : TLASLeaf0> buildTLAS(
        objects: ArrayList<V>, splitMethod: SplitMethod,
        start: Int, end: Int, // array indices
    ): TLASNode {
        val count = end - start
        if (count <= 1) {
            // leaf was already created by parent buildTLAS()
            return objects[start]
        } else {

            // bounds of center of primitives for efficient split dimension
            val centroidBounds = AABBf()
            for (index in start until end) {
                centroidBounds.union(objects[index].centroid)
            }

            // split dimension
            val dim = centroidBounds.maxDim()

            // partition primitives into two sets & build children
            var mid = (start + end) / 2
            if (centroidBounds.getMax(dim) == centroidBounds.getMin(dim)) {
                // creating a leaf node here would be illegal, because maxNodesPerPoint would be violated
                // nodes must be split randomly -> just skip all splitting computations
            } else {
                // partition based on split method
                // for the very start, we'll only implement the simplest methods
                when (splitMethod) {
                    SplitMethod.MIDDLE -> {
                        val midF = (centroidBounds.getMin(dim) + centroidBounds.getMax(dim)) * 0.5f
                        mid = objects.partition1(start, end) { t ->
                            t.centroid[dim] < midF
                        }
                        if (mid == start || mid >= end - 1) {// middle didn't work -> use more elaborate scheme
                            mid = medianApprox(objects, start, end, dim)
                        }
                    }
                    SplitMethod.MEDIAN -> {
                        objects.median(start, end) { t0, t1 ->
                            t0.centroid[dim].compareTo(t1.centroid[dim])
                        }
                        mid = (start + end) ushr 1
                    }
                    SplitMethod.MEDIAN_APPROX -> {
                        mid = medianApprox(objects, start, end, dim)
                    }
                    SplitMethod.SURFACE_AREA_HEURISTIC -> throw NotImplementedError()
                    SplitMethod.HIERARCHICAL_LINEAR -> throw NotImplementedError()
                }
            }

            val n0 = buildTLAS(objects, splitMethod, start, mid)
            val n1 = buildTLAS(objects, splitMethod, mid, end)

            val bounds = AABBf(n0.bounds)
            bounds.union(n1.bounds)

            return TLASBranch(dim, n0, n1, bounds)
        }
    }

    private fun <V : TLASLeaf0> medianApprox(objects: ArrayList<V>, start: Int, end: Int, dim: Int): Int {
        // don't sort, use statistical median
        fun sampleRandomly(): Float {
            val inst = objects[start + ((end - start) * Maths.random()).toInt()]
            return inst.centroid[dim]
        }

        var mid = (start + end) ushr 1
        val count = end - start
        val tries = count.toFloat().log2i() / 2
        for (ti in 0 until tries) {

            var pivot = 0f
            for (i in 0 until 5) pivot += sampleRandomly()
            pivot *= 0.2f

            var i = start - 1
            var j = end
            while (true) {
                do {
                    j--
                } while (objects[j].centroid[dim] < pivot)
                do {
                    i++
                } while (objects[i].centroid[dim] >= pivot)
                if (i < j) {
                    val tmp = objects[i]
                    objects[i] = objects[j]
                    objects[j] = tmp
                } else break
            }
            val relative = j - start
            if (relative > 0.25f * count && relative < 0.75f * count) { // >50% chance
                mid = j
                break
            }
        }
        return mid
    }

    // todo parallelize using GPU, if possible
    // this can be quite slow, taking 600ms for the dragon with 800k triangles

    private fun createBLASLeaf(
        positions: FloatArray,
        indices: IntArray,
        start: Int,
        end: Int,
        geometryData: GeometryData
    ): BLASLeaf {
        val bounds = AABBf()
        for (i in start * 3 until end * 3) {
            val ci = indices[i] * 3
            bounds.union(positions[ci], positions[ci + 1], positions[ci + 2])
        }
        val count = end - start
        return BLASLeaf(start, count, geometryData, bounds)
    }

    private fun calculateCentroidX3(positions: FloatArray, indices: IntArray, start: Int, end: Int): AABBf {
        val centroidBoundsX3 = AABBf()
        for (triIndex in start until end) {
            val pointIndex = triIndex * 3
            var ai = indices[pointIndex] * 3
            var bi = indices[pointIndex + 1] * 3
            var ci = indices[pointIndex + 2] * 3
            centroidBoundsX3.union(
                positions[ai++] + positions[bi++] + positions[ci++],
                positions[ai++] + positions[bi++] + positions[ci++],
                positions[ai] + positions[bi] + positions[ci]
            )
        }
        return centroidBoundsX3
    }

    private fun recursiveBuildBLAS(
        positions: FloatArray,
        indices: IntArray,
        start: Int, end: Int, // triangle indices
        maxNodeSize: Int,
        splitMethod: SplitMethod,
        geometryData: GeometryData,
    ): BLASNode {

        val count = end - start
        if (count <= maxNodeSize) {
            return createBLASLeaf(positions, indices, start, end, geometryData)
        }

        // bounds of center of primitives for efficient split dimension
        val centroidBoundsX3 = calculateCentroidX3(positions, indices, start, end)

        // split dimension
        val dim = centroidBoundsX3.maxDim()
        // println("centroid ${centroidBounds.deltaX}, ${centroidBounds.deltaY}, ${centroidBounds.deltaZ} -> $dim")

        // partition primitives into two sets & build children
        var mid = (start + end) / 2
        if (centroidBoundsX3.getMax(dim) == centroidBoundsX3.getMin(dim)) {
            // creating a leaf node here would be illegal, because maxNodesPerPoint would be violated
            // nodes must be split randomly -> just skip all splitting computations
        } else {
            // partition based on split method
            // for the very start, we'll only implement the simplest methods
            @Suppress("DEPRECATION")
            when (splitMethod) {
                SplitMethod.MIDDLE -> {
                    val midF = (centroidBoundsX3.getMin(dim) + centroidBoundsX3.getMax(dim)) * 0.5f
                    mid = partition(positions, indices, start, end) { a, b, c ->
                        a[dim] + b[dim] + c[dim] < midF
                    }
                    if (mid == start || mid >= end - 1) {// middle didn't work -> use more elaborate scheme
                        mid = (start + end) / 2
                        median(positions, indices, start, end) { a0, b0, c0, a1, b1, c1 ->
                            (a0[dim] + b0[dim] + c0[dim]).compareTo(a1[dim] + b1[dim] + c1[dim])
                        }
                    }
                }
                SplitMethod.MEDIAN -> {
                    median(positions, indices, start, end) { a0, b0, c0, a1, b1, c1 ->
                        (a0[dim] + b0[dim] + c0[dim]).compareTo(a1[dim] + b1[dim] + c1[dim])
                    }
                }
                SplitMethod.MEDIAN_APPROX -> {
                    medianApprox(positions, indices, start, end, dim)
                }
                SplitMethod.SURFACE_AREA_HEURISTIC -> throw NotImplementedError()
                SplitMethod.HIERARCHICAL_LINEAR -> throw NotImplementedError()
            }
        }

        val n0 = recursiveBuildBLAS(positions, indices, start, mid, maxNodeSize, splitMethod, geometryData)
        val n1 = recursiveBuildBLAS(positions, indices, mid, end, maxNodeSize, splitMethod, geometryData)

        val bounds = AABBf(n0.bounds)
        bounds.union(n1.bounds)

        return BLASBranch(dim, n0, n1, bounds)
    }

    fun medianApprox(positions: FloatArray, indices: IntArray, start: Int, end: Int, dim: Int): Int {
        fun sample(idx: Int): Float {
            val ai = indices[idx] * 3 + dim
            val bi = indices[idx + 1] * 3 + dim
            val ci = indices[idx + 2] * 3 + dim
            return positions[ai] + positions[bi] + positions[ci]
        }

        fun sample(): Float {
            val idx = (start + ((end - start) * Maths.random()).toInt())
            return sample(idx * 3)
        }

        var mid = (start + end) ushr 1

        val count = end - start
        val tries = count.toFloat().log2i() / 2
        for (ti in 0 until tries) {

            var pivot = 0f
            for (i in 0 until 5) pivot += sample()
            pivot *= 0.2f

            var i = start - 1
            var j = end
            while (true) {
                do {
                    j--
                } while (j >= 0 && sample(j * 3) < pivot)
                do {
                    i++
                } while (i < end && sample(i * 3) >= pivot)
                if (i < j) {
                    val i3 = i * 3
                    val j3 = j * 3
                    val t0 = indices[i3]
                    indices[i3] = indices[j3]
                    indices[j3] = t0
                    val t1 = indices[i3 + 1]
                    indices[i3 + 1] = indices[j3 + 1]
                    indices[j3 + 1] = t1
                    val t2 = indices[i3 + 2]
                    indices[i3 + 2] = indices[j3 + 2]
                    indices[j3 + 2] = t2
                } else break
            }
            val relative = j - start
            if (relative > 0.25f * count && relative < 0.75f * count) { // >50% chance
                mid = j
                break
            }
        }
        return mid
    }

    fun median(
        positions: FloatArray, indices: IntArray, start: Int, end: Int,
        condition: (
            Vector3f, Vector3f, Vector3f,
            Vector3f, Vector3f, Vector3f
        ) -> Int
    ) {
        // to do fix performance of this
        // not optimal performance, but at least it will 100% work
        val count = end - start
        val solution = ArrayList<Int>(count)
        for (it in 0 until count) {
            solution.add(start + it)
        }
        solution.median(0, count) { a, b ->
            comp(positions, indices, a, b, condition)
        }
        val c3 = count * 3
        val s3 = start * 3
        val indexBackup = IntArray(c3)
        indices.copyInto(indexBackup, 0, s3, s3 + indexBackup.size)
        var dst3 = s3
        for (i in 0 until count) {
            val m = solution[i]
            // move triangle from i to m
            var src3 = m * 3 - s3
            indices[dst3++] = indexBackup[src3++]
            indices[dst3++] = indexBackup[src3++]
            indices[dst3++] = indexBackup[src3]
        }
    }

    private fun comp(
        positions: FloatArray, indices: IntArray, i: Int, j: Int,
        condition: (
            Vector3f, Vector3f, Vector3f,
            Vector3f, Vector3f, Vector3f
        ) -> Int
    ): Int {
        val a0 = JomlPools.vec3f.create()
        val b0 = JomlPools.vec3f.create()
        val c0 = JomlPools.vec3f.create()
        val a1 = JomlPools.vec3f.create()
        val b1 = JomlPools.vec3f.create()
        val c1 = JomlPools.vec3f.create()
        val i3 = i * 3
        a0.set(positions, indices[i3] * 3)
        b0.set(positions, indices[i3 + 1] * 3)
        c0.set(positions, indices[i3 + 2] * 3)
        val j3 = j * 3
        a1.set(positions, indices[j3] * 3)
        b1.set(positions, indices[j3 + 1] * 3)
        c1.set(positions, indices[j3 + 2] * 3)
        val r = condition(a0, b0, c0, a1, b1, c1)
        JomlPools.vec3f.sub(6)
        return r
    }

    private fun partition(
        positions: FloatArray,
        indices: IntArray,
        start: Int,
        end: Int,
        condition: (Vector3f, Vector3f, Vector3f) -> Boolean
    ): Int {

        var i = start
        var j = (end - 1)

        while (i < j) {
            // while front is fine, progress front
            while (i < j && condition2(positions, indices, i, condition)) i++
            // while back is fine, progress back
            while (i < j && !condition2(positions, indices, j, condition)) j--
            // if nothing works, swap i and j
            if (i < j) {
                var i3 = i * 3
                var j3 = j * 3
                for (k in 0 until 3) {
                    val t = indices[i3]
                    indices[i3] = indices[j3]
                    indices[j3] = t
                    i3++
                    j3++
                }
            }
        }

        return i
    }

    private fun condition2(
        positions: FloatArray, indices: IntArray, i: Int,
        condition: (Vector3f, Vector3f, Vector3f) -> Boolean
    ): Boolean {
        val a = JomlPools.vec3f.create()
        val b = JomlPools.vec3f.create()
        val c = JomlPools.vec3f.create()
        val i3 = i * 3
        a.set(positions, indices[i3] * 3)
        b.set(positions, indices[i3 + 1] * 3)
        c.set(positions, indices[i3 + 2] * 3)
        val r = condition(a, b, c)
        JomlPools.vec3f.sub(3)
        return r
    }

    fun createTexture(name: String, numElements: Int, pixelsPerElement: Int): Texture2D {
        val requiredPixels = numElements * pixelsPerElement
        val textureWidth = Maths.align(sqrt(requiredPixels.toFloat()).toInt(), pixelsPerElement)
        val textureHeight = Maths.ceilDiv(requiredPixels, textureWidth)
        return Texture2D(name, textureWidth, textureHeight, 1)
    }
}