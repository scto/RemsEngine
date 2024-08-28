package me.anno.tests.physics

import com.bulletphysics.collision.shapes.CollisionShape
import com.bulletphysics.collision.shapes.ConvexShape
import com.bulletphysics.linearmath.Transform
import me.anno.bullet.BulletPhysics
import me.anno.bullet.Rigidbody
import me.anno.bullet.createBulletShape
import me.anno.ecs.Entity
import me.anno.ecs.components.collider.BoxCollider
import me.anno.ecs.components.collider.SphereCollider
import me.anno.ecs.systems.Systems
import me.anno.engine.ui.render.SceneView.Companion.testScene
import me.anno.sdf.SDFCollider
import me.anno.sdf.shapes.SDFBox
import me.anno.sdf.shapes.SDFSphere
import me.anno.ui.debug.TestEngine.Companion.testUI
import me.anno.utils.structures.lists.Lists.createList
import org.joml.Vector3d
import java.util.Random

fun main() {

    // todo create reliable sdf physics

    // done -> create scene with default mesh
    // todo -> compare the functions of their colliders with our custom implementation

    fun test0() {
        val scene = Entity()
        val physics = BulletPhysics()
        Systems.registerSystem(physics)
        physics.updateInEditMode = true

        Entity("SphereCollider", scene)
            .add(Rigidbody().apply { mass = 0.0 })
            .add(SphereCollider())
            .setPosition(0.0, -20.0, 0.0)
            .setScale(20.0)
            .add(SDFSphere())

        Entity("BoxCollider", scene)
            .add(Rigidbody().apply { mass = 1.0 })
            .add(BoxCollider())
            .setPosition(-3.3, 15.0, 0.0)
            .setScale(3.0)
            .add(SDFBox())

        Entity("SDFCollider", scene)
            .add(Rigidbody().apply { mass = 1.0 })
            .add(SDFCollider().apply { isConvex = true })
            .setPosition(+3.3, 15.0, 0.0)
            .setScale(3.0)
            .add(SDFBox())

        testUI("SDF Physics") {
            testScene(scene)
        }
    }

    fun test1() {

        val random = Random(1234L)
        val samples = createList(64) {
            javax.vecmath.Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
        }

        println(samples)

        fun print(shape: CollisionShape) {

            // convex sdf collisions shapes look quite good now :)
            // todo scaled convex shapes don't work correctly, looks as if it wasn't scaled

            println("${shape.isConvex}, ${shape.isConvex}, ${shape.isCompound}, ${shape.isPolyhedral}, ${shape.isInfinite}")
            val min = javax.vecmath.Vector3d()
            val max = javax.vecmath.Vector3d()
            val tr = Transform()
            tr.setIdentity()
            shape.getAabb(tr, min, max)
            println("$min - $max")
            println(shape.margin)
            println(shape.shapeType)
            println(shape.getBoundingSphere(javax.vecmath.Vector3d()))
            shape as ConvexShape
            println("${shape.numPreferredPenetrationDirections}") // only used for hull building
            println(samples.map { shape.localGetSupportingVertex(it, javax.vecmath.Vector3d()) })
            println(samples.map { shape.localGetSupportingVertexWithoutMargin(it, javax.vecmath.Vector3d()) })
            println(samples.map {
                val out = arrayOf(javax.vecmath.Vector3d())
                shape.batchedUnitVectorGetSupportingVertexWithoutMargin(
                    arrayOf(it),
                    out,
                    1
                )
                out[0]
            })
            println()
        }

        val shape0 = BoxCollider().createBulletShape(Vector3d(1.0))
        val shape1c = SDFCollider()
        val entity = Entity()
        entity.add(SDFBox())
        entity.add(shape1c)
        val shape1 = shape1c.createBulletCollider(Vector3d(1.0))
        print(shape0)
        print(shape1)
    }

    test1()
    test0()
}