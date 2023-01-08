package me.anno.graph.ui

import me.anno.fonts.FontManager.getBaselineY
import me.anno.gpu.drawing.DrawTexts.drawText
import me.anno.gpu.drawing.GFXx2D.drawCircle
import me.anno.gpu.drawing.GFXx2D.drawHalfArrow
import me.anno.graph.Node
import me.anno.graph.NodeConnector
import me.anno.graph.NodeInput
import me.anno.input.MouseButton
import me.anno.io.serialization.NotSerializedProperty
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.distance
import me.anno.maths.Maths.length
import me.anno.maths.Maths.mapClamped
import me.anno.maths.Maths.mixARGB
import me.anno.maths.Maths.mulAlpha
import me.anno.ui.Panel
import me.anno.ui.base.Font
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.text.TextStyleable
import me.anno.ui.style.Style
import me.anno.utils.Color.a
import me.anno.utils.Color.black
import me.anno.utils.Color.withAlpha
import me.anno.utils.structures.lists.Lists.none2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// todo show output value in tooltip on connector (for where it is easily computable without actions)
// todo can we add debug-clamps?: input and output overrides for debugging...
class NodePanel(
    val node: Node,
    val gp: GraphEditor,
    style: Style
) : PanelList(style) {

    var lineCount = 0
    val baseTextSize get() = gp.baseTextSize

    var bgAlpha = 0.7f

    var lineSpacing = 0.5

    init {
        // slightly transparent, so covered connections can be seen
        backgroundColor = mulAlpha(mixARGB(backgroundColor, black, 0.5f), bgAlpha)
        node.createUI(this, style)
        name = node.name
    }

    val customLayoutEndIndex = children.size

    var isDragged = false

    val inputFields = HashMap<NodeConnector, Panel>()

    var focusOutlineColor = -1
    var focusOutlineThickness = 2f

    override fun calculateSize(w: Int, h: Int) {

        val expectedChars = 20

        lineCount = 1 + // title
                // 1 + // desc
                max(node.inputs?.size ?: 0, node.outputs?.size ?: 0)

        minW = (expectedChars * baseTextSize).toInt()
        minH = ((lineCount * (1.0 + lineSpacing) + lineSpacing) * baseTextSize).toInt()

        // calculate how many lines, and space we need;
        // base calculation on w maybe

        backgroundRadius = w / 10f

        val inputs = node.inputs
        if (inputs != null) for (con in inputs) {
            // add all needed new input fields
            val oldField = inputFields[con]
            val newField = gp.getInputField(con, oldField)
            if (newField !== oldField) {
                if (oldField != null) remove(oldField)
                if (newField != null) {
                    add(newField)
                    inputFields[con] = newField
                } else inputFields.remove(con)
            }
            newField?.calculateSize(minW, minH)
        } else if (inputFields.isNotEmpty()) {
            // remove all input fields that are no longer needed
            for ((_, panel) in inputFields) {
                remove(panel)
            }
            inputFields.clear()
        }

        val minH0 = minH
        for (i in 0 until customLayoutEndIndex) {
            val child = children[i]
            if (child is TextStyleable) {
                child.textSize = gp.font.size
            }
            child.calculateSize(minW, minH0)
            minH += child.minH
        }

        this.w = minW
        this.h = minH

    }

    override fun setPosition(x: Int, y: Int) {
        super.setPosition(x, y)
        val font = gp.font
        val textSize = font.sampleHeight
        val titleOffset = textSize * 3 / 2
        var yi = y + titleOffset
        for (i in 0 until customLayoutEndIndex) {
            val child = children[i]
            child.minW = min(child.minW, w - textSize)
            child.setPosSize(x + (w - child.minW).shr(1), yi, child.minW, child.minH)
            yi += child.minH
        }
        yi -= titleOffset
        // calculate positions for connectors
        placeConnectors()
        // place all input fields to the correct position
        val baseTextSize = baseTextSize.toInt()
        if (inputFields.isNotEmpty()) for ((key, panel) in inputFields) {
            val pos = key.position
            val cx = gp.coordsToWindowX(pos.x).toInt()
            val cy = gp.coordsToWindowY(pos.y).toInt()
            // place to the right by radius
            panel.setPosSize(cx + baseTextSize.shr(1), cy - panel.minH.shr(1), panel.minW, panel.minH)
        }
    }

    @NotSerializedProperty
    private var lmx = 0

    @NotSerializedProperty
    private var lmy = 0

    override fun onUpdate() {
        super.onUpdate()
        if (canBeSeen) {
            val window = window
            if (window != null) {
                if (lmx != window.mouseXi || lmy != window.mouseYi) {
                    lmx = window.mouseXi
                    lmy = window.mouseYi
                    invalidateDrawing()
                }
            }
        }
    }

    private fun <V : NodeConnector> placeConnectors(connectors: Array<V>?, y: Int, x: Double) {
        connectors ?: return
        for ((index, con) in connectors.withIndex()) {
            con.position.set(x, gp.windowToCoordsY(y + (index + 1.5) * baseTextSize * (1.0 + lineSpacing)), 0.0)
        }
    }

    fun placeConnectors() {
        var yi = this.y
        for (i in 0 until customLayoutEndIndex) {
            yi += children[i].minH
        }
        placeConnectors(node.inputs, yi, gp.windowToCoordsX(this.x + baseTextSize))
        placeConnectors(node.outputs, yi, gp.windowToCoordsX(this.x + this.w - baseTextSize))
    }

    var titleWidth = 0

    var titleY0 = 0
    var titleY1 = 0

    var textColor = -1

    fun drawBackground(outline: Boolean, inner: Boolean, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (!outline && !inner) return
        // draw whether the node is in focus
        if (outline) {
            backgroundOutlineThickness = focusOutlineThickness
            backgroundOutlineColor = focusOutlineColor
            backgroundColor = backgroundColor.withAlpha(if (inner) bgAlpha else 0f)
        } else {
            backgroundOutlineThickness = 0f
            backgroundColor = backgroundColor.withAlpha(bgAlpha)
        }
        drawBackground(x0, y0, x1, y1)
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        val inFocus = isInFocus || gp.overlapsSelection(this)
        drawBackground(inFocus, true, x0, y0, x1, y1)

        val backgroundColor = mixARGB(gp.backgroundColor, backgroundColor, backgroundColor.a()) and 0xffffff
        val font = gp.font
        val textSize = font.sampleHeight

        // node title
        titleY0 = y + textSize / 2
        titleY1 = titleY0 + textSize

        titleWidth = drawText(
            x + w.shr(1), titleY0, font, node.name, textColor,
            backgroundColor, (w * 3).shr(2), -1, AxisAlignment.CENTER
        )

        val window = window
        val mouseX = window?.mouseX ?: 0f
        val mouseY = window?.mouseY ?: 0f

        // draw sockets, and their names
        val dxTxt = (baseTextSize * 0.7).toInt()
        val dyTxt = getBaselineY(font).toInt()
        val baseTextSize = baseTextSize.toFloat()

        // to do generally, weights could be useful on either end (maybe?)

        val inputs = node.inputs
        if (inputs != null) for (con in inputs) {
            var dx = dxTxt
            val panel = inputFields[con]
            if (panel != null) dx += panel.w //+ baseTextSize.toInt()
            drawConnector(con, baseTextSize, mouseX, mouseY, dx, dyTxt, font, textColor)
        }
        val outputs = node.outputs
        if (outputs != null) for (con in outputs) {
            drawConnector(con, baseTextSize, mouseX, mouseY, -dxTxt, dyTxt, font, textColor)
        }

        drawChildren(x0, y0, x1, y1)

    }

    fun drawConnector(
        con: NodeConnector,
        baseTextSize: Float,
        mouseX: Float,
        mouseY: Float,
        dx: Int,
        dy: Int,
        font: Font,
        textColor: Int
    ) {
        val pos = con.position
        val px = gp.coordsToWindowX(pos.x).toFloat()
        val py = gp.coordsToWindowY(pos.y).toFloat()
        val pxi = px.toInt()
        val pyi = py.toInt()
        val radius = baseTextSize * 0.4f
        val radius2 = mapClamped(
            length(px - mouseX, py - mouseY),
            0.9f * radius, 1.3f * radius,
            radius * 1.2f, radius
        )
        val innerRadius = if (con.others.isEmpty()) min(0.8f, (radius - 2f) / radius) else 0f
        val bg = mixARGB(gp.backgroundColor, backgroundColor, backgroundColor.a()) and 0xffffff
        if (con.type == "Flow") {
            // if the type is flow, draw an arrow instead of circle
            val rx = radius2 * 0.75f
            // apply inner radius
            drawHalfArrow(
                pxi - rx, pyi - radius2, 2f * rx, 2f * radius2,
                gp.getTypeColor(con), bg or black
            )
            if (innerRadius > 0f) {
                val dx2 = ((1f - innerRadius) * radius2).roundToInt()
                val rx2 = rx - dx2
                val ry2 = radius2 - dx2 + 1
                drawHalfArrow(
                    pxi - rx2 - 1, pyi - ry2, 2 * rx2, 2 * ry2,
                    bg or black, gp.getTypeColor(con)
                )
            }
        } else {
            drawCircle(
                pxi, pyi, radius2, radius2, innerRadius,
                bg, gp.getTypeColor(con), bg
            )
        }
        drawText(
            pxi + dx, pyi + dy, font, con.name, textColor,
            bg, -1, -1,
            if (dx < 0) AxisAlignment.MAX else AxisAlignment.MIN
        )
    }

    fun getConnectorAt(x: Float, y: Float): NodeConnector? {
        if (children.any { it.contains(x, y) }) return null
        val radius = baseTextSize * 0.5 + 5 // 5 for padding
        val radiusSq = radius * radius
        val cx = gp.windowToCoordsX(x.toDouble())
        val cy = gp.windowToCoordsY(y.toDouble())
        var bestDistance = radiusSq
        var bestCon: NodeConnector? = null
        val inputs = node.inputs
        if (inputs != null) for (con in inputs) {
            val distance = con.position.distanceSquared(cx, cy, 0.0)
            if (distance < bestDistance) {
                bestDistance = distance
                bestCon = con
            }
        }
        val outputs = node.outputs
        if (outputs != null) for (con in outputs) {
            val distance = con.position.distanceSquared(cx, cy, 0.0)
            if (distance < bestDistance) {
                bestDistance = distance
                bestCon = con
            }
        }
        return bestCon
    }

    /*override fun getCursor(): Long? {
        val window = window ?: return null
        return when {
            getConnectorAt(window.mouseX, window.mouseY) != null -> Cursor.hand
            // todo move-cursor
            isOpaqueAt(window.mouseXi, window.mouseYi) -> null // if (isInFocus) Cursor.vResize else null
            else -> null
        }
    }*/

    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
        val con = getConnectorAt(x, y)
        isDragged = false
        when {
            button.isLeft && con != null -> {
                gp.dragged = con
                gp.invalidateDrawing()
            }
            button.isLeft -> {
                isDragged = true
            }
            else -> super.onMouseDown(x, y, button)
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDragged) {
            val wx = gp.coordsToWindowX(node.position.x) + dx
            val wy = gp.coordsToWindowY(node.position.y) + dy
            gp.moveIfOnEdge(x, y)
            val dx2 = gp.windowToCoordsX(wx) - node.position.x
            val dy2 = gp.windowToCoordsY(wy) - node.position.y
            node.position.add(dx2, dy2, 0.0)
            gp.invalidateLayout()
        } else if (windowStack.inFocus.none2 { it.parent == uiParent }) {
            super.onMouseMoved(x, y, dx, dy)
        }
    }

    override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
        val con0 = gp.dragged
        val con1 = (gp.getPanelAt(x.toInt(), y.toInt()) as? NodePanel)?.getConnectorAt(x, y)
        val window = window
        // todo forbid connections, that would create infinite calculation loops
        when {
            con0 != null && con1 != null && con0 !== con1 &&
                    con0::class != con1::class -> {
                // also only connect, if not already connected
                if (con1 in con0) {
                    con0.disconnect(con1)
                } else {
                    connect(con0, con1)
                }
            }
            con0 != null && con1 != null && con0 !== con1 /* && con0.node == con1.node */ -> {
                // switch connections on these two nodes
                // todo only if types are compatible
                for (oi in con0.others) {
                    oi.others = oi.others.map { if (it == con0) con1 else it }
                }
                for (oi in con1.others) {
                    oi.others = oi.others.map { if (it == con1) con0 else it }
                }
                val o = con1.others
                con1.others = con0.others
                con0.others = o
            }
            con0 != null && (window == null ||
                    distance(window.mouseDownX, window.mouseDownY, window.mouseX, window.mouseY) < w / 10f) -> {
                // loosen this connection
                con0.disconnectAll()
            }
            con0 != null -> {
                // open new node menu, and then connect them automatically
                gp.openNewNodeMenu {
                    val base = if (con0 is NodeInput) it.outputs else it.inputs
                    val newCon = base?.firstOrNull()
                    if (newCon != null) {
                        connect(con0, newCon)
                    }
                }
            }
            else -> super.onMouseUp(x, y, button)
        }
        isDragged = false
        gp.dragged = null
        gp.invalidateDrawing()
    }

    fun connect(con0: NodeConnector, con1: NodeConnector) {
        // todo only connect, if types are compatible (flow only to flow)
        val input = if (con0 is NodeInput) con0 else con1
        val output = if (con0 === input) con1 else con0
        // only connect, if connection is supported, otherwise replace
        if (!input.isEmpty() && !input.node!!.supportsMultipleInputs(input)) {
            input.disconnectAll()
        }
        if (!output.isEnabled && !output.node!!.supportsMultipleOutputs(output)) {
            output.disconnectAll()
        }
        con0.connect(con1)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        val graph = gp.graph
        if (isInFocus) {
            val inFocus = windowStack.inFocus
            for (index in inFocus.indices) {
                val panel = inFocus[index]
                if (panel is NodePanel) panel.node.delete(graph)
            }
        } else node.delete(graph)
        gp.remove(this)
        gp.invalidateLayout()
    }

    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        // if user is clicking onto title, ask for new name
        val xi = x.toInt()
        val yi = y.toInt()
        if (yi in titleY0 until titleY1 &&
            abs(xi * 2 - (this.x * 2 + this.w)) < max(titleWidth, titleY1 - titleY0)
        ) {
            askName(windowStack, xi, yi, NameDesc("Set Node Name"), node.name, NameDesc("OK"),
                { textColor }, {
                    node.name = it
                    invalidateLayout()
                })
        } else {
            gp.target.set(node.position.x, node.position.y)
            invalidateLayout()
        }
    }

    override fun getTooltipText(x: Float, y: Float): String? {
        val con = getConnectorAt(x, y)
        return con?.type
    }

    override fun getMultiSelectablePanel() = this

    override val className get() = "NodePanel"

}