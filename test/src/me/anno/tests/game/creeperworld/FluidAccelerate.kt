package me.anno.tests.game.creeperworld

class FluidAccelerate(
    val pressureDiff: Float,
    val gravityY: Float
) : FluidWithNeighborShader {

    override fun process(fluid: FluidFramebuffer, world: World) {
        super.process(fluid, world)
        fluid.impulseX.swap()
        fluid.impulseY.swap()
    }

    override fun processEdgePixel(x: Int, y: Int, i: Int, fluid: FluidFramebuffer, world: World) {

        val srcH = fluid.level.read
        val srcVX = fluid.impulseX.read
        val srcVY = fluid.impulseY.read
        val dstVX = fluid.impulseX.write
        val dstVY = fluid.impulseY.write

        val srcH0 = srcH[i]
        dstVX[i] = srcVX[i] +
                pressureDiff * (srcH.getOrElse(i + 1) { 0f } - srcH.getOrElse(i - 1) { 0f })
        dstVY[i] = srcVY[i] +
                pressureDiff * (srcH.getOrElse(i + h) { 0f } + srcH.getOrElse(i - h) { 0f }) +
                gravityY * srcH0 // gravity
    }

    override fun processInnerPixels(i0: Int, i1: Int, fluid: FluidFramebuffer, world: World) {
        val srcH = fluid.level.read
        val srcVX = fluid.impulseX.read
        val srcVY = fluid.impulseY.read
        val dstVX = fluid.impulseX.write
        val dstVY = fluid.impulseY.write

        // todo gravity depends on the density of fluids at that location

        for (i in i0 until i1) {
            val srcH0 = srcH[i]
            dstVX[i] = srcVX[i] + pressureDiff * (srcH[i + 1] - srcH[i - 1])
            dstVY[i] = srcVY[i] + pressureDiff * (srcH[i + h] - srcH[i - h]) + gravityY * srcH0
        }
    }
}