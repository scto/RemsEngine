package me.anno.io

import me.anno.io.base.BaseWriter

/**
 * something that should be saveable, but also nameable by editors or users
 * */
open class NamedSaveable : Saveable() {

    open var name = ""
    open var description = ""

    override val approxSize get() = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("name", name)
        writer.writeString("desc", description)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "name" -> this.name = value as? String ?: return
            "desc", "description" -> this.description = value as? String ?: return
            else -> super.setProperty(name, value)
        }
    }
}