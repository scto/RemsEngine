package me.anno.ecs.components.camera.control

import me.anno.Time
import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.Transform
import me.anno.ecs.annotations.Range
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.camera.Camera
import me.anno.ecs.components.player.LocalPlayer
import me.anno.ecs.interfaces.InputListener
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.ui.render.RenderView
import me.anno.input.Input
import me.anno.input.Input.isKeyDown
import me.anno.input.Input.isShiftDown
import me.anno.input.Key
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.maths.Maths.PIf
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.dtTo01
import me.anno.utils.types.Floats.toRadians
import org.joml.Vector3f

abstract class CameraController : Component(), InputListener {

    companion object {
        fun setup(controller: CameraController, renderView: RenderView): Entity {
            val base = Entity("CamBase")
            val rota = Entity("CamRot")
            val cam = Camera()
            base.add(rota)
            rota.add(cam)
            base.add(controller)
            controller.camera = cam
            val player = LocalPlayer()
            cam.use(player)
            renderView.localPlayer = player
            return base
        }
    }

    var needsClickToRotate = false
    var rotateLeft = false
    var rotateMiddle = false
    var rotateRight = false

    val acceleration = Vector3f()
    val velocity = Vector3f()
    val position = Vector3f()

    /**
     * euler yxz rotation in radians, where x=x,y=y,z=z
     * */
    var rotation = Vector3f()

    var movementSpeed = 1f
    var rotationSpeed = 0.3f

    @Type("Camera/SameSceneRef")
    var camera: Camera? = null

    @Range(0.0, 100.0)
    var friction = 5f

    /** whether the motion will be rotated by the camera looking direction */
    var rotateAngleY = true
    var rotateAngleX = false
    var rotateAngleZ = false

    /** set to 0 to disable numpad as mouse; degrees per second */
    var numpadAsMouseSpeed = 90f
    var numpadWheelSpeed = 1f

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as CameraController
        dst.camera = getInClone(camera, dst)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(null, "camera", camera)
    }

    /**
     * can prevent velocities from being applied, e.g., when running against a wall (like in Digital Campus)
     * */
    open fun clampVelocity() {
    }

    open fun clampRotation() {
        rotation.x = clamp(rotation.x, -PIf * 0.5f, PIf * 0.5f)
    }

    /**
     * define the acceleration in [-1 .. +1] range. Larger values will be clamped.
     * */
    open fun collectInputs(acceleration: Vector3f) {
        // todo replace this with actions, so it can be configured easily
        val s = movementSpeed
        if (isKeyDown(Key.KEY_W) || isKeyDown(Key.KEY_ARROW_UP)) acceleration.z -= s
        if (isKeyDown(Key.KEY_S) || isKeyDown(Key.KEY_ARROW_DOWN)) acceleration.z += s
        if (isKeyDown(Key.KEY_A) || isKeyDown(Key.KEY_ARROW_LEFT)) acceleration.x -= s
        if (isKeyDown(Key.KEY_D) || isKeyDown(Key.KEY_ARROW_RIGHT)) acceleration.x += s
        if (isShiftDown || isKeyDown('q')) acceleration.y -= s
        if (isKeyDown(' ') || isKeyDown('e')) acceleration.y += s
        if (numpadAsMouseSpeed != 0f || numpadWheelSpeed != 0f) {

            var dx = 0f
            var dy = 0f
            var dz = 0f

            if (isKeyDown("numpad4")) dx--
            if (isKeyDown("numpad6")) dx++
            if (isKeyDown("numpad8")) dy--
            if (isKeyDown("numpad2")) dy++
            if (isKeyDown("numpad7")) dz++
            if (isKeyDown("numpad1")) dz--

            if (dx != 0f || dy != 0f) {
                val dt = Time.deltaTime.toFloat() * numpadAsMouseSpeed * rotationSpeed
                rotation.add((dy * dt).toRadians(), (dx * dt).toRadians(), 0f)
                clampRotation()
            }

            if (dz != 0f) {
                val dt = Time.deltaTime.toFloat() * numpadWheelSpeed
                onMouseWheel(0f, 0f, 0f, dz * dt, false)
            }
        }
    }

    open fun clampAcceleration(acceleration: Vector3f) {
        val al = acceleration.length()
        if (al > movementSpeed) {
            acceleration.mul(movementSpeed / al)
        }
    }

    override fun onUpdate(): Int {

        val camera = camera
        val camEntity = camera?.entity
        val base = entity

        lastWarning = when {
            camera == null -> "Camera Missing"
            camEntity == null -> "Camera Missing Entity"
            base == null -> "Base Missing"
            else -> {

                lastWarning = null

                val dt = Time.deltaTime.toFloat()

                acceleration.set(0f)

                collectInputs(acceleration)

                clampAcceleration(acceleration)

                velocity.mul(1f - dtTo01(dt * friction))
                acceleration.mulAdd(dt, velocity, velocity)

                clampVelocity()

                velocity.mulAdd(dt, position, position)

                if (rotateAngleY) position.rotateY(rotation.y)
                if (rotateAngleX) position.rotateX(rotation.x)
                if (rotateAngleZ) position.rotateZ(rotation.z)

                computeTransform(base.transform, camEntity.transform, camera)

                return 1
            }
        }
        return 5
    }

    abstract fun computeTransform(baseTransform: Transform, camTransform: Transform, camera: Camera)

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float): Boolean {
        return if (!needsClickToRotate || (Input.isLeftDown && rotateLeft) || (Input.isMiddleDown && rotateMiddle) || (Input.isRightDown && rotateRight)) {
            rotation.add(-dy.toRadians() * rotationSpeed, -dx.toRadians() * rotationSpeed, 0f)
            clampRotation()
            // ... apply rotation to transform
            true
        } else false
    }
}