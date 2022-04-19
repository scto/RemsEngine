package me.anno.ecs.components.physics

import com.bulletphysics.BulletGlobals
import com.bulletphysics.collision.broadphase.DbvtBroadphase
import com.bulletphysics.collision.dispatch.CollisionDispatcher
import com.bulletphysics.collision.dispatch.CollisionObject
import com.bulletphysics.collision.dispatch.CollisionObject.ACTIVE_TAG
import com.bulletphysics.collision.dispatch.CollisionObject.DISABLE_DEACTIVATION
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration
import com.bulletphysics.collision.shapes.CollisionShape
import com.bulletphysics.collision.shapes.CompoundShape
import com.bulletphysics.dynamics.DiscreteDynamicsWorld
import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.dynamics.RigidBodyConstructionInfo
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver
import com.bulletphysics.dynamics.vehicle.DefaultVehicleRaycaster
import com.bulletphysics.dynamics.vehicle.RaycastVehicle
import com.bulletphysics.dynamics.vehicle.VehicleTuning
import com.bulletphysics.dynamics.vehicle.WheelInfo
import com.bulletphysics.linearmath.DefaultMotionState
import com.bulletphysics.linearmath.Transform
import cz.advel.stack.Stack
import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.components.collider.Collider
import me.anno.ecs.components.physics.constraints.Constraint
import me.anno.ecs.components.physics.events.FallenOutOfWorld
import me.anno.engine.physics.BulletDebugDraw
import me.anno.engine.ui.render.DrawAABB
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.render.RenderView.Companion.camPosition
import me.anno.engine.ui.render.RenderView.Companion.cameraMatrix
import me.anno.gpu.buffer.LineBuffer
import me.anno.input.Input
import me.anno.io.serialization.NotSerializedProperty
import me.anno.maths.Maths.clamp
import org.apache.logging.log4j.LogManager
import org.joml.AABBd
import org.joml.Matrix4x3d
import org.joml.Quaterniond
import org.lwjgl.glfw.GLFW
import javax.vecmath.Matrix4d
import javax.vecmath.Quat4d
import javax.vecmath.Vector3d
import kotlin.collections.set
import kotlin.math.max

class BulletPhysics() : Physics<Rigidbody, RigidBody>(Rigidbody::class) {

    companion object {

        private val LOGGER = LogManager.getLogger(BulletPhysics::class)

        fun castB(s: org.joml.Vector3d): Vector3d {
            val v = Stack.borrowVec()
            v.set(s.x, s.y, s.z)
            return v
        }

        fun castB(s: Quaterniond): Quat4d {
            val v = Stack.borrowQuat()
            v.set(s.x, s.y, s.z, s.z)
            return v
        }

        fun castB(x: Double, y: Double, z: Double): Vector3d {
            val v = Stack.borrowVec()
            v.set(x, y, z)
            return v
        }

        fun convertMatrix(ourTransform: Matrix4x3d, scale: org.joml.Vector3d): Matrix4d {
            // bullet does not support scale -> we always need to correct it
            val sx = 1.0 / scale.x
            val sy = 1.0 / scale.y
            val sz = 1.0 / scale.z
            return Matrix4d(// we have to transpose the matrix, because joml uses Axy and vecmath uses Ayx
                ourTransform.m00() * sx, ourTransform.m10() * sy, ourTransform.m20() * sz, ourTransform.m30(),
                ourTransform.m01() * sx, ourTransform.m11() * sy, ourTransform.m21() * sz, ourTransform.m31(),
                ourTransform.m02() * sx, ourTransform.m12() * sy, ourTransform.m22() * sz, ourTransform.m32(),
                0.0, 0.0, 0.0, 1.0
            )
        }
    }

    // todo onPreEnable() // before all children
    // todo onPostEnable() // after all children
    // -> components can be registered before/after enable :)

    constructor(base: BulletPhysics) : this() {
        base.copy(this)
    }

    // I use jBullet2, however I have modified it to use doubles for everything
    // this may be bad for performance, but it also allows our engine to run much larger worlds
    // if we need top-notch-performance, I just should switch to a native implementation

