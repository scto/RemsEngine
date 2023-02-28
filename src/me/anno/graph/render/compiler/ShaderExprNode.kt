package me.anno.graph.render.compiler

import me.anno.ecs.components.mesh.TypeValue
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.Shader
import me.anno.graph.types.flow.actions.ActionNode

// this is a simple method for rendering, which does not allow loops
// todo alternatives, which are CubeBufferNode, Buffer3DNode, BufferArrayNode, ...

class ShaderExprNode : ActionNode(
    "Shader Expression",
    // num channels, num samples, todo target type, depth mode
    // todo blend mode/conditional clear
    // todo multi-sampled/single-sampled output
    listOf(
        "Vector4f", "Data",
        "Int", "Width",
        "Int", "Height",
        "Int", "Channels",
        "Int", "Samples",
    ),
    listOf(
        "Texture", "Result", // resampled
    )
), ExpressionRenderer {

    init {
        init()
    }

    override var shader: Shader? = null
    override var buffer: Framebuffer? = null
    override var typeValues: HashMap<String, TypeValue>? = null
    override fun executeAction() {
        setOutput(render(true), 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        shader?.destroy()
        buffer?.destroy()
        shader = null
        buffer = null
    }

}