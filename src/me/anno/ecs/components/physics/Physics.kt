package me.anno.ecs.components.physics

import me.anno.Engine
import me.anno.Time
import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.EntityQuery.getComponent
import me.anno.ecs.EntityQuery.hasComponent
import me.anno.ecs.annotations.DebugAction
import me.anno.ecs.annotations.DebugProperty
import me.anno.ecs.annotations.Docs
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.Events.addEvent
import me.anno.engine.RemsEngine
import me.anno.engine.serialization.NotSerializedProperty
import me.anno.engine.serialization.SerializedProperty
import me.anno.engine.ui.render.PlayMode
import me.anno.engine.ui.render.RenderView
import me.anno.maths.Maths.MILLIS_TO_NANOS
import me.anno.ui.debug.FrameTimings
import me.anno.utils.Color.black
import me.anno.utils.Logging.hash32
import me.anno.utils.structures.sets.ParallelHashSet
import me.anno.utils.types.Floats.f1
import org.apache.logging.log4j.LogManager
import org.joml.AABBd
import org.joml.Matrix4x3d
import org.joml.Vector3d
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.reflect.KClass

abstract class Physics<InternalRigidBody : Component, ExternalRigidBody>(
    val rigidComponentClass: KClass<InternalRigidBody>
) : Component() {

    companion object {
        private val LOGGER = LogManager.getLogger(Physics::class)
    }

    // entities outside these bounds will be killed
    @SerializedProperty
    var allowedSpace: AABBd = AABBd()
        .setMin(-1e300, -100.0, -1e300)
        .setMax(+1e300, 1e300, +1e300)

    @SerializedProperty
    var gravity: Vector3d = Vector3d(0.0, -9.81, 0.0)
        set(value) {
            field = value
            updateGravity()
        }

    @NotSerializedProperty
    val invalidEntities = ParallelHashSet<Entity>(256)

    @NotSerializedProperty
    val rigidBodies = HashMap<Entity, BodyWithScale<ExternalRigidBody>?>()

    @NotSerializedProperty
    val nonStaticRigidBodies = HashMap<Entity, BodyWithScale<ExternalRigidBody>>()

    @SerializedProperty
    var targetUpdatesPerSecond = 30.0

    @DebugProperty
    val numNonStatic get() = nonStaticRigidBodies.size

    @DebugProperty
    val numRigidbodies get() = rigidBodies.size

    @DebugProperty
    val numInvalid get() = invalidEntities.size

    @DebugProperty
    @NotSerializedProperty
    var timeNanos = 0L

    var printValidations = false

    abstract fun updateGravity()

    @DebugAction
    fun invalidateAll() {
        entity?.forAll {
            if (rigidComponentClass.isInstance(it)) {
                val e = (it as? Component)?.entity
                if (e != null) invalidate(e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        rigidBodies.clear()
        nonStaticRigidBodies.clear()
    }

    override fun onChangeStructure(entity: Entity) {
        super.onChangeStructure(entity)
        invalidateRecursively(entity)
    }

    fun registerNonStatic(entity: Entity, isStatic: Boolean, bodyWithScale: BodyWithScale<ExternalRigidBody>){
        if (isStatic) {
            nonStaticRigidBodies.remove(entity)
        } else {
            nonStaticRigidBodies[entity] = bodyWithScale
        }
    }

    fun invalidateRecursively(entity: Entity) {
        val rb = entity.getComponent(rigidComponentClass)
        if (rb != null) {
            getInit(entity)
        } else {
            for (child in entity.children) {
                invalidateRecursively(child)
            }
        }
    }

    open fun invalidate(entity: Entity) {
        if (printValidations) LOGGER.debug("Invalidated {}", hash32(this))
        invalidEntities.add(entity)
    }

    open fun invalidateTransform(entity: Entity) {
        invalidate(entity)
    }

    fun validate() {
        if (printValidations) LOGGER.debug("Validating {}", hash32(this))
        invalidEntities.process2x({ entity ->
            remove(entity, false)
            removeConstraints(entity)
        }, ::getInit)
    }

    private fun getInit(entity: Entity) {
        val rigidbody = addOrGet(entity)
        entity.isPhysicsControlled = rigidbody != null
    }

    abstract fun removeConstraints(entity: Entity)

    fun <V : Component> getValidComponents(entity: Entity, clazz: KClass<V>): Sequence<V> {
        // only collect colliders, which are appropriate for this: stop at any other rigidbody
        return sequence {
            @Suppress("unchecked_cast")
            yieldAll(entity.components.filter { it.isEnabled && clazz.isInstance(it) } as List<V>)
            for (child in entity.children) {
                if (child.isEnabled && !child.hasComponent(rigidComponentClass, false)) {
                    yieldAll(getValidComponents(child, clazz))
                }
            }
        }
    }

    open fun remove(entity: Entity, fallenOutOfWorld: Boolean) {
        val rigid = rigidBodies.remove(entity) ?: return
        // LOGGER.debug("- ${entity.prefabPath}")
        nonStaticRigidBodies.remove(entity)
        worldRemoveRigidbody(rigid.body)
    }

    abstract fun createRigidbody(
        entity: Entity,
        rigidBody: InternalRigidBody
    ): BodyWithScale<ExternalRigidBody>?

    abstract fun onCreateRigidbody(
        entity: Entity,
        rigidbody: InternalRigidBody,
        bodyWithScale: BodyWithScale<ExternalRigidBody>
    )

    fun getRigidbody(rigidBody: InternalRigidBody): ExternalRigidBody? {

        // todo when a rigidbody is invalidated, also re-create all constrained rigidbodies!
        // todo otherwise we'll get issues, where one partner no longer is part of the world...

        // todo possible solution: instead of recreating the rigidbody instance, just clear all properties,
        // todo and write them again :)

        // todo also we need to somehow ensure, that constrained rigidbodies are enabled as well
        // todo we can't have constraints between two static rigidbodies

        val entity = rigidBody.entity!!
        entity.validateTransform()

        var newlyCreated = false
        val bodyWithScale = rigidBodies.getOrPut(entity) {
            newlyCreated = true
            createRigidbody(entity, rigidBody)
        }
        if (newlyCreated && bodyWithScale != null) {
            // after creating and registering, so
            // it works for circular constraint dependencies
            onCreateRigidbody(entity, rigidBody, bodyWithScale)
            // LOGGER.debug("+ ${entity.prefabPath}")
        }
        return bodyWithScale?.body
    }

    fun addOrGet(entity: Entity): ExternalRigidBody? {
        // LOGGER.info("adding ${entity.name} maybe, ${entity.getComponent(Rigidbody::class, false)}")
        val rigidbody = entity.getComponent(rigidComponentClass, false) ?: return null
        return if (rigidbody.isEnabled) getRigidbody(rigidbody) else null
    }

    private fun callUpdates(dt: Double) {
        for (body in rigidBodies.keys) {
            physicsUpdate(body, dt)
        }
    }

    fun physicsUpdate(self: Entity, dt: Double) {
        // called by physics thread
        // only called for rigidbodies
        // not called for static objects (?), since they should not move
        val components = self.components
        for (i in components.indices) {
            val c = components[i]
            if (c.isEnabled) {
                c.onPhysicsUpdate(dt)
            }
        }
    }

    @Docs("If the game hang for that many milliseconds, the physics will stop being simulated, and be restarted on the next update")
    @SerializedProperty
    var simulationTimeoutMillis = 5000L

    @Docs("Whether physics should be executed in the editing environment")
    @SerializedProperty
    var updateInEditMode = false

    @NotSerializedProperty
    var lastUpdate = 0L

    @NotSerializedProperty
    private var workerThread: Thread? = null

    // todo transforms are not 100% stable with async physics
    @SerializedProperty
    var synchronousPhysics = true
        set(value) {
            if (field != value) {
                stopWorker()
                field = value
            }
        }

    var showDebug = false

    @DebugAction
    fun reloadScene() {
        // todo root cannot be restored, why?
        val selected = RemsEngine.collectSelected()
        root.prefab?.invalidateInstance()
        RemsEngine.restoreSelected(selected)
    }

    private fun startWorker() {
        workerThread = thread(name = className) {
            try {
                while (!Engine.shutdown) {

                    val targetUPS = targetUpdatesPerSecond
                    val targetStep = 1.0 / targetUPS
                    val targetStepNanos = (targetStep * 1e9).toLong()

                    // stop if received updates for no more than 1-3s
                    val targetTime = Time.nanoTime
                    if (abs(targetTime - lastUpdate) > simulationTimeoutMillis * MILLIS_TO_NANOS) {
                        LOGGER.debug(
                            "Stopping work, {} > {}",
                            (targetTime - lastUpdate) / 1e6,
                            simulationTimeoutMillis
                        )
                        break
                    }

                    // the absolute worst case time
                    val absMinimumTime = targetTime - targetStepNanos * 2
                    if (timeNanos < absMinimumTime) {
                        // report this value somehow...
                        // there may be lots and lots of warnings, if the calculations are too slow
                        val delta = absMinimumTime - timeNanos
                        timeNanos = absMinimumTime
                        val warning = "Physics skipped ${(delta * 1e-9).f1()}s"
                        lastWarning = warning
                        LOGGER.warn(warning)
                    }

                    if (timeNanos > targetTime) {
                        // done :), sleep
                        Thread.sleep((timeNanos - targetTime) / (2 * MILLIS_TO_NANOS))
                    } else {
                        // there is still work to do
                        val t0 = Time.nanoTime
                        val debug = false //Engine.gameTime > 10e9 // wait 10s
                        step(targetStepNanos, debug)
                        val t1 = Time.nanoTime
                        addEvent { FrameTimings.putValue((t1 - t0) * 1e-9f, 0xffff99 or black) }
                    }
                }
            } catch (e: InterruptedException) {
                // we were stopped, which is fine
            } finally {
                workerThread = null
            }
        }
    }

    private fun stopWorker() {
        synchronized(this) {
            workerThread?.interrupt()
            workerThread = null
        }
    }

    override fun onUpdate(): Int {
        super.onUpdate()
        lastUpdate = Time.nanoTime
        val shallExecute = updateInEditMode || RenderView.currentInstance?.playMode != PlayMode.EDITING
        if (shallExecute) {
            if (synchronousPhysics) {
                step((Time.deltaTime * 1e9).toLong(), false)
            } else {
                if (isEnabled) {
                    if (workerThread == null) {
                        startWorker()
                    }
                } else stopWorker()
            }
        } else stopWorker()
        return 1
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWorker()
    }

    abstract fun worldStepSimulation(step: Double)

    abstract fun worldRemoveRigidbody(rigidbody: ExternalRigidBody)

    abstract fun isActive(rigidbody: ExternalRigidBody): Boolean

    abstract fun convertTransformMatrix(
        rigidbody: ExternalRigidBody, scale: Vector3d,
        dstTransform: Matrix4x3d
    )

    @DebugAction
    fun manualStep() {
        // dt = 1e9 / 60
        step(16_666_667L, true)
    }

    open fun step(dt: Long, printSlack: Boolean) {

        // clock.start()

        // val oldSize = rigidBodies.size
        validate()
        // val newSize = rigidBodies.size
        // clock.stop("added ${newSize - oldSize} entities")

        val step = dt * 1e-9
        callUpdates(step)
        worldStepSimulation(step)

        // clock.stop("calculated changes, step ${dt * 1e-9}", 0.1)

        this.timeNanos += dt

        // is incorrect for the physics, but we use it for gfx only anyway
        // val time = Engine.gameTime

        val deadEntities = ArrayList<Entity>()
        val deadRigidBodies = ArrayList<ExternalRigidBody>()

        for ((entity, rigidbodyWithScale) in nonStaticRigidBodies) {

            val (rigidbody, scale) = rigidbodyWithScale
            if (!isActive(rigidbody)) continue

            val dst = entity.transform
            val dstTransform = dst.globalTransform
            convertTransformMatrix(rigidbody, scale, dstTransform)

            dst.setStateAndUpdate(me.anno.ecs.Transform.State.VALID_GLOBAL)

            if (!allowedSpace.testPoint(dstTransform.m30, dstTransform.m31, dstTransform.m32)) {
                // delete the entity
                deadEntities.add(entity)
                deadRigidBodies.add(rigidbody)
                continue
            }
        }

        updateWheels()

        for (i in deadEntities.indices) {
            remove(deadEntities[i], true)
            worldRemoveRigidbody(deadRigidBodies[i])
        }

        // update the local transforms last, so all global transforms have been completely updated
        for ((entity, bodyWithScale) in nonStaticRigidBodies) {
            if (!isActive(bodyWithScale.body)) continue
            // val dst = entity.transform
            // dst.calculateLocalTransform((entity.parent as? Entity)?.transform)
            entity.invalidateAABBsCompletely()
            entity.invalidateChildTransforms()
            entity.validateTransform()
        }

        // clock.total("physics step", 0.1)
    }

    open fun updateWheels() {
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as Physics<*, *>
        dst.allowedSpace.set(allowedSpace)
        dst.targetUpdatesPerSecond = targetUpdatesPerSecond
        dst.timeNanos = timeNanos
        dst.synchronousPhysics = synchronousPhysics
        dst.gravity.set(gravity)
    }
}