    // todo ideally for bullet, we would need a non-symmetric collision matrix:
    // this would allow for pushing, ignoring, and such
    //
    //   t y p e s
    // t
    // y
    // p  whether it can be moved by the other
    // e
    // s

    /*@DebugAction
    fun reset() {
        // todo reset everything...
        // todo index whole world...
    }*/

    @NotSerializedProperty
    private val sampleWheels = ArrayList<WheelInfo>()

    @NotSerializedProperty
    private lateinit var world: DiscreteDynamicsWorld

    @NotSerializedProperty
    private lateinit var raycastVehicles: HashMap<Entity, RaycastVehicle>

    override fun onCreate() {
        super.onCreate()
        raycastVehicles = HashMap()
        world = createBulletWorldWithGravity()
    }

    private fun createCollider(entity: Entity, colliders: List<Collider>, scale: org.joml.Vector3d): CollisionShape {
        val firstCollider = colliders.first()
        return if (colliders.size == 1 && firstCollider.entity === entity) {
            // there is only one, and no transform needs to be applied -> use it directly
            firstCollider.createBulletShape(scale)
        } else {
            val jointCollider = CompoundShape()
            for (collider in colliders) {
                val (transform, subCollider) = collider.createBulletCollider(entity, scale)
                jointCollider.addChildShape(transform, subCollider)
            }
            jointCollider
        }
    }

    override fun createRigidbody(entity: Entity, rigidBody: Rigidbody): BodyWithScale<RigidBody>? {

        val colliders = getValidComponents(entity, Collider::class).toList()
        return if (colliders.isNotEmpty()) {

            // bullet does not work correctly with scale changes: create larger shapes directly
            val globalTransform = entity.transform.globalTransform
            val scale = globalTransform.getScale(org.joml.Vector3d())

            // copy all knowledge from ecs to bullet
            val jointCollider = createCollider(entity, colliders, scale)

            val mass = max(0.0, rigidBody.mass)
            val inertia = Vector3d()
            if (mass > 0) jointCollider.calculateLocalInertia(mass, inertia)

            val bulletTransform = Transform(convertMatrix(globalTransform, scale))

            // convert the center of mass to a usable transform
            val com0 = rigidBody.centerOfMass
            val com1 = Vector3d(com0.x, com0.y, com0.z)
            val com2 = Transform(Matrix4d(Quat4d(0.0, 0.0, 0.0, 1.0), com1, 1.0))

            // create the motion state
            val motionState = DefaultMotionState(bulletTransform, com2)
            val rbInfo = RigidBodyConstructionInfo(mass, motionState, jointCollider, inertia)
            rbInfo.friction = rigidBody.friction
            rbInfo.restitution = rigidBody.restitution
            rbInfo.linearDamping = rigidBody.linearDamping
            rbInfo.angularDamping = rigidBody.angularDamping
            rbInfo.linearSleepingThreshold = rigidBody.linearSleepingThreshold
            rbInfo.angularSleepingThreshold = rigidBody.angularSleepingThreshold

            val rb = RigidBody(rbInfo)
            rb.deactivationTime = rigidBody.sleepingTimeThreshold
            BulletGlobals.setDeactivationTime(1.0)

            BodyWithScale(rb, scale)

        } else null

    }

    private fun defineVehicle(entity: Entity, rigidbody: Vehicle, body: RigidBody) {
        // todo correctly create vehicle, if the body is scaled
        val tuning = VehicleTuning()
        tuning.frictionSlip = rigidbody.frictionSlip
        tuning.suspensionDamping = rigidbody.suspensionDamping
        tuning.suspensionStiffness = rigidbody.suspensionStiffness
        tuning.suspensionCompression = rigidbody.suspensionCompression
        tuning.maxSuspensionTravelCm = rigidbody.maxSuspensionTravelCm
        val raycaster = DefaultVehicleRaycaster(world)
        val vehicle = RaycastVehicle(tuning, body, raycaster)
        vehicle.setCoordinateSystem(0, 1, 2)
        val wheels = getValidComponents(entity, VehicleWheel::class)
        for (wheel in wheels) {
            val info = wheel.createBulletInstance(entity, vehicle)
            wheel.bulletInstance = info
            sampleWheels.add(info)
        }
        // vehicle.currentSpeedKmHour
        // vehicle.applyEngineForce()
        world.addVehicle(vehicle)
        body.activationState = DISABLE_DEACTIVATION
        raycastVehicles[entity] = vehicle
    }

