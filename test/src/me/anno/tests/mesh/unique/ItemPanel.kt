package me.anno.tests.mesh.unique

import me.anno.config.DefaultConfig.style
import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.mesh.material.Material
import me.anno.gpu.GFX
import me.anno.input.Key
import me.anno.io.files.InvalidRef
import me.anno.maths.Maths.min
import me.anno.mesh.Shapes.flatCube
import me.anno.tests.utils.TestWorld
import me.anno.ui.base.buttons.TextButton.Companion.drawButtonBorder
import me.anno.ui.utils.ThumbnailPanel
import me.anno.utils.Color.black
import me.anno.utils.Color.withAlpha

class ItemPanel(val slot: ItemSlot) : ThumbnailPanel(InvalidRef, style) {
    companion object {
        var inHandBlock = TestWorld.dirt
        val previewBlockIds = TestWorld.palette.withIndex()
            .filter { it.value != 0 }.map { it.index.toByte() }
        val previewBlocks = previewBlockIds.associateWith { id ->
            val color = TestWorld.colors[id]!!.withAlpha(255)
            val mesh = flatCube.front.clone() as Mesh
            val materialI = Material.diffuse(color)
            Entity(MeshComponent(mesh, materialI))
        }
    }

    val leftColor = style.getColor("borderColorLeft", black or 0x999999)
    val rightColor = style.getColor("borderColorRight", black or 0x111111)
    val topColor = style.getColor("borderColorTop", black or 0x999999)
    val bottomColor = style.getColor("borderColorBottom", black or 0x111111)

    val borderSize = style.getPadding("borderSize", 2)
    var isPressed = false

    // todo highlight selected slot

    override fun onUpdate() {
        super.onUpdate()
        source = previewBlocks[slot.type]?.ref ?: InvalidRef
    }

    override fun calculateSize(w: Int, h: Int) {
        val size = min(64, GFX.someWindow.width / 11)
        minW = size
        minH = size
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        super.onKeyDown(x, y, key)
        isPressed = true
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        super.onKeyUp(x, y, key)
        isPressed = false
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        inHandBlock = slot.type
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        drawButtonBorder(
            leftColor, topColor, rightColor, bottomColor,
            true, borderSize, isPressed
        )
    }
}