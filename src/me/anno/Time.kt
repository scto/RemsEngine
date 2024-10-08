package me.anno

import me.anno.maths.Maths.SECONDS_TO_NANOS
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.ui.debug.FrameTimings
import kotlin.math.abs
import kotlin.math.min

/**
 * state of time, for UI, and timelapse-able elements, and nanoTime (everything)
 * */
object Time {

    /**
     * game time since last frame in seconds;
     * 0.1 at max.
     * */
    var deltaTime = 0.0
        private set

    /**
     * time since last frame in seconds;
     * clamped, but not influenced by timeSpeed; should be used by UI
     * */
    var uiDeltaTime = 0.0
        private set

    /**
     * time since last frame in seconds;
     * not clamped
     * */
    var rawDeltaTime = 0.0
        private set

    /**
     * estimate for current fps
     * */
    var currentFPS = 60.0
        private set

    /**
     * estimate for current fps
     * */
    var currentMinFPS = 60.0
        private set

    private var lastFPSTime = 0L
    private var fpsCounter = 0
    private var maxCurrDt = 0L

    /**
     * nanoTime of when the engine was started in OS time
     * */
    @JvmField
    val startTimeN = System.nanoTime()

    /**
     * current frame time in nanoseconds;
     * use this in UI to check whether a function was already called that frame
     * */
    @JvmStatic
    var frameTimeNanos = 0L
        private set

    /**
     * dateTime of when the engine was started
     * */
    @JvmField
    val startDateTime = System.currentTimeMillis()

    /**
     * time at this moment since the engine started; in nanoseconds;
     * should be used for UI, and animations that shouldn't be scaled in time
     * */
    @JvmStatic
    val nanoTime get(): Long = System.nanoTime() - startTimeN

    /**
     * time of current frame; integrated by time speed; in nanoseconds
     * use gameTime for a float value
     *
     * This is used for Transform.lastDrawn, Transform.getDrawMatrix().
     * It should not be used for UI, because UI should never freeze, not even when the game is frozen XD.
     * Use nanoTime there instead.
     * */
    @JvmStatic
    var gameTimeN: Long = 0L
        private set

    /**
     * time of current frame; integrated by time speed; in nanoseconds
     * use gameTimeN for a long value
     * */
    @JvmStatic
    var gameTime: Double = 0.0
        private set

    @JvmStatic
    var lastGameTime: Double = 0.0
        private set

    @JvmStatic
    var lastGameTimeN: Long = 0L
        private set

    @JvmStatic
    var frameIndex: Int = 0
        private set

    /**
     * how fast gameTime is increased relative to actual nanoTime;
     * most gameplay should use gameTime, most UI should use nanoTime;
     *
     * when you accelerate parts of your game, or slow them down, everything should (^^) scale accordingly
     * */
    @JvmStatic
    var timeSpeed: Double = 1.0

    @JvmStatic
    fun updateTime() {
        updateTime(nanoTime, frameTimeNanos)
    }

    @JvmStatic
    fun updateTime(thisTime: Long, lastTime: Long) {
        val rawDeltaTime = (thisTime - lastTime) * 1e-9
        updateTime(rawDeltaTime, thisTime)
    }

    @JvmStatic
    fun updateTime(dt: Double, thisTime: Long) {

        rawDeltaTime = dt
        uiDeltaTime = min(dt, 0.1)
        deltaTime = clamp(dt * timeSpeed, -0.1, 0.1) // clamping before or after timeSpeed???
        FrameTimings.putTime(dt.toFloat())

        updateFPS(thisTime)

        lastGameTime = gameTime
        lastGameTimeN = gameTimeN
        gameTimeN += (deltaTime * 1e9).toLong()
        gameTime = gameTimeN * 1e-9

        frameIndex++
    }

    @JvmStatic
    private fun updateFPS(thisTime: Long) {
        val dt = abs(thisTime - frameTimeNanos)
        if (abs(thisTime - lastFPSTime) >= SECONDS_TO_NANOS) {
            val numFrames = fpsCounter + 1.0
            currentFPS = numFrames * SECONDS_TO_NANOS / abs(thisTime - lastFPSTime)
            currentMinFPS = 1e9 / maxCurrDt
            lastFPSTime = thisTime
            fpsCounter = 0
            maxCurrDt = dt
        } else {
            fpsCounter++
            maxCurrDt = max(dt, maxCurrDt)
            frameTimeNanos = thisTime
        }
    }
}