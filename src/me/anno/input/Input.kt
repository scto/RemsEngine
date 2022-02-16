package me.anno.input

import me.anno.Build
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.gameTime
import me.anno.gpu.GFX.window
import me.anno.gpu.OpenGL
import me.anno.gpu.debug.DebugGPUStorage
import me.anno.input.MouseButton.Companion.toMouseButton
import me.anno.input.Touch.Companion.onTouchDown
import me.anno.input.Touch.Companion.onTouchMove
import me.anno.input.Touch.Companion.onTouchUp
import me.anno.io.ISaveable
import me.anno.io.SaveableArray
import me.anno.io.files.FileFileRef
import me.anno.io.files.FileFileRef.Companion.copyHierarchy
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.InvalidRef
import me.anno.io.text.TextWriter
import me.anno.maths.Maths.length
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.studio.StudioBase.Companion.defaultWindowStack
import me.anno.studio.StudioBase.Companion.instance
import me.anno.ui.Panel
import me.anno.ui.Window
import me.anno.ui.editor.treeView.TreeViewPanel
import me.anno.utils.files.FileExplorerSelectWrapper
import me.anno.utils.files.Files.findNextFile
import me.anno.utils.hpc.Threads.threadWithName
import me.anno.utils.types.Strings.isArray
import me.anno.utils.types.Strings.isName
import me.anno.utils.types.Strings.isNumber
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWDropCallback
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor.*
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.RenderedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Input {

    private val LOGGER = LogManager.getLogger(Input::class)

    var keyUpCtr = 0

    // where the mouse is
    // the default is before any mouse move was registered:
    // then the cursor shall start in the center of the window
    var mouseX = GFX.width * 0.5f
    var mouseY = GFX.height * 0.5f

    var lastFile: FileReference = InvalidRef

    var mouseDownX = 0f
    var mouseDownY = 0f
    var mouseKeysDown = HashSet<Int>()

    val keysDown = HashMap<Int, Long>()
    val keysWentDown = HashSet<Int>()
    val keysWentUp = HashSet<Int>()

    var lastShiftDown = 0L

    var lastClickX = 0f
    var lastClickY = 0f
    var lastClickTime = 0L
    var keyModState = 0
        set(value) {// check for shift...
            if (isShiftTrulyDown) lastShiftDown = gameTime
            field = value
        }

    var mouseMovementSinceMouseDown = 0f
    val maxClickDistance = 5f
    var hadMouseMovement = false

    var isLeftDown = false
    var isMiddleDown = false
    var isRightDown = false

    val isControlDown get() = (keyModState and GLFW.GLFW_MOD_CONTROL) != 0

    // 30ms shift lag for numpad, because shift disables it on Windows
    val isShiftTrulyDown get() = (keyModState and GLFW.GLFW_MOD_SHIFT) != 0
    val isShiftDown get() = isShiftTrulyDown || abs(lastShiftDown - gameTime) < 30_000_000

    val isCapsLockDown get() = (keyModState and GLFW.GLFW_MOD_CAPS_LOCK) != 0
    val isAltDown get() = (keyModState and GLFW.GLFW_MOD_ALT) != 0
    val isSuperDown get() = (keyModState and GLFW.GLFW_MOD_SUPER) != 0

    var framesSinceLastInteraction = 0
    val layoutFrameCount = 10

    fun needsLayoutUpdate() = framesSinceLastInteraction < layoutFrameCount

    fun invalidateLayout() {
        framesSinceLastInteraction = 0
    }

    fun initForGLFW() {

        GLFW.glfwSetDropCallback(window) { _: Long, count: Int, names: Long ->
            if (count > 0) {
                // it's important to be executed here, because the strings may be GCed otherwise
                val files = Array(count) { nameIndex ->
                    try {
                        File(GLFWDropCallback.getName(names, nameIndex))
                    } catch (e: Exception) {
                        null
                    }
                }
                addEvent {
                    framesSinceLastInteraction = 0
                    val dws = defaultWindowStack
                    if (dws != null) {
                        dws.requestFocus(dws.getPanelAt(mouseX, mouseY), true)
                        dws.inFocus0?.apply {
                            onPasteFiles(mouseX, mouseY, files.filterNotNull().map { getReference(it) })
                        }
                    }
                }
            }
        }

        /*GLFW.glfwSetCharCallback(window) { _, _ ->
            addEvent {
                // LOGGER.info("char event $codepoint")
            }
        } */

        // key typed callback
        GLFW.glfwSetCharModsCallback(window) { _, codepoint, mods ->
            addEvent { onCharTyped(codepoint, mods) }
        }

        GLFW.glfwSetCursorPosCallback(window) { _, xPosition, yPosition ->
            addEvent { onMouseMove(xPosition.toFloat(), yPosition.toFloat()) }
        }

        GLFW.glfwSetMouseButtonCallback(window) { _, button, action, mods ->
            addEvent {
                framesSinceLastInteraction = 0
                when (action) {
                    GLFW.GLFW_PRESS -> onMousePress(button)
                    GLFW.GLFW_RELEASE -> onMouseRelease(button)
                }
                keyModState = mods
            }
        }

        GLFW.glfwSetScrollCallback(window) { _, xOffset, yOffset ->
            addEvent { onMouseWheel(xOffset.toFloat(), yOffset.toFloat(), true) }
        }

        GLFW.glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            if (window != GFX.window) {
                // touch events are hacked into GLFW for Windows 7+
                framesSinceLastInteraction = 0
                // val pressure = max(1, mods)
                val x = scancode * 0.01f
                val y = action * 0.01f
                addEvent {
                    when (mods) {
                        -1 -> onTouchDown(key, x, y)
                        -2 -> onTouchUp(key, x, y)
                        else -> onTouchMove(key, x, y)
                    }
                }
            } else addEvent {
                when (action) {
                    GLFW.GLFW_PRESS -> onKeyPressed(key)
                    GLFW.GLFW_RELEASE -> onKeyReleased(key)
                    GLFW.GLFW_REPEAT -> onKeyTyped(action, key)
                }
                // LOGGER.info("event $key $scancode $action $mods")
                keyModState = mods
            }
        }

    }

    fun resetFrameSpecificKeyStates() {
        keysWentDown.clear()
        keysWentUp.clear()
    }

    val inFocus0 get() = defaultWindowStack?.inFocus0

    fun onCharTyped(codepoint: Int, mods: Int) {
        framesSinceLastInteraction = 0
        inFocus0?.onCharTyped(mouseX, mouseY, codepoint)
        keyModState = mods
        KeyMap.onCharTyped(codepoint)
    }

    fun onKeyPressed(key: Int) {
        framesSinceLastInteraction = 0
        keysDown[key] = gameTime
        keysWentDown += key
        inFocus0?.onKeyDown(mouseX, mouseY, key) // 264
        ActionManager.onKeyDown(key)
        onKeyTyped(GLFW.GLFW_PRESS, key)
    }

    fun onKeyReleased(key: Int) {
        framesSinceLastInteraction = 0
        keyUpCtr++
        keysWentUp += key
        defaultWindowStack?.inFocus0?.onKeyUp(mouseX, mouseY, key)
        ActionManager.onKeyUp(key)
        keysDown.remove(key)
    }

    fun onKeyTyped(action: Int, key: Int) {

        framesSinceLastInteraction = 0

        ActionManager.onKeyTyped(key)

        // just related to the top window-stack
        val dws = defaultWindowStack
        val inFocus = dws?.inFocus ?: mutableSetOf()
        val inFocus0 = dws?.inFocus0

        when (key) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (inFocus0 != null) {
                    if (isShiftDown || isControlDown) {
                        inFocus0.onCharTyped(mouseX, mouseY, '\n'.code)
                    } else {
                        inFocus0.onEnterKey(mouseX, mouseY)
                    }
                }
            }
            GLFW.GLFW_KEY_DELETE -> {
                // tree view selections need to be removed, because they would be illogical to keep
                // (because the underlying Transform changes)
                val inFocusTreeViews = inFocus.filterIsInstance<TreeViewPanel<*>>()
                for (it in inFocus) it.onDeleteKey(mouseX, mouseY)
                inFocus.removeAll(inFocusTreeViews)
            }
            GLFW.GLFW_KEY_BACKSPACE -> {
                inFocus0?.onBackSpaceKey(mouseX, mouseY)
            }
            GLFW.GLFW_KEY_TAB -> {
                if (inFocus0 != null) {
                    if (isShiftDown || isControlDown
                        || !inFocus0.isKeyInput()
                        || !inFocus0.acceptsChar('\t'.code)
                    ) {
                        // switch between input elements
                        val root = inFocus0.rootPanel
                        // todo make groups, which are not empty, inputs?
                        val list = root.listOfAll.filter { it.canBeSeen && it.isKeyInput() }.toList()
                        val index = list.indexOf(inFocus0)
                        if (index > -1) {
                            var next = list
                                .subList(index + 1, list.size)
                                .firstOrNull()
                            if (next == null) {
                                // LOGGER.info("no more text input found, starting from top")
                                // restart from top
                                next = list.firstOrNull()
                            }// else LOGGER.info(next)
                            if (next != null) {
                                inFocus.clear()
                                inFocus += next
                            }
                        }// else error, child missing
                    } else inFocus0.onCharTyped(mouseX, mouseY, '\t'.code)
                }
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                val ws = defaultWindowStack!!
                if (ws.size > 1) {
                    val window2 = ws.peek()
                    if (window2.canBeClosedByUser) {
                        ws.pop().destroy()
                    } else inFocus0?.onEscapeKey(mouseX, mouseY)
                } else inFocus0?.onEscapeKey(mouseX, mouseY)
            }
            else -> {
                if (isControlDown) {
                    if (action == GLFW.GLFW_PRESS) {
                        when (key) {
                            GLFW.GLFW_KEY_S -> instance?.save()
                            GLFW.GLFW_KEY_V -> paste()
                            GLFW.GLFW_KEY_C -> copy()
                            GLFW.GLFW_KEY_D -> {// duplicate selection
                                copy()
                                paste()
                            }
                            GLFW.GLFW_KEY_X -> {// cut
                                copy()
                                empty()
                            }
                            GLFW.GLFW_KEY_I -> import()
                            GLFW.GLFW_KEY_H -> instance?.openHistory()
                            GLFW.GLFW_KEY_A -> inFocus0?.onSelectAll(mouseX, mouseY)
                            GLFW.GLFW_KEY_M -> if (Build.isDebug) DebugGPUStorage.openMenu()
                            GLFW.GLFW_KEY_L -> addEvent { OpenGL.newSession() }
                        }
                    }
                }
                // LOGGER.info("typed by $action")
                inFocus0?.onKeyTyped(mouseX, mouseY, key)
                // inFocus?.onCharTyped(mx,my,key)
            }
        }
    }

    fun onMouseMove(newX: Float, newY: Float) {

        if (keysDown.isNotEmpty()) framesSinceLastInteraction = 0

        val dx = newX - mouseX
        val dy = newY - mouseY

        val length = length(dx, dy)
        mouseMovementSinceMouseDown += length
        if (length > 0f) hadMouseMovement = true

        mouseX = newX
        mouseY = newY

        defaultWindowStack?.inFocus0?.onMouseMoved(mouseX, mouseY, dx, dy)
        ActionManager.onMouseMoved(dx, dy)

    }

    var userCanScrollX = false
    fun onMouseWheel(dx: Float, dy: Float, byMouse: Boolean) {
        if (length(dx, dy) > 0f) framesSinceLastInteraction = 0
        addEvent {
            val clicked = defaultWindowStack?.getPanelAt(mouseX, mouseY)
            if (!byMouse && abs(dx) > abs(dy)) userCanScrollX = true // e.g. by touchpad: use can scroll x
            if (clicked != null) {
                if (!userCanScrollX && byMouse && (isShiftDown || isControlDown)) {
                    clicked.onMouseWheel(mouseX, mouseY, -dy, dx, byMouse)
                } else {
                    clicked.onMouseWheel(mouseX, mouseY, dx, dy, byMouse)
                }
            }
        }
    }

    val controllers = Array(15) { Controller(it) }
    fun pollControllers() {
        // controllers need to be pulled constantly
        synchronized(GFX.glfwLock) {
            var isFirst = true
            for (index in controllers.indices) {
                if (controllers[index].pollEvents(isFirst)) {
                    isFirst = false
                }
            }
        }
    }

    fun onClickIntoWindow(button: Int, panelWindow: Pair<Panel, Window>?) {
        if (panelWindow != null) {
            val mouseButton = button.toMouseButton()
            val ws = defaultWindowStack ?: return
            while (ws.isNotEmpty()) {
                val peek = ws.peek()
                if (panelWindow.second == peek || !peek.acceptsClickAway(mouseButton)) break
                ws.pop().destroy()
                windowWasClosed = true
            }
        }
    }

    var mouseStart = 0L
    var windowWasClosed = false
    var maySelectByClick = false

    fun onMousePress(button: Int) {

        // find the clicked element
        mouseDownX = mouseX
        mouseDownY = mouseY
        mouseMovementSinceMouseDown = 0f
        keysWentDown += button

        when (button) {
            0 -> isLeftDown = true
            1 -> isRightDown = true
            2 -> isMiddleDown = true
        }

        windowWasClosed = false
        val dws = defaultWindowStack!!
        val panelWindow = dws.getPanelAndWindowAt(mouseX, mouseY)
        onClickIntoWindow(button, panelWindow)

        val singleSelect = isControlDown
        val multiSelect = isShiftDown
        val inFocus0 = dws.inFocus0

        val mouseTarget = dws.getPanelAt(mouseX, mouseY)
        maySelectByClick = if (singleSelect || multiSelect) {
            val selectionTarget = mouseTarget?.getMultiSelectablePanel()
            val inFocusTarget = inFocus0?.getMultiSelectablePanel()
            val joinedParent = inFocusTarget?.parent
            if (selectionTarget != null && joinedParent == selectionTarget.parent) {
                if (inFocus0 != inFocusTarget) dws.requestFocus(inFocusTarget, true)
                if (singleSelect) {
                    if (selectionTarget.isInFocus) dws.inFocus -= selectionTarget
                    else selectionTarget.requestFocus(false)
                    selectionTarget.invalidateDrawing()
                } else {
                    val index0 = inFocusTarget!!.indexInParent
                    val index1 = selectionTarget.indexInParent
                    // todo we should use the last selected as reference point...
                    val minIndex = min(index0, index1)
                    val maxIndex = max(index0, index1)
                    for (index in minIndex..maxIndex) {
                        val child = joinedParent!!.children[index]
                        if (child is Panel) {
                            child.requestFocus(false)
                            child.invalidateDrawing()
                        }
                    }
                }
                false
            } else {
                if (mouseTarget != null && mouseTarget.isInFocus) {
                    true
                } else {
                    dws.requestFocus(mouseTarget, true)
                    false
                }
            }
        } else {
            if (mouseTarget != null && mouseTarget.isInFocus) {
                true
            } else {
                dws.requestFocus(mouseTarget, true)
                false
            }
        }

        if (!windowWasClosed) {

            inFocus0?.onMouseDown(mouseX, mouseY, button.toMouseButton())
            ActionManager.onKeyDown(button)
            mouseStart = System.nanoTime()
            mouseKeysDown.add(button)
            keysDown[button] = gameTime

        }

    }

    fun onMouseRelease(button: Int) {

        keyUpCtr++
        keysWentUp += button

        when (button) {
            0 -> isLeftDown = false
            1 -> isRightDown = false
            2 -> isMiddleDown = false
        }

        inFocus0?.onMouseUp(mouseX, mouseY, button.toMouseButton())

        ActionManager.onKeyUp(button)
        ActionManager.onKeyTyped(button)

        val longClickMillis = DefaultConfig["longClick", 300]
        val currentNanos = System.nanoTime()
        val isClick = mouseMovementSinceMouseDown < maxClickDistance && !windowWasClosed

        if (isClick) {

            if (maySelectByClick) {
                val dws = defaultWindowStack
                if (dws != null) {
                    val panelWindow = dws.getPanelAndWindowAt(mouseX, mouseY)
                    dws.requestFocus(panelWindow?.first, true)
                }
            }

            val longClickNanos = 1_000_000 * longClickMillis
            val isDoubleClick = abs(lastClickTime - currentNanos) < longClickNanos &&
                    length(mouseX - lastClickX, mouseY - lastClickY) < maxClickDistance

            if (isDoubleClick) {

                ActionManager.onKeyDoubleClick(button)
                inFocus0?.onDoubleClick(mouseX, mouseY, button.toMouseButton())

            } else {

                val mouseDuration = currentNanos - mouseStart
                val isLongClick = mouseDuration / 1_000_000 >= longClickMillis
                inFocus0?.onMouseClicked(mouseX, mouseY, button.toMouseButton(), isLongClick)

            }

            lastClickX = mouseX
            lastClickY = mouseY
            lastClickTime = currentNanos

        }

        mouseKeysDown.remove(button)
        keysDown.remove(button)

    }

    fun empty() {
        // LOGGER.info("[Input] emptying, $inFocus0, ${inFocus0?.javaClass}")
        inFocus0?.onEmpty(mouseX, mouseY)
    }

    fun import() {
        threadWithName("Ctrl+I") {
            if (lastFile == InvalidRef) lastFile = instance!!.getDefaultFileLocation()
            FileExplorerSelectWrapper.selectFile((lastFile as? FileFileRef)?.file) { file ->
                if (file != null) {
                    val fileRef = getReference(file)
                    lastFile = fileRef
                    instance?.importFile(fileRef)
                }
            }
        }
    }

    fun copy() {
        val mx = mouseX
        val my = mouseY
        val dws = defaultWindowStack ?: return
        val inFocus = dws.inFocus
        val inFocus0 = dws.inFocus0 ?: return
        when (inFocus.size) {
            0 -> return // should not happen
            1 -> setClipboardContent(inFocus0.onCopyRequested(mx, my)?.toString())
            else -> {
                // combine them into an array
                when (val first = inFocus0.onCopyRequested(mx, my)) {
                    is ISaveable -> {
                        // create array
                        val array = SaveableArray()
                        array.add(first)
                        for (panel in inFocus) {
                            if (panel !== inFocus0) {
                                array.add(panel.onCopyRequested(mx, my) as? ISaveable ?: continue)
                            }
                        }
                        setClipboardContent(TextWriter.toText(listOf(array)))
                        // todo where necessary, support pasting an array of elements
                    }
                    is FileReference -> {
                        // when this is a list of files, invoke copyFiles instead
                        copyFiles(inFocus.mapNotNull { it.onCopyRequested(mx, my) as? FileReference })
                    }
                    else -> {
                        // create very simple, stupid array of values as strings
                        val data = inFocus
                            .mapNotNull { it.onCopyRequested(mx, my) }
                            .joinToString(",", "[", "]") {
                                val s = it.toString()
                                when {
                                    s.isEmpty() -> "\"\""
                                    isName(s) || isArray(s) || isNumber(s) -> s
                                    else -> "\"${
                                        s.replace("\\", "\\\\").replace("\"", "\\\"")
                                    }\""
                                }
                            }
                        setClipboardContent(data)
                    }
                }
            }
        }
    }

    fun copy(panel: Panel) {
        setClipboardContent(panel.onCopyRequested(mouseX, mouseY)?.toString())
    }

    fun setClipboardContent(copied: String?) {
        copied ?: return
        val selection = StringSelection(copied)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    fun paste(panel: Panel? = inFocus0) {
        if (panel == null) return
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        /*val flavors = clipboard.availableDataFlavors
        for(flavor in flavors){
            val charset = flavor.getParameter("charset")
            val repClass = flavor.representationClass

        }*/
        try {
            val data = clipboard.getData(stringFlavor)
            if (data is String) {
                // LOGGER.info(data)
                panel.onPaste(mouseX, mouseY, data, "")
                return
            }
        } catch (e: UnsupportedFlavorException) {
        }
        /*try {
            val data = clipboard.getData(getTextPlainUnicodeFlavor())
            LOGGER.info("plain text data: $data")
            if (data is String) inFocus0?.onPaste(mouseX, mouseY, data, "")
            // return
        } catch (e: UnsupportedFlavorException) {
            LOGGER.info("Plain text flavor is not supported")
        }
        try {
            val data = clipboard.getData(javaFileListFlavor)
            LOGGER.info("file data: $data")
            LOGGER.info((data as? List<*>)?.map { it?.javaClass })
            if (data is String) inFocus0?.onPaste(mouseX, mouseY, data, "")
            // return
        } catch (e: UnsupportedFlavorException) {
            LOGGER.info("File List flavor is not supported")
        }*/
        try {
            val data = clipboard.getData(javaFileListFlavor) as? List<*>
            val data2 = data?.filterIsInstance<File>()
            if (data2 != null && data2.isNotEmpty()) {
                // LOGGER.info(data2)
                panel.onPasteFiles(mouseX, mouseY, data2.map { getReference(it) })
                return
                // return
            }
        } catch (e: UnsupportedFlavorException) {
        }
        try {
            val data = clipboard.getData(imageFlavor) as RenderedImage
            val folder = instance!!.getPersistentStorage()
            val file0 = folder.getChild("PastedImage.png")
            val file1 = findNextFile(file0, 3, '-', 1)
            file1.outputStream().use { ImageIO.write(data, "png", it) }
            LOGGER.info("Pasted image of size ${data.width} x ${data.height}, placed into $file1")
            panel.onPasteFiles(mouseX, mouseY, listOf(file1))
            return
        } catch (e: UnsupportedFlavorException) {

        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        /*try {
            val data = clipboard.getData(DataFlavor.getTextPlainUnicodeFlavor())
            LOGGER.info("plain text data: $data")
        } catch (e: UnsupportedFlavorException) {
        }*/
        LOGGER.warn("Unsupported Data Flavor")
    }

    fun copyFiles(files: List<FileReference>) {
        // we need this folder, when we have temporary copies,
        // because just File.createTempFile() changes the name,
        // and we need the original file name
        val tmpFolder = lazy { Files.createTempDirectory("tmp").toFile() }
        val tmpFiles = files.map {
            if (it is FileFileRef) it.file
            else {
                // create a temporary copy, that the OS understands
                val tmp = File(tmpFolder.value, it.name)
                copyHierarchy(it, tmp)
                tmp
            }
        }
        copyFiles2(tmpFiles)
    }

    fun copyFiles2(files: List<File>) {
        Toolkit
            .getDefaultToolkit()
            .systemClipboard
            .setContents(FileTransferable(files), null)
    }

    fun isKeyDown(key: Int): Boolean {
        return key in keysDown
    }

    fun isKeyDown(key: Char): Boolean {
        return isKeyDown(key.uppercaseChar().code)
    }

    fun wasKeyPressed(key: Int): Boolean {
        return key in keysWentDown
    }

    fun wasKeyPressed(key: Char): Boolean {
        return wasKeyPressed(key.uppercaseChar().code)
    }

    fun wasKeyReleased(key: Int): Boolean {
        return key in keysWentUp
    }

    fun wasKeyReleased(key: Char): Boolean {
        return wasKeyReleased(key.uppercaseChar().code)
    }

}