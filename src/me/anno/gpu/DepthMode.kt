package me.anno.gpu

import org.lwjgl.opengl.GL46C

@Suppress("unused")
enum class DepthMode(val id: Int) {

    // default depth mode is reversed in the engine, because it is superior
    // except on platforms, where glClipControl is not supported, and therefore the use of it is pointless
    ALWAYS(GL46C.GL_ALWAYS),
    DIFFERENT(GL46C.GL_NOTEQUAL),
    FARTHER(GL46C.GL_LESS),
    FAR(GL46C.GL_LEQUAL),
    EQUALS(GL46C.GL_EQUAL),
    CLOSE(GL46C.GL_GEQUAL),
    CLOSER(GL46C.GL_GREATER),

    FORWARD_ALWAYS(GL46C.GL_ALWAYS),
    FORWARD_DIFFERENT(GL46C.GL_NOTEQUAL),
    FORWARD_FARTHER(GL46C.GL_GREATER),
    FORWARD_FAR(GL46C.GL_GEQUAL),
    FORWARD_EQUALS(GL46C.GL_EQUAL),
    FORWARD_CLOSE(GL46C.GL_LEQUAL),
    FORWARD_CLOSER(GL46C.GL_LESS);

    val reversedDepth: Boolean get() = ordinal < 7
    val reversedMode: DepthMode get() = entries[ordinal + if (reversedDepth) 7 else -7]
    val alwaysMode: DepthMode get() = if (reversedDepth) ALWAYS else FORWARD_ALWAYS
    val equalsMode: DepthMode get() = if (reversedDepth) EQUALS else FORWARD_EQUALS
    val skyDepth: Double get() = if (reversedDepth) 0.0 else 1.0
}