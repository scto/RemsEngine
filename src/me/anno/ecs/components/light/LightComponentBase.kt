package me.anno.ecs.components.light

import me.anno.Engine
import me.anno.ecs.Component
import me.anno.ecs.interfaces.Renderable

// a light component, of which there can be multiple per object
abstract class LightComponentBase : Component(), Renderable {

    override fun onUpdate(): Int {
        super.onUpdate()
        if (lastDrawn >= Engine.lastGameTime)
            onVisibleUpdate()
        return 1
    }

    open fun onVisibleUpdate(): Boolean = false
}