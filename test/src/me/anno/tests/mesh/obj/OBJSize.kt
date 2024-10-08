package me.anno.tests.mesh.obj

import me.anno.mesh.obj.OBJReader
import me.anno.utils.OS.downloads

fun main() {
    // seems like a good guess: :)
    // val defaultSize = Maths.clamp(file.length() / 64, 64, 1 shl 20).toInt()
    val ref = downloads.getChild("3d/ogldev-source/dabrovic-sponza/sponza.obj")
    // val ref = getReference(documents, "sphere.obj")
    ref.inputStream { input, err ->
        err?.printStackTrace()
        if (input != null) {
            val reader = OBJReader(input, ref)
            println("materials: " + reader.materialsFolder.children.size)
            println("meshes: " + reader.meshesFolder.children.size)
        }
    }
}