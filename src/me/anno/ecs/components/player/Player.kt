package me.anno.ecs.components.player

import me.anno.ecs.Component
import me.anno.ecs.components.camera.CameraState
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.utils.StringMap

open class Player : Component() {

    val cameraState = CameraState()

    // todo save all kind of information here
    val sessionInfo = StringMap()

    // todo needs to be saved every once in a while, preferably onSave ;)
    val persistentInfo = StringMap()

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // writer.writeMap("session", sessionInfo)
        writer.writeObject(null, "persistent", persistentInfo)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "persistent" -> {
                if (value !is StringMap) return
                persistentInfo.clear()
                persistentInfo.putAll(value)
            }
            else -> super.setProperty(name, value)
        }
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        if (dst !is Player) return
        cameraState.copyInto(dst.cameraState)
        dst.sessionInfo.clear()
        dst.sessionInfo.putAll(sessionInfo)
        dst.persistentInfo.clear()
        dst.persistentInfo.putAll(persistentInfo)
    }
}