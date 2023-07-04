/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package me.anno.gpu

import me.anno.Build.isDebug
import me.anno.Engine
import me.anno.Engine.projectName
import me.anno.Engine.shutdown
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX.addGPUTask
import me.anno.gpu.GFX.checkIsGFXThread
import me.anno.gpu.GFX.focusedWindow
import me.anno.gpu.GFX.getErrorTypeName
import me.anno.gpu.debug.LWJGLDebugCallback
import me.anno.gpu.debug.OpenGLDebug.getDebugSeverityName
import me.anno.gpu.debug.OpenGLDebug.getDebugSourceName
import me.anno.gpu.debug.OpenGLDebug.getDebugTypeName
import me.anno.image.Image
import me.anno.image.ImageCPUCache
import me.anno.input.Input
import me.anno.input.Input.isMouseTrapped
import me.anno.input.Input.trapMouseWindow
import me.anno.io.files.BundledRef
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.language.translation.NameDesc
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.input.InputPanel
import me.anno.utils.Clock
import me.anno.utils.OS
import me.anno.utils.structures.lists.Lists.all2
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.none2
import org.apache.logging.log4j.LogManager.getLogger
import org.lwjgl.BufferUtils
import org.lwjgl.Version
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL43C.glDebugMessageCallback
import org.lwjgl.system.Callback
import org.lwjgl.system.MemoryUtil
import java.awt.AWTException
import java.awt.Robot
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * Showcases how you can use multithreading in a GLFW application
 * to separate the (blocking) winproc handling from the render loop.
 *
 * @author Kai Burjack
 *
 * modified by Antonio Noack
 * including all os natives has luckily only very few overhead :) (&lt; 1 MiB)
 *
 * todo rebuild and recompile the glfw driver, which handles the touch input, so the input can be assigned to the window
 * (e.g., add 1 to the pointer)
 */
object GFXBase {

    @JvmStatic
    private val LOGGER = getLogger(GFXBase::class)

    @JvmStatic
    private val windows get() = GFX.windows

    @JvmStatic
    private var debugMsgCallback: Callback? = null

    @JvmStatic
    private var errorCallback: GLFWErrorCallback? = null

    @JvmField
    val glfwLock = Any()

    @JvmField
    val openglLock = Any()

    @JvmField
    var destroyed = false

    @JvmField
    var capabilities: GLCapabilities? = null

    @JvmField
    var usesRenderDoc = false

    @JvmField
    val robot = try {
        Robot()
    } catch (e: AWTException) {
        e.printStackTrace()
        null
    }

    /** must be executed before OpenGL-init;
     * must be disabled for Nvidia Nsight */
    @JvmStatic
    private var disableRenderDoc = false

    @JvmStatic
    fun disableRenderDoc() {
        disableRenderDoc = true
    }

    @JvmStatic
    fun loadRenderDoc() {
        val enabled = DefaultConfig["debug.renderdoc.enabled", isDebug]
        if (enabled && !disableRenderDoc) {
            forceLoadRenderDoc(null)
        }
    }