    override fun onCreateRigidbody(entity: Entity, rigidbody: Rigidbody, bodyWithScale: BodyWithScale<RigidBody>) {

        val body = bodyWithScale.body

        // vehicle stuff
        if (rigidbody is Vehicle) {
            defineVehicle(entity, rigidbody, body)
        }

        // activate
        if (rigidbody.activeByDefault) body.activationState = ACTIVE_TAG

        world.addRigidBody(body, clamp(rigidbody.group, 0, 15).toShort(), rigidbody.collisionMask)

        // must be done after adding the body to the world,
        // because it is overridden by World.addRigidbody()
        if (rigidbody.overrideGravity) {
            body.setGravity(castB(rigidbody.gravity))
        }

        rigidBodies[entity] = bodyWithScale
        rigidbody.bulletInstance = body

        if (!rigidbody.isStatic) {
            nonStaticRigidBodies[entity] = bodyWithScale
        } else {
            nonStaticRigidBodies.remove(entity)
        }

        for (c in rigidbody.constrained) {
            // ensure the constraint exists
            val rigidbody2 = c.entity!!.rigidbodyComponent!!
            addConstraint(c, getRigidbody(rigidbody2)!!, rigidbody2, rigidbody)
        }

        // create all constraints
        entity.allComponents(Constraint::class, false) { c ->
            val other = c.other
            if (other != null && other != rigidbody && other.isEnabled) {
                addConstraint(c, body, rigidbody, other)
            }
            false
        }
    }

    private fun addConstraint(c: Constraint<*>, body: RigidBody, rigidbody: Rigidbody, other: Rigidbody) {
        val oldInstance = c.bulletInstance
        if (oldInstance != null) {
            world.removeConstraint(oldInstance)
            c.bulletInstance = null
        }
        if (!rigidbody.isStatic || !other.isStatic) {
            val otherBody = getRigidbody(other)
            if (otherBody != null) {
                // create constraint
                val constraint = c.createConstraint(body, otherBody, c.getTA(), c.getTB())
                c["bulletInstance"] = constraint
                world.addConstraint(constraint, c.disableCollisionsBetweenLinked)
                if (oldInstance != null) {
                    LOGGER.debug("* ${c.prefabPath}")
                } else {
                    LOGGER.debug("+ ${c.prefabPath}")
                }
            }
        } else {
            LOGGER.warn("Cannot constrain two static bodies!, ${rigidbody.prefabPath} to ${other.prefabPath}")
        }
    }

    override fun remove(entity: Entity, fallenOutOfWorld: Boolean) {
        super.remove(entity, fallenOutOfWorld)
        entity.allComponents(Constraint::class) {
            val bi = it.bulletInstance
            if (bi != null) {
                it.bulletInstance = null
                world.removeConstraint(bi)
                LOGGER.debug("- ${it.prefabPath}")
            }
            false
        }
        val rigid2 = entity.rigidbodyComponent
        if (rigid2 != null) {
            for (c in rigid2.constrained) {
                val bi = c.bulletInstance
                if (bi != null) {
                    world.removeConstraint(bi)
                    c.bulletInstance = null
                    LOGGER.debug("- ${c.prefabPath}")
                }
            }
        }
        val vehicle = raycastVehicles.remove(entity) ?: return
        world.removeVehicle(vehicle)
        entity.isPhysicsControlled = false
        if (fallenOutOfWorld) {
            if (rigid2 != null) {
                // when something falls of the world, often it's nice to directly destroy the object,
                // because it will no longer be needed
                // call event, so e.g. we could add it back to a pool of entities, or respawn it
                entity.allComponents(Component::class) {
                    if (it is FallenOutOfWorld) it.onFallOutOfWorld()
                    false
                }
                if (rigid2.deleteWhenKilledByDepth) {
                    entity.parentEntity?.deleteChild(entity)
                }
            }
        }
    }

