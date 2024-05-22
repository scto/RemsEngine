package me.anno.engine.history

import me.anno.gpu.GFX
import me.anno.io.saveable.Saveable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import org.apache.logging.log4j.LogManager
import java.util.LinkedList
import kotlin.math.max

abstract class History<V : Any> : Saveable() {

    abstract fun apply(prev: V, curr: V)
    abstract fun getTitle(v: V): String
    abstract fun filter(v: Any?): V?

    var currentState: V? = null
    var nextInsertIndex = 0
        set(value) {
            field = max(value, 0)
        }

    val states = LinkedList<V>()

    fun isEmpty() = states.isEmpty()

    val numStates get() = states.size

    fun clearToSize(targetSize: Int = maxChanged) {
        synchronized(states) {
            while (states.size > targetSize && targetSize > 0) {
                states.removeFirst()
                nextInsertIndex--
            }
        }
    }

    fun put(change: V): Int {
        return synchronized(states) {
            states.add(change)
            clearToSize()
            nextInsertIndex = states.size
            currentState = states.last()
            nextInsertIndex
        }
    }

    fun redo(): Boolean {
        return synchronized(states) {
            if (nextInsertIndex < states.size) {
                val nextState = states[nextInsertIndex++]
                apply(currentState!!, nextState)
                currentState = nextState
                true
            } else {
                LOGGER.info("Nothing left to redo!")
                false
            }
        }
    }

    fun undo(): Boolean {
        return synchronized(states) {
            if (nextInsertIndex >= 2) {
                nextInsertIndex--
                val nextState = states[nextInsertIndex - 1]
                apply(currentState!!, nextState)
                currentState = nextState
                true
            } else {
                LOGGER.info("Nothing left to undo!")
                false
            }
        }
    }

    private fun redo(index: Int) {
        val nextState = states.getOrNull(index) ?: return
        apply(currentState!!, nextState)
        currentState = nextState
    }

    fun display() {
        openMenu(
            GFX.someWindow.windowStack,
            NameDesc("Inspect History", "", "ui.inspectHistory"),
            states.mapIndexed { index, change ->
                val title0 = getTitle(change)
                val title = if (index == nextInsertIndex - 1) "* $title0" else title0
                MenuOption(NameDesc(title, Dict["Click to redo", "ui.history.clickToUndo"], "")) {
                    redo(index)
                }
            }.reversed()
        )
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "nextInsertIndex" -> nextInsertIndex = value as? Int ?: return
            "state" -> states.add(filter(value) ?: return)
            "states" -> {
                states.clear()
                states.addAll((value as List<*>).mapNotNull { filter(it) })
            }
            else -> super.setProperty(name, value)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeInt("nextInsertIndex", nextInsertIndex)
        if (states.isNotEmpty()) {
            synchronized(states) {
                saveStates(writer)
            }
        }
    }

    open fun saveStates(writer: BaseWriter) {
        writer.writeSomething(null, "states", states, true)
    }

    override val approxSize get() = 1_500_000_000

    companion object {
        private val LOGGER = LogManager.getLogger(History::class)
        var maxChanged = 512
    }
}