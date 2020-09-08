package me.anno.objects.modes

import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01Cube
import me.anno.gpu.buffer.StaticFloatBuffer
import me.anno.objects.Video.Companion.cubemapBuffer

// todo make this applicable to all video and image types? (except svg)
enum class UVProjection(val id: Int, val doScale: Boolean, val displayName: String, val description: String){
    Planar(0, true, "Planar", "Simple plane, e.g. for 2D video"){
        override fun getBuffer(): StaticFloatBuffer = flat01Cube
    },
    Equirectangular(1, false, "Full Cubemap", "Earth-like projection, equirectangular"){
        override fun getBuffer(): StaticFloatBuffer = cubemapBuffer
    },
    TiledCubemap(2, false, "Tiled Cubemap", "Cubemap with 6 square sides"){
        override fun getBuffer(): StaticFloatBuffer = cubemapBuffer
    };
    abstract fun getBuffer(): StaticFloatBuffer
}