    override fun step(dt: Long, printSlack: Boolean) {
        // just in case
        Stack.reset(printSlack)
        super.step(dt, printSlack)
    }

    override fun worldStepSimulation(step: Double) {
        world.stepSimulation(step, 1, step)
    }

    override fun isActive(rigidbody: RigidBody): Boolean {
        return rigidbody.isActive
    }

    override fun worldRemoveRigidbody(rigidbody: RigidBody) {
        world.removeRigidBody(rigidbody)
    }

    override fun convertTransformMatrix(rigidbody: RigidBody, scale: org.joml.Vector3d, dstTransform: Matrix4x3d) {

        val tmpTransform = Stack.borrowTrans()
        // set the global transform
        rigidbody.getWorldTransform(tmpTransform)

        val basis = tmpTransform.basis
        val origin = tmpTransform.origin
        // bullet/javax uses normal ij indexing, while joml uses ji indexing
        val sx = scale.x
        val sy = scale.y
        val sz = scale.z

        dstTransform.set(
            basis.m00 * sx, basis.m10 * sy, basis.m20 * sz,
            basis.m01 * sx, basis.m11 * sy, basis.m21 * sz,
            basis.m02 * sx, basis.m12 * sy, basis.m22 * sz,
            origin.x, origin.y, origin.z
        )

    }

    private fun drawDebug(view: RenderView, worldScale: Double) {

        val debugDraw = debugDraw ?: return

        // define camera transform
        debugDraw.stack.set(cameraMatrix)
        debugDraw.worldScale = worldScale
        debugDraw.cam.set(camPosition)

        if (view.renderMode == RenderMode.PHYSICS) {
            drawContactPoints(view)
            drawAABBs(view)
            drawVehicles(view)
            LineBuffer.finish(cameraMatrix)
        }

    }

    private fun drawContactPoints(view: RenderView) {
        val dispatcher = world.dispatcher
        val numManifolds: Int = dispatcher.numManifolds
        for (i in 0 until numManifolds) {
            val contactManifold = dispatcher.getManifoldByIndexInternal(i) ?: break
            val numContacts = contactManifold.numContacts
            for (j in 0 until numContacts) {
                val cp = contactManifold.getContactPoint(j)
                DrawAABB.drawLine(
                    cp.positionWorldOnB,
                    Vector3d(cp.positionWorldOnB).apply { add(cp.normalWorldOnB) },
                    view.worldScale, 0x777777
                )
            }
        }
    }

    private fun drawAABBs(view: RenderView) {

        val tmpTrans = Stack.newTrans()
        val minAabb = Vector3d()
        val maxAabb = Vector3d()

        val collisionObjects = world.collisionObjectArray

        val worldScale = view.worldScale

        for (i in 0 until collisionObjects.size) {

            val colObj = collisionObjects.getQuick(i) ?: break
            val color = when (colObj.activationState) {
                CollisionObject.ACTIVE_TAG -> -1
                CollisionObject.ISLAND_SLEEPING -> 0x00ff00
                CollisionObject.WANTS_DEACTIVATION -> 0x00ffff
                CollisionObject.DISABLE_DEACTIVATION -> 0xff0000
                CollisionObject.DISABLE_SIMULATION -> 0xffff00
                else -> 0xff0000
            }

            // todo draw the local coordinate arrows
            // debugDrawObject(colObj.getWorldTransform(tmpTrans), colObj.collisionShape, color)

            colObj.collisionShape.getAabb(colObj.getWorldTransform(tmpTrans), minAabb, maxAabb)

            DrawAABB.drawAABB(
                AABBd()
                    .setMin(minAabb.x, minAabb.y, minAabb.z)
                    .setMax(maxAabb.x, maxAabb.y, maxAabb.z),
                worldScale,
                color
            )
        }

    }

