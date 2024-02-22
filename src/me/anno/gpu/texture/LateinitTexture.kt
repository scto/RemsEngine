package me.anno.gpu.texture

import me.anno.cache.ICacheData

class LateinitTexture : ICacheData {
    var texture: ITexture2D? = null
        set(value) {
            if (isDestroyed) value?.destroy()
            field = value
        }
    var isDestroyed = false
    override fun destroy() {
        isDestroyed = true
        texture?.destroy()
    }
}