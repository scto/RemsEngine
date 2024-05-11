@file:Suppress("unused")

package me.anno.graph.visual.scalar

import me.anno.graph.visual.EnumNode
import me.anno.graph.visual.FlowGraphNodeUtils.getIntInput
import me.anno.graph.visual.render.compiler.GLSLFuncNode

val dataI1 = MathNodeData(
    IntMathsUnary.entries,
    listOf("Int"), "Int",
    IntMathsUnary::id, IntMathsUnary::glsl
)

class MathI1Node : MathNode<IntMathsUnary>(dataI1), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0)))
    }
}

val dataI2 = MathNodeData(
    IntMathsBinary.entries,
    listOf("Int", "Int"), "Int",
    IntMathsBinary::id, IntMathsBinary::glsl
)

class MathI2Node : MathNode<IntMathsBinary>(dataI2), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0), getIntInput(1)))
    }
}

val dataI3 = MathNodeData(
    IntMathsTernary.entries,
    listOf("Int", "Int", "Int"), "Int",
    IntMathsTernary::id, IntMathsTernary::glsl
)

class MathI3Node : MathNode<IntMathsTernary>(dataI3), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0), getIntInput(1), getIntInput(2)))
    }
}