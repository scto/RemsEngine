package me.anno.engine.ui.control

import me.anno.Engine.deltaTime
import me.anno.config.DefaultConfig.style
import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.components.camera.Camera
import me.anno.engine.debug.DebugLine
import me.anno.engine.debug.DebugPoint
import me.anno.engine.debug.DebugShapes.debugLines
import me.anno.engine.debug.DebugShapes.debugPoints
import me.anno.engine.raycast.RayHit
import me.anno.engine.raycast.Raycast
import me.anno.engine.ui.EditorState
import me.anno.engine.ui.EditorState.control
import me.anno.engine.ui.EditorState.editMode
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.render.RenderView.Companion.camPosition
import me.anno.engine.ui.render.RenderView.Companion.mouseDir
import me.anno.gpu.GFX
import me.anno.input.Input
import me.anno.input.MouseButton
import me.anno.input.Touch
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.studio.StudioBase
import me.anno.ui.base.groups.NineTilePanel
import me.anno.ui.editor.PropertyInspector
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.types.Quaternions.toQuaternionDegrees
import me.anno.utils.types.Vectors.safeNormalize
import org.apache.logging.log4j.LogManager
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.sign

// todo touch controls

open class ControlScheme(val camera: Camera, val library: EditorState, val view: RenderView) :
    NineTilePanel(style) {

    constructor(view: RenderView) : this(view.editorCamera, view.library, view)

    override fun isOpaqueAt(x: Int, y: Int): Boolean = true

    val cameraNode = camera.entity!!

    val selectedEntities
        get() = library.selection.mapNotNull {
            when (it) {
                is Component -> it.entity
                is Entity -> it
                else -> null
            }
        }

    val selectedTransforms get() = selectedEntities.map { it.transform }

    val isSelected get() = uiParent!!.children.any2 { it.isInFocus }

    private val hit = RayHit()

    private val dirX = Vector3d()
    private val dirY = Vector3d()
    private val dirZ = Vector3d()
    private val rotQuad = Quaterniond()
    private val velocity = Vector3d()

    // transfer events
    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
        invalidateDrawing()
        if (control?.onMouseDown(button) == true) return
        if (editMode?.onEditDown(button) == true) return
        super.onMouseDown(x, y, button)
    }

    override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
        invalidateDrawing()
        if (control?.onMouseUp(button) == true) return
        if (editMode?.onEditUp(button) == true) return
        super.onMouseUp(x, y, button)
    }

    override fun onCharTyped(x: Float, y: Float, key: Int) {
        invalidateDrawing()
        if (control?.onCharTyped(key) == true) return
        if (editMode?.onEditTyped(key) == true) return
        super.onCharTyped(x, y, key)
    }

    override fun onKeyTyped(x: Float, y: Float, key: Int) {
        invalidateDrawing()
        if (control?.onKeyTyped(key) == true) return
        if (editMode?.onEditClick(key) == true) return
        super.onKeyTyped(x, y, key)
    }

    override fun onKeyDown(x: Float, y: Float, key: Int) {
        invalidateDrawing()
        if (control?.onKeyDown(key) == true) return
        if (editMode?.onEditDown(key) == true) return
        super.onKeyDown(x, y, key)
    }

    override fun onKeyUp(x: Float, y: Float, key: Int) {
        invalidateDrawing()
        if (control?.onKeyUp(key) == true) return
        if (editMode?.onEditUp(key) == true) return
        super.onKeyUp(x, y, key)
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        invalidateDrawing()
        if (control?.onMouseClicked(button, long) == true) return
        if (editMode?.onEditClick(button, long) == true) return
        selectObjectAtCursor(x, y)
        testHits()
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        invalidateDrawing()
        if (control?.onMouseMoved(x, y, dx, dy) == true) return
        if (editMode?.onEditMove(x, y, dx, dy) == true) return
        if (isSelected && Input.isRightDown) {
            rotateCamera(dx, dy)
        }
    }

    fun rotateCamera(dx: Float, dy: Float) {
        // right mouse key down -> move the camera
        val speed = -500f / Maths.max(GFX.height, h)
        rotateCamera(dy * speed, dx * speed, 0f)
    }

    // less than 90, so we always know forward when computing movement
    val limit = 90.0 - 0.0001

    fun rotateCameraTo(v: Vector3f) {
        view.rotation.set(v)
        view.updateEditorCameraTransform()
        invalidateDrawing()
    }

    fun rotateCamera(vx: Float, vy: Float, vz: Float) {
        view.rotation.apply {
            set(clamp(x + vx, -limit, +limit), y + vy, z + vz)
        }
        view.updateEditorCameraTransform()
        invalidateDrawing()
    }

    fun rotateCamera(v: Vector3f) {
        rotateCamera(v.x, v.y, v.z)
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        invalidateDrawing()
        if (control?.onMouseWheel(x, y, dx, dy, byMouse) == true) return
        // not supported, always will be zooming
        // if (editMode?.onEditWheel(x, y, dx, dy) == true) return
        if (isSelected) {
            val factor = Maths.pow(0.5f, dy / 16f)
            view.radius *= factor
            view.updateEditorCameraTransform()
            invalidateDrawing()
        }
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        if (control?.onGotAction(x, y, dx, dy, action) == true) return true
        return super.onGotAction(x, y, dx, dy, action, isContinuous)
    }

    private fun selectObjectAtCursor(x: Float, y: Float) {
        // select the clicked thing in the scene
        val (e, c) = view.resolveClick(x, y)
        // show the entity in the property editor
        // but highlight the specific mesh
        library.select(e ?: c?.entity, e ?: c)
    }

    open fun drawGizmos() {

    }

    /*override fun onKeyTyped(x: Float, y: Float, key: Int) {
        super.onKeyTyped(x, y, key)
        if (isSelected) {
            super.onKeyTyped(x, y, key)
            if (key in '1'.code..'9'.code) {
                view.selectedAttribute = key - '1'.code
            }
        }
    }*/

    fun invalidateInspector() {
        for (window in windowStack) {
            for (panel in window.panel.listOfVisible) {
                when (panel) {
                    is PropertyInspector -> {
                        panel.invalidate()
                    }
                }
            }
        }
    }

    private fun testHits() {
        val world = library.world
        if (world !is Entity) return
        world.validateMasks()
        world.validateAABBs()
        world.validateTransform()
        // mouseDir.mul(100.0)
        val cam = Vector3d(camPosition)
        // debugRays.add(DebugRay(cam, Vector3d(mouseDir), -1))
        // .add(camDirection.x * 20, camDirection.y * 20, camDirection.z * 20)
        // .add(Math.random()*20-10,Math.random()*20-10, Math.random()*20-10)
        val hit = Raycast.raycast(
            world, cam, mouseDir, 0.0, 1.0 / max(h, 1),
            view.radius * 1e3, Raycast.TypeMask.BOTH, -1, false, hit
        )
        if (hit == null) {
            // draw red point in front of the camera
            debugPoints.add(DebugPoint(Vector3d(mouseDir).mul(20.0).add(cam), 0xff0000))
        } else {
            val pos = Vector3d(hit.positionWS)
            val normal = Vector3d(hit.normalWS).normalize()
            // draw collision point
            debugPoints.add(DebugPoint(pos, -1))
            // draw collision normal
            debugLines.add(DebugLine(pos, normal.add(pos), 0x00ff00))
        }
    }

    open fun checkMovement() {
        val view = view
        val dt = deltaTime
        val factor = clamp(20.0 * dt, 0.0, 1.0)
        val velocity = velocity.mul(1.0 - factor)
        val radius = view.radius
        val s = factor * radius * 1.2
        if (isSelected) {
            if (Input.isKeyDown('a')) velocity.x -= s
            if (Input.isKeyDown('d')) velocity.x += s
            if (Input.isKeyDown('w')) velocity.z -= s
            if (Input.isKeyDown('s')) velocity.z += s
            if (Input.isKeyDown('q')) velocity.y -= s
            if (Input.isKeyDown('e')) velocity.y += s
        }
        moveCamera(velocity.x * dt, velocity.y * dt, velocity.z * dt)
        val position = view.position
        if (!position.isFinite) {
            LOGGER.warn("Invalid position $position from $velocity * mat($dirX, $dirY, $dirZ)")
            position.set(0.0)
            Thread.sleep(100)
        }
    }

    fun moveCamera(dx: Double, dy: Double, dz: Double) {
        val normXZ = !Input.isShiftDown // todo use UI toggle instead
        val rotQuad = view.rotation
            .toQuaternionDegrees(rotQuad)
            .invert()
        val dirX = dirX.set(1.0, 0.0, 0.0)
        val dirY = dirY.set(0.0, 1.0, 0.0)
        val dirZ = dirZ.set(0.0, 0.0, 1.0)
        rotQuad.transform(dirX)
        rotQuad.transform(dirZ)
        if (normXZ) {
            dirX.y = 0.0
            dirZ.y = 0.0
            dirX.safeNormalize()
            dirZ.safeNormalize()
        } else {
            rotQuad.transform(dirY)
        }
        view.position.add(
            dirX.dot(dx, dy, dz),
            dirY.dot(dx, dy, dz),
            dirZ.dot(dx, dy, dz)
        )
    }

    // to do call events before we draw the scene? that way we would not get the 1-frame delay
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        // no background
        if (view.renderMode == RenderMode.RAY_TEST) {
            testHits()
        }
        parseTouchInput()
        backgroundColor = backgroundColor and 0xffffff
        super.onDraw(x0, y0, x1, y1)
        checkMovement()
    }

    fun parseTouchInput() {

        val first = Touch.touches.values.firstOrNull()
        if (first != null && contains(first.x0, first.y0)) {

            when (Touch.touches.size) {
                0, 1 -> {
                } // handled like a mouse
                2 -> {

                    val speed = -120f * StudioBase.shiftSlowdown / GFX.height
                    // this gesture started on this view -> this is our gesture
                    // rotating is the hardest on a touchpad, because we need to click right
                    // -> rotation
                    val dx = Touch.sumDeltaX() * speed
                    val dy = Touch.sumDeltaY() * speed
                    rotateCamera(dy, dx, 0f)

                    // zoom in/out
                    val r = Touch.getZoomFactor()
                    view.radius *= r * r * sign(r) // power 1 is too slow

                    Touch.update()

                }
                else -> {

                    // move the camera around
                    val speed = -3f * view.radius / GFX.height

                    val dx = Touch.avgDeltaX() * speed
                    val dy = Touch.avgDeltaY() * speed
                    if (Input.isShiftDown)
                        moveCamera(dx, -dy, 0.0)
                    else
                        moveCamera(dx, 0.0, dy)

                    // zoom in/out
                    val r = Touch.getZoomFactor()
                    view.radius *= r * r * sign(r) // power 1 is too slow

                    Touch.update()

                }
            }
        }

    }

    companion object {
        private val LOGGER = LogManager.getLogger(ControlScheme::class)
    }

}