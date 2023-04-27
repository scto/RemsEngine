package me.anno.tests.physics

import me.anno.ecs.Entity
import me.anno.ecs.components.audio.AudioComponent
import me.anno.ecs.components.collider.BoxCollider
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.physics.BulletPhysics
import me.anno.ecs.components.physics.Rigidbody
import me.anno.engine.ECSRegistry
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.io.ISaveable.Companion.registerCustomClass
import me.anno.mesh.Shapes.flatCube
import me.anno.utils.OS.music
import me.anno.utils.structures.maps.KeyPairMap
import org.joml.Vector3d
import org.joml.Vector3f

// physics demo: dominos like https://www.youtube.com/watch?v=YZxky260O-4
// place / program / draw dominos
// done click to start it, maybe red domino or impulse, or falling ball :)
//  somehow dragging while playing is supported :)
fun main() {

    ECSRegistry.initMeshes()
    registerCustomClass(Rigidbody())
    registerCustomClass(BoxCollider())
    registerCustomClass(BulletPhysics())
    registerCustomClass(AudioComponent())

    val inch = 1f // 2.54e-2f
    val width = 17f / 16f * inch
    val height = 35f / 16f * inch
    val thickness = 7f / 16f * inch

    // todo audio debug animation to show playing sources

    val scene = Entity("Scene")
    val audiosEntity = Entity("Audios")
    scene.add(audiosEntity)
    var audioIndex = 0
    val audios = Array(16) {
        AudioComponent().apply {
            source = music.getChild("domino-click.wav")
            maxDistance = inch * 250f
            val helper = Entity()
            helper.add(this)
            audiosEntity.add(helper)
        }
    }

    scene.add(object : BulletPhysics() {
        // make domino sound on every contact :3
        val contacts = KeyPairMap<Any, Any, Unit>()
        override fun step(dt: Long, printSlack: Boolean) {
            super.step(dt, printSlack)
            val dispatcher = world?.dispatcher ?: return
            val numManifolds = dispatcher.numManifolds
            for (i in 0 until numManifolds) {
                val contactManifold = dispatcher.getManifoldByIndexInternal(i) ?: break
                if (contactManifold.numContacts < 1) continue
                val a = contactManifold.body0
                val b = contactManifold.body1
                contacts.getOrPut(a, b) { _, _ ->
                    val audio = audios[(audioIndex++) % (audios.size - 1)]
                    audio.stop()
                    // to do choose source randomly from random set of sounds
                    // place audio at the correct position
                    val pos = contactManifold.getContactPoint(0).positionWorldOnA
                    audio.entity!!.position = audio.entity!!.position
                        .set(pos.x, pos.y, pos.z)
                    audio.start()
                }
            }
        }
    }.apply {
        // updateInEditMode = true
        // fixedStep = 1.0 / 60.0
    })

    val density = 1.0
    val mass1 = width * height * thickness * density

    val halfExtends1 = Vector3d(width * 0.5, height * 0.5, thickness * 0.5)
    val mesh = flatCube.scaled(Vector3f(halfExtends1)).front.ref

    val dominos = Entity("Dominos")
    scene.add(dominos)
    fun add(x: Float, z: Float): Entity {
        // todo why are smaller bricks unstable?
        val domino = Entity()
        domino.add(MeshComponent(mesh).apply {
            isInstanced = true
        })
        domino.add(Rigidbody().apply {
            mass = mass1
            friction = 0.9
        })
        domino.add(BoxCollider().apply {
            halfExtends = halfExtends1
            margin = thickness / 10.0
        })
        domino.position = domino.position.set(x.toDouble(), halfExtends1.y, z.toDouble())
        dominos.add(domino)
        return domino
    }

    val floorHalfSize = 20.0 * inch
    val floors = Entity("Floors")
    scene.add(floors)
    for (z in -100..100) {
        for (x in 0..1) {
            val floor = Entity()
            floor.add(Rigidbody().apply {
                mass = 0.0
                friction = 0.9
            })
            floor.add(BoxCollider().apply {
                halfExtends.set(floorHalfSize)
                margin = floorHalfSize / 100.0
            })
            floor.add(MeshComponent(flatCube.scaled(Vector3f(floorHalfSize.toFloat())).front))
            floor.position = floor.position.set(2 * x * floorHalfSize, -floorHalfSize, 2 * z * floorHalfSize)
            floors.add(floor)
        }
    }

    // todo starting structure, and image support like in video

    val spacing = height * 0.7f
    for (i in -1000 until 1000) {
        add(0f, spacing * i)
    }

    // todo why are tons of bricks unstable?

    testSceneWithUI(scene) {
        it.renderer.renderMode = RenderMode.PHYSICS
    }
}