package me.anno.ui.editor.treeView

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle
import me.anno.config.DefaultStyle.black
import me.anno.gpu.Cursor
import me.anno.gpu.GFX
import me.anno.gpu.GFX.select
import me.anno.input.Input
import me.anno.input.Input.mouseX
import me.anno.input.Input.mouseY
import me.anno.input.MouseButton
import me.anno.io.text.TextReader
import me.anno.io.utils.StringMap
import me.anno.objects.*
import me.anno.objects.Transform.Companion.toTransform
import me.anno.objects.effects.MaskLayer
import me.anno.studio.RemsStudio.onLargeChange
import me.anno.studio.RemsStudio.selectedTransform
import me.anno.studio.StudioBase.Companion.dragged
import me.anno.ui.base.TextPanel
import me.anno.ui.dragging.Draggable
import me.anno.ui.editor.files.addChildFromFile
import me.anno.ui.style.Style
import me.anno.utils.clamp
import me.anno.utils.mixARGB
import org.joml.Vector4f
import java.io.File
import java.lang.Exception
import java.lang.RuntimeException

class TreeViewPanel(val getElement: () -> Transform, style: Style): TextPanel("", style){

    // todo text shadow, if text color and background color are close

    private val accentColor = style.getColor("accentColor", black or 0xff0000)
    private val defaultBackground = backgroundColor

    init { enableHoverColor = true }

    override fun tickUpdate() {
        super.tickUpdate()
        val transform = getElement()
        val dragged = dragged
        textColor = black or (transform.getLocalColor().toRGB(180))
        val colorIndex = if(
            mouseX.toInt() in lx0 .. lx1 &&
            mouseY.toInt() in ly0 .. ly1 &&
            dragged is Draggable && dragged.getOriginal() is Transform){
            clamp(((mouseY - this.y) / this.h * 3).toInt(), 0, 2)
        } else null
        val tint = if(colorIndex == null) null else intArrayOf(0xffff77, 0xff77ff, 0x77ffff)[colorIndex] or black
        backgroundColor = if(tint == null) defaultBackground else mixARGB(defaultBackground, tint, 0.5f)
        val isInFocus = isInFocus || selectedTransform == transform
        if(isHovered) textColor = hoverColor
        if(isInFocus) textColor = accentColor
    }

    override val effectiveTextColor: Int get() = textColor

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {

        val transform = getElement()
        when(button){
            MouseButton.LEFT -> {
                if(Input.isShiftDown){
                    transform.isCollapsed = !transform.isCollapsed
                } else {
                    select(transform)
                }
            }
            MouseButton.RIGHT -> {

                fun add(action: (Transform) -> Transform): () -> Unit = { transform.apply { select(action(this)) } }
                val options = DefaultConfig["createNewInstancesList"] as? StringMap
                if(options != null){
                    val extras = ArrayList<Pair<String, () -> Unit>>()
                    if(transform.parent != null){
                        extras += "Add Mask" to {
                            val parent = transform.parent!!
                            val i = parent.children.indexOf(transform)
                            if(i < 0) throw RuntimeException()
                            val mask = MaskLayer.create(listOf(Rectangle.create()), listOf(transform))
                            mask.isFullscreen = true
                            parent.setChildAt(mask, i)
                        }
                    }
                    GFX.openMenu(
                        mouseX, mouseY, "Add Child",
                        options.entries.map { (key, value) ->
                            key to add {
                                val newT = if(value is Transform) value.clone() else value.toString().toTransform()
                                newT!!
                                it.addChild(newT)
                                newT
                            }
                        } + extras
                    )
                } else println("Reset the config, to enable this menu!")
            }
        }
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        return getElement().stringify()
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        if(!data.startsWith("[")) return super.onPaste(x, y, data, type)
        try {
            val child = TextReader.fromText(data).firstOrNull { it is Transform } as? Transform ?: return super.onPaste(x, y, data, type)
            val original = (dragged as? Draggable)?.getOriginal() as? Transform
            val relativeY = (y - this.y) / this.h
            val e = getElement()
            if(relativeY < 0.33f){
                // paste on top
                if(e.parent != null){
                    e.addBefore(child)
                } else {
                    e.addChild(child)
                }
            } else if(relativeY < 0.67f){
                // paste as child
                e.addChild(child)
            } else {
                // paste below
                if(e.parent != null){
                    e.addAfter(child)
                } else {
                    e.addChild(child)
                }
            }
            // we can't remove the element, if it's the parent
            if(original !in child.listOfAll){
                original?.removeFromParent()
            }
            select(child)
            onLargeChange()
        } catch (e: Exception){
            e.printStackTrace()
            super.onPaste(x, y, data, type)
        }
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<File>) {
        val transform = getElement()
        files.forEach {
            addChildFromFile(transform, it, {})
        }
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when(action){
            "DragStart" -> {
                val transform = getElement()
                if(dragged?.getOriginal() != transform){
                    dragged = Draggable(transform.stringify(), "Transform", transform, TextPanel(transform.name, style))
                }
            }
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onEmpty(x: Float, y: Float) {
        onDeleteKey(x, y)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        getElement().destroy()
    }

    override fun onBackSpaceKey(x: Float, y: Float) = onDeleteKey(x,y)
    override fun getCursor() = Cursor.drag

    override fun getTooltipText(x: Float, y: Float): String? {
        val transform = getElement()
        return if(transform is Camera) "Drag Onto Scene to Use" else null
    }

    fun Vector4f.toRGB(scale: Int = 255): Int {
        return clamp((x * scale).toInt(), 0, 255).shl(16) or
                clamp((y * scale).toInt(), 0, 255).shl(8) or
                clamp((z * scale).toInt(), 0, 255) or
                clamp((w * 255).toInt(), 0, 255).shl(24)
    }

    // multiple values can be selected
    override fun getMultiSelectablePanel() = this

}