    private fun drawVehicles(view: RenderView) {

        val wheelPosWS = Vector3d()
        val axle = Vector3d()
        val tmp = Stack.newVec()

        val worldScale = view.worldScale

        val vehicles = world.vehicles
        for (i in 0 until vehicles.size) {
            val vehicle = vehicles.getQuick(i) ?: break
            for (v in 0 until vehicle.numWheels) {

                val wheelColor = if (vehicle.getWheelInfo(v).raycastInfo.isInContact) {
                    0x0000ff
                } else {
                    0xff0000
                }

                wheelPosWS.set(vehicle.getWheelInfo(v).worldTransform.origin)
                axle.set(
                    vehicle.getWheelInfo(v).worldTransform.basis.getElement(0, vehicle.rightAxis),
                    vehicle.getWheelInfo(v).worldTransform.basis.getElement(1, vehicle.rightAxis),
                    vehicle.getWheelInfo(v).worldTransform.basis.getElement(2, vehicle.rightAxis)
                )

                tmp.add(wheelPosWS, axle)
                DrawAABB.drawLine(wheelPosWS, tmp, worldScale, wheelColor)
                DrawAABB.drawLine(
                    wheelPosWS, vehicle.getWheelInfo(v).raycastInfo.contactPointWS,
                    worldScale, wheelColor
                )

            }
        }

        val actions = world.actions
        for (i in 0 until actions.size) {
            val action = actions.getQuick(i) ?: break
            action.debugDraw(debugDraw)
        }

    }

    private var debugDraw: BulletDebugDraw? = null
    override fun onDrawGUI(all: Boolean) {
        super.onDrawGUI(all)
        val view = RenderView.currentInstance!!
        drawDebug(view, view.worldScale)
    }

    override fun onUpdate(): Int {
        testVehicleControls()
        return 1
    }

    private fun testVehicleControls() {

        var steering = 0.0
        var engineForce = 0.0
        var brakeForce = 0.0

        if (GLFW.GLFW_KEY_SPACE in Input.keysDown) brakeForce++
        if (GLFW.GLFW_KEY_UP in Input.keysDown) engineForce++
        if (GLFW.GLFW_KEY_DOWN in Input.keysDown) engineForce--
        if (GLFW.GLFW_KEY_LEFT in Input.keysDown) steering++
        if (GLFW.GLFW_KEY_RIGHT in Input.keysDown) steering--

        try {
            val wheels = sampleWheels
            for (index in wheels.indices) {
                val wheel = wheels[index]
                wheel.engineForce = if (wheel.bIsFrontWheel) 0.0 else engineForce
                wheel.steering = if (wheel.bIsFrontWheel) steering * 0.5 else 0.0
                wheel.brake = brakeForce
            }
        } catch (e: ConcurrentModificationException) {
            // will flicker a little, when cars are spawned/de-spawned
        }

    }

    private fun createBulletWorld(): DiscreteDynamicsWorld {
        val collisionConfig = DefaultCollisionConfiguration()
        val dispatcher = CollisionDispatcher(collisionConfig)
        val bp = DbvtBroadphase()
        val solver = SequentialImpulseConstraintSolver()
        val world = DiscreteDynamicsWorld(dispatcher, bp, solver, collisionConfig)
        debugDraw = debugDraw ?: BulletDebugDraw()
        world.debugDrawer = debugDraw
        return world
    }

    private fun createBulletWorldWithGravity(): DiscreteDynamicsWorld {
        val world = createBulletWorld()
        val tmp = Stack.borrowVec()
        tmp.set(gravity.x, gravity.y, gravity.z)
        world.setGravity(tmp)
        return world
    }

    override fun updateGravity() {
        val tmp = Stack.borrowVec()
        tmp.set(gravity.x, gravity.y, gravity.z)
        world.setGravity(tmp)
    }

    override fun clone() = BulletPhysics(this)

    override val className: String = "BulletPhysics"

}