package me.anno.ecs.components.mesh.utils

import me.anno.ecs.components.mesh.Mesh
import me.anno.maths.Packing.pack64
import me.anno.utils.structures.arrays.BooleanArrayList
import kotlin.math.max
import kotlin.math.min

object OnEdgeCalculator {

    fun calculateIsOnEdge(mesh: Mesh, dst: BooleanArrayList): BooleanArrayList {

        dst.fill(true)

        // calculate isOnEdge
        // if there is few vertices, we could use an IntArray for counting
        val sides = HashMap<Long, Int>()
        var index = 0
        mesh.forEachLineIndex { a, b ->
            val min = min(a, b)
            val max = max(a, b)
            val hash = pack64(min, max)
            val previousIndex = sides.put(hash, index)
            if (previousIndex != null) {
                // it was already included -> this side is an edge
                dst[previousIndex] = false
                dst[index] = false
            }
            index++
        }

        return dst
    }
}