    @JvmStatic
    fun forceLoadRenderDoc(renderDocPath: String? = null) {
        if (OS.isWeb) return // not supported
        val path = renderDocPath ?: DefaultConfig["debug.renderdoc.path", "C:/Program Files/RenderDoc/renderdoc.dll"]
        try {
            // if renderdoc is installed on linux, or given in the path, we could use it as well with loadLibrary()
            // at least this is the default location for RenderDoc
            if (getReference(path).exists) {
                LOGGER.info("Loading RenderDoc")
                System.load(path)
                usesRenderDoc = true
            } else LOGGER.warn("Did not find RenderDoc, searched '$path'")
        } catch (e: Exception) {
            LOGGER.warn("Could not initialize RenderDoc")
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun run() {
        try {

            loadRenderDoc()

            val clock = initLWJGL()

            val window0 = createWindow(OSWindow(projectName), clock)

            runLoops(window0)

            // wait for the last frame to be finished,
            // before we actually destroy the window and its framebuffer
            synchronized(glfwLock) {
                synchronized(openglLock) {
                    destroyed = true
                    when (windows.size) {
                        0 -> {}
                        1 -> LOGGER.info("Closing one remaining window")
                        else -> LOGGER.info("Closing ${windows.size} remaining windows")
                    }
                    for (index in 0 until windows.size) {
                        close(windows.getOrNull(index) ?: break)
                    }
                    windows.clear()
                }
            }
            if (debugMsgCallback != null) debugMsgCallback!!.free()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            GLFW.glfwTerminate()
            errorCallback!!.free()
        }
    }

    @JvmStatic
    fun initLWJGL(): Clock {
        if (!OS.isWeb) LOGGER.info("Using LWJGL Version " + Version.getVersion())
        val tick = Clock()
        GLFW.glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err).also { errorCallback = it })
        tick.stop("Error callback")
        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }
        tick.stop("GLFW initialization")
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
        if (isDebug) {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE)
        }
        // removes scaling options -> how could we replace them?
        // glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        // tick.stop("window hints");// 0s
        return tick
    }

    @JvmStatic
    fun addCallbacks(window: OSWindow) {
        window.addCallbacks()
        Input.initForGLFW(window)
    }

    @JvmStatic
    fun createWindow(title: String, panel: Panel): OSWindow {
        val window = OSWindow(title)
        createWindow(window, null)
        window.windowStack.push(panel)
        return window
    }

    @JvmStatic
    fun createWindow(instance: OSWindow, tick: Clock?): OSWindow {
        synchronized(glfwLock) {
            val width = instance.width
            val height = instance.height
            val sharedWindow = windows.firstOrNull { it.pointer != 0L }?.pointer ?: 0L
            val window = GLFW.glfwCreateWindow(width, height, projectName, 0L, sharedWindow)
            instance.pointer = window
            if (window == 0L) throw RuntimeException("Failed to create the GLFW window")
            windows.add(instance)
            tick?.stop("Create window")
            addCallbacks(instance)
            tick?.stop("Adding callbacks")
            val videoMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor())
            if (videoMode != null) GLFW.glfwSetWindowPos(
                window,
                (videoMode.width() - width) / 2,
                (videoMode.height() - height) / 2
            )

            val w = intArrayOf(0)
            val h = intArrayOf(1)
            GLFW.glfwGetFramebufferSize(window, w, h)

            instance.width = w[0]
            instance.height = h[0]

            tick?.stop("Window position")
            GLFW.glfwSetWindowTitle(window, instance.title)

            // tick.stop("window title"); // 0s
            GLFW.glfwShowWindow(window)
            tick?.stop("Show window")
            setIcon(window)
            tick?.stop("Setting icon")
            val x = DoubleArray(1)
            val y = DoubleArray(1)
            GLFW.glfwGetCursorPos(window, x, y)
            instance.mouseX = x[0].toFloat()
            instance.mouseY = y[0].toFloat()
        }
        return instance
    }

    @JvmStatic
    private var neverStarveWindows = DefaultConfig["ux.neverStarveWindows", false]

    @JvmStatic
    fun prepareForRendering(tick: Clock?) {
        capabilities = GL.createCapabilities()
        tick?.stop("OpenGL initialization")
        debugMsgCallback = GLUtil.setupDebugMessageCallback(LWJGLDebugCallback)
        tick?.stop("Debugging Setup")
        // render first frames = render logo
        // the engine will still be loading,
        // so it has to be a still image
        // alternatively we could play a small animation
        GFX.maxSamples = max(1, GL30C.glGetInteger(GL30C.GL_MAX_SAMPLES))
        GFX.check()
        // nice features :3
        // cause issues in FrameGen -> not enabled everywhere
        // glEnable(GL_LINE_SMOOTH)
        // glEnable(GL_POINT_SMOOTH)
        checkIsGFXThread()
    }

    @JvmField
    var numLogoFrames = 2

    @JvmStatic
    fun runRenderLoop0(window0: OSWindow) {
        LOGGER.info("Running RenderLoop")
        val tick = Clock()
        window0.makeCurrent()
        window0.forceUpdateVsync()
        tick.stop("Make context current + vsync")
        prepareForRendering(tick)
        GFX.setFrameNullSize(window0)
        val logoFrames = numLogoFrames
        for (i in 0 until logoFrames) {
            drawLogo(window0.width, window0.height, i == logoFrames - 1)
            GFX.check()
            GLFW.glfwSwapBuffers(window0.pointer)
            val err = glGetError()
            if (err != 0)
                LOGGER.warn("Got awkward OpenGL error from calling glfwSwapBuffers: ${getErrorTypeName(err)}")
            // GFX.check()
        }
        tick.stop("Render frame zero")
        if (isDebug) {
            glDebugMessageCallback({ source: Int, type: Int, id: Int, severity: Int, _: Int, message: Long, _: Long ->
                val message2 = if (message != 0L) MemoryUtil.memUTF8(message) else null
                if (message2 != null && "will use VIDEO memory as the source for buffer object operations" !in message2)
                    LOGGER.warn(
                        message2 +
                                ", source: " + getDebugSourceName(source) +
                                ", type: " + getDebugTypeName(type) + // mmh, not correct, at least for my simple sample I got a non-mapped code
                                ", id: " + getErrorTypeName(id) +
                                ", severity: " + getDebugSeverityName(severity)
                    )
            }, 0)
            glEnable(KHRDebug.GL_DEBUG_OUTPUT)
        }
        init2(tick)
    }

    @JvmStatic
    fun init2(tick: Clock?) {
        GFX.setup(tick)
        GFX.check()
        tick?.stop("Render step zero")
        StudioBase.instance?.gameInit()
        tick?.stop("Game Init")
    }

    @JvmStatic
    fun runRenderLoop() {
        lastTime = System.nanoTime()
        while (!destroyed && !shutdown) {
            Engine.updateTime()
            renderFrame()
        }
        StudioBase.instance?.onShutdown()
    }

    @JvmStatic
    fun runLoops(window0: OSWindow) {

        Thread.currentThread().name = "GLFW"

        // Start new thread to have the OpenGL context current in and that does the rendering.
        thread(name = "OpenGL") {
            runRenderLoop0(window0)
            runRenderLoop()
        }

        while (!windows.all2 { it.shouldClose } && !shutdown) {
            updateWindows()
        }

    }

    @JvmField
    var lastTime = System.nanoTime()

    @JvmStatic
    fun renderFrame() {

        val time = Engine.nanoTime

        val firstWindow = windows.firstOrNull()
        if (firstWindow != null) Input.pollControllers(firstWindow)

        for (index in 0 until windows.size) {
            val window = windows.getOrNull(index) ?: break
            if (window.isInFocus ||
                window.hasActiveMouseTargets() ||
                neverStarveWindows ||
                abs(window.lastUpdate - time) * GFX.idleFPS > 1e9
            ) {
                window.lastUpdate = time
                // this is hopefully ok (calling it async to other glfw stuff)
                if (window.makeCurrent()) {
                    synchronized(openglLock) {
                        GFX.activeWindow = window
                        GFX.renderStep(window)
                    }
                    synchronized(glfwLock) {
                        if (!destroyed) {
                            GLFW.glfwWaitEventsTimeout(0.0)
                            GLFW.glfwSwapBuffers(window.pointer)
                            // works in reducing input latency by 1 frame 😊
                            // https://www.reddit.com/r/GraphicsProgramming/comments/tkpdhd/minimising_input_latency_in_opengl/
                            if (OS.isWindows) glFinish()
                            window.updateVsync()
                        }
                    }
                }
            }
        }

        for (index in 0 until windows.size) {
            val window = windows.getOrNull(index) ?: break
            if (window.shouldClose) close(window)
        }

        if (windows.isNotEmpty() &&
            windows.none2 { (it.isInFocus && !it.isMinimized) || it.hasActiveMouseTargets() } &&
            mayIdle && !OS.isWeb // Browser must not wait, because it is slow anyway ^^, and we probably can't detect in-focus
        ) {
            // enforce 30 fps, because we don't need more
            // and don't want to waste energy
            val currentTime = System.nanoTime()
            val waitingTime = 1000 / max(1, GFX.idleFPS) - (currentTime - lastTime) / 1000000
            lastTime = currentTime
            if (waitingTime > 0) try {
                // wait does not work, causes IllegalMonitorState exception
                Thread.sleep(waitingTime)
            } catch (ignored: InterruptedException) {
            }
        }
    }

    @JvmField
    var mayIdle = true

    @JvmField
    var lastTrapWindow: OSWindow? = null

    @JvmStatic
    private fun handleClose(window: OSWindow) {
        val ws = window.windowStack
        if (ws.isEmpty() ||
            DefaultConfig["window.close.directly", false] ||
            ws.peek().isClosingQuestion
        ) {
            window.shouldClose = true
            GLFW.glfwSetWindowShouldClose(window.pointer, true)
        } else {
            GLFW.glfwSetWindowShouldClose(window.pointer, false)
            addGPUTask("close-request", 1) {
                ask(
                    ws, NameDesc("Close %1?", "", "ui.closeProgram")
                        .with("%1", projectName)
                ) {
                    window.shouldClose = true
                    GLFW.glfwSetWindowShouldClose(window.pointer, true)
                }?.isClosingQuestion = true
                window.framesSinceLastInteraction = 0
                ws.peek().setAcceptsClickAway(false)
            }
        }
    }

    @JvmStatic
    fun updateWindows() {

        for (index in 0 until windows.size) {
            val window = windows[index]
            if (!window.shouldClose) {
                if (GLFW.glfwWindowShouldClose(window.pointer)) {
                    handleClose(window)
                } else {
                    // update small stuff, that may need to be updated;
                    // currently only the title
                    window.updateTitle()
                }
            }
        }

        val trapWindow = trapMouseWindow
        if (isMouseTrapped && trapWindow != null && !trapWindow.shouldClose) {
            if (lastTrapWindow == null) {
                GLFW.glfwSetInputMode(trapWindow.pointer, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
                // GLFW.glfwSetInputMode(trapWindow.pointer, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
                lastTrapWindow = trapWindow
            }
            /*val x = trapWindow.mouseX
            val y = trapWindow.mouseY
            val centerX = trapWindow.width * 0.5
            val centerY = trapWindow.height * 0.5
            val dx = x - centerX
            val dy = y - centerY
            if (dx * dx + dy * dy > trapMouseRadius * trapMouseRadius) {
                GLFW.glfwSetCursorPos(trapWindow.pointer, centerX, centerY)
            }*/
        } else if (lastTrapWindow?.shouldClose == false) {
            GLFW.glfwSetInputMode(lastTrapWindow!!.pointer, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            lastTrapWindow = null
        } else if (Input.mouseMovementSinceMouseDown > 5f && Input.mouseKeysDown.isNotEmpty() && DefaultConfig["ui.enableMouseJumping", true]) {
            // when dragging a value (dragging + selected.isInput), and cursor is on the border, respawn it in the middle of the screen
            // for that, the cursor should be within 2 frames of reaching the border...
            // for that, we need the last mouse movement :)
            val window = focusedWindow
            if (window != null) {
                val margin = 10f
                if (window.mouseX !in margin..window.width - margin || window.mouseY !in margin..window.height - margin) {
                    val inFocus = window.windowStack.inFocus
                    if (inFocus.any2 { p -> p.anyInHierarchy { h -> h is InputPanel<*> } }) {
                        val centerX = window.width * 0.5
                        val centerY = window.height * 0.5
                        synchronized(window) {
                            GLFW.glfwSetCursorPos(window.pointer, centerX, centerY)
                            window.mouseX = centerX.toFloat()
                            window.mouseY = centerY.toFloat()
                            window.lastMouseCorrection = Engine.nanoTime + 5_000_000 // 5ms safety delay
                        }
                    }
                }
            }
        }

        for (index in windows.indices) {
            val window = windows[index]
            if (!window.shouldClose && window.updateMouseTarget()) break
        }

        // glfwWaitEventsTimeout() without args only terminates, if keyboard or mouse state is changed
        GLFW.glfwWaitEventsTimeout(0.0)

    }

    @JvmStatic
    fun setIcon(window: Long) {
        val src = getReference(BundledRef.prefix + "icon.png")
        val srcImage = ImageCPUCache[src, false]
        if (srcImage != null) {
            setIcon(window, srcImage)
        }
    }

    @JvmStatic
    fun setIcon(window: Long, srcImage: Image) {

        val image = GLFWImage.malloc()
        val buffer = GLFWImage.malloc(1)

        val w = srcImage.width
        val h = srcImage.height
        val pixels = BufferUtils.createByteBuffer(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // argb -> rgba
                val color = srcImage.getRGB(x, y)
                pixels.put(color.shr(16).toByte())
                pixels.put(color.shr(8).toByte())
                pixels.put(color.toByte())
                pixels.put(color.shr(24).toByte())
            }
        }
        pixels.flip()
        image.set(w, h, pixels)
        buffer.put(0, image)
        GLFW.glfwSetWindowIcon(window, buffer)

    }

    @JvmStatic
    fun close(window: OSWindow) {
        synchronized(glfwLock) {
            if (window.pointer != 0L) {
                GLFW.glfwDestroyWindow(window.pointer)
                window.keyCallback?.free()
                window.keyCallback = null
                window.fsCallback?.free()
                window.fsCallback = null
                window.pointer = 0L
            }
        }
    }
}