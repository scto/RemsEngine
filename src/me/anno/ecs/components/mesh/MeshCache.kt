package me.anno.ecs.components.mesh

import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.EntityQuery.forAllChildren
import me.anno.ecs.EntityQuery.forAllComponents
import me.anno.ecs.Transform
import me.anno.ecs.components.mesh.utils.MeshJoiner
import me.anno.ecs.prefab.PrefabByFileCache
import me.anno.ecs.prefab.change.Path
import me.anno.gpu.pipeline.Pipeline
import me.anno.io.files.FileReference
import me.anno.io.saveable.Saveable
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.lists.Lists.any2
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4x3d
import org.joml.Matrix4x3f

object MeshCache : PrefabByFileCache<Mesh>(Mesh::class, "Mesh") {

    private val LOGGER = LogManager.getLogger(MeshCache::class)

    /**
     * transforms any Saveable into a Mesh (as far as supported);
     * can be really expensive, so you should cache your results
     * */
    override fun castInstance(instance: Saveable?, ref: FileReference): Mesh? {
        return when (instance) {
            is Mesh -> instance
            is MeshComponentBase -> instance.getMesh() as? Mesh
            is MeshSpawner -> joinMeshes(listOf(instance))
            is Entity -> joinMeshes(findMeshes(instance))
            null -> null
            else -> {
                LOGGER.warn("Requesting mesh from ${instance.className}, cannot extract it")
                null
            }
        }
    }

    private fun findMeshes(instance: Entity): List<Component> {
        val components = ArrayList<Component>()
        val remaining = ArrayList<Entity>()
        remaining.add(instance)
        while (true) { // non-recursive implementation
            val entity = remaining.removeLastOrNull() ?: break
            entity.validateTransform()
            entity.forAllChildren(false) { child ->
                remaining.add(child)
            }
            entity.forAllComponents(false) { comp ->
                if (comp is MeshComponentBase || comp is MeshSpawner) {
                    components.add(comp)
                }
            }
        }
        return components
    }

    private fun addMesh(
        meshes: ArrayList<Triple<Mesh, Transform?, List<FileReference>>>,
        mesh: IMesh?, transform: Transform?, compMaterials: List<FileReference>?
    ) {
        if (mesh is Mesh && mesh.proceduralLength <= 0) {
            val meshMaterials = mesh.materials
            val materials = (0 until mesh.numMaterials).map {
                Pipeline.getMaterialRef(compMaterials, meshMaterials, it)
            }
            meshes.add(Triple(mesh, transform, materials))
        } // else not supported
    }

    private fun collectMeshes(list: List<Component>): List<Triple<Mesh, Transform?, List<FileReference>>> {
        val meshes = ArrayList<Triple<Mesh, Transform?, List<FileReference>>>()
        for (i in list.indices) {
            when (val comp = list[i]) {
                is MeshComponentBase -> {
                    addMesh(meshes, comp.getMesh(), comp.transform, comp.materials)
                }
                is MeshSpawner -> {
                    comp.forEachMesh { mesh, material, transform ->
                        val materialList = if (material == null) emptyList()
                        else listOf(material.ref)
                        addMesh(meshes, mesh, transform, materialList)
                    }
                }
            }
        }
        return meshes
    }

    /**
     * this should only be executed for decently small meshes ^^,
     * large meshes may cause OutOfMemoryExceptions
     * */
    private fun joinMeshes(list: List<Component>): Mesh? {
        val meshes = collectMeshes(list)
        return when (meshes.size) {
            0 -> null
            1 -> join1Mesh(meshes[0])
            else -> joinNMeshes(meshes)
        }
    }

    private fun join1Mesh(meshes: Triple<Mesh, Transform?, List<FileReference>>): Mesh {
        // special case: no joining required
        val (mesh, transform, materials) = meshes
        transform?.validate()
        val matrix = transform?.globalTransform
        return if ((matrix == null || matrix.isIdentity())) {
            if (materials == mesh.materials) mesh else {
                val clone = mesh.clone() as Mesh
                clone.materials = materials
                clone.prefabPath = Path.ROOT_PATH
                clone.prefab = null
                clone
            }
        } else {
            // transform required
            // only needed for position, normal and tangents
            val clone = mesh.clone() as Mesh
            transformMesh(clone, matrix)
            clone.materials = materials
            clone.unlink()
            clone
        }
    }

    private fun joinNMeshes(meshes: List<Triple<Mesh, Transform?, List<FileReference>>>): Mesh {
        val hasColors = meshes.any2 { it.first.color0 != null }
        val hasBones = meshes.any2 { it.first.hasBones }
        val hasUVs = meshes.any2 { it.first.uvs != null }

        return object : MeshJoiner<Triple<Mesh, Transform?, List<FileReference>>>(hasColors, hasBones, hasUVs) {
            override fun getMesh(element: Triple<Mesh, Transform?, List<FileReference>>) = element.first
            override fun getMaterials(element: Triple<Mesh, Transform?, List<FileReference>>) = element.third
            override fun getTransform(element: Triple<Mesh, Transform?, List<FileReference>>, dst: Matrix4x3f) {
                val transform = element.second
                if (transform != null) {
                    transform.validate()
                    dst.set(transform.globalTransform)
                } else dst.identity()
            }
        }.join(meshes)
    }

    fun transformMesh(mesh: Mesh, matrix: Matrix4x3d): Mesh {
        mesh.positions = transformPositions(matrix, mesh.positions)
        mesh.normals = transformDirections(matrix, mesh.normals, 3)
        mesh.tangents = transformDirections(matrix, mesh.tangents, 4)
        mesh.invalidateGeometry()
        return mesh
    }

    private fun transformPositions(matrix: Matrix4x3d, src: FloatArray?): FloatArray? {
        src ?: return null
        val tmp = JomlPools.vec3f.borrow()
        for (i in src.indices step 3) {
            tmp.set(src, i)
            matrix.transformPosition(tmp)
            tmp.get(src, i)
        }
        return src
    }

    private fun transformDirections(matrix: Matrix4x3d, src: FloatArray?, stride: Int): FloatArray? {
        src ?: return null
        val tmp = JomlPools.vec3f.borrow()
        for (i in src.indices step stride) {
            tmp.set(src, i)
            matrix.transformDirection(tmp)
                .safeNormalize()
            tmp.get(src, i)
        }
        return src
    }
}