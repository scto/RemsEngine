package me.anno.ecs.components.mesh

import me.anno.ecs.annotations.Type
import me.anno.ecs.components.mesh.material.Material
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.engine.serialization.SerializedProperty

// todo light beams: when inside the cone, from that view, then add a little brightness

open class MeshComponent() : MeshComponentBase() {

    constructor(mesh: FileReference) : this() {
        this.name = mesh.nameWithoutExtension
        this.meshFile = mesh
    }

    constructor(mesh: FileReference, material: Material) : this(mesh, material.ref)
    constructor(mesh: FileReference, material: FileReference) : this(mesh) {
        super.materials = listOf(material)
    }

    constructor(mesh: Mesh) : this(mesh.ref)
    constructor(mesh: Mesh, material: Material) : this(mesh) {
        super.materials = listOf(material.ref)
    }

    @SerializedProperty
    @Type("Mesh/Reference")
    var meshFile: FileReference = InvalidRef
        set(value) {
            if (field != value) {
                field = value
                invalidateAABB()
            }
        }

    // todo why is getMeshOrNull with async not working to load prefabs properly???
    override fun getMeshOrNull(): Mesh? = MeshCache[meshFile, false]
    override fun getMesh(): Mesh? = MeshCache[meshFile, false]

    // far into the future:
    // todo instanced animations for hundreds of humans:
    // todo bake animations into textures, and use indices + weights

    // on destroy we should maybe destroy the mesh:
    // only if it is unique, and owned by ourselves

    override fun destroy() {
        super.destroy()
        occlusionQuery?.destroy()
        occlusionQuery = null
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as MeshComponent
        dst.meshFile = meshFile
    }
}