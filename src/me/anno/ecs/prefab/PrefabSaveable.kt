package me.anno.ecs.prefab

import me.anno.ecs.prefab.change.Path
import me.anno.io.ISaveable
import me.anno.io.NamedSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.serialization.NotSerializedProperty
import me.anno.io.serialization.SerializedProperty
import me.anno.objects.inspectable.Inspectable
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.stacked.Option
import me.anno.ui.style.Style
import me.anno.utils.LOGGER
import me.anno.utils.structures.Hierarchical

abstract class PrefabSaveable : NamedSaveable(), Hierarchical<PrefabSaveable>, Inspectable, Cloneable {

    @SerializedProperty
    override var isEnabled = true

    @NotSerializedProperty // ideally, this would have the default value "depth>3" or root.numChildrenAtDepth(depth)>100
    override var isCollapsed = true

    // todo when creating a new prefab, this needs to be set
    var id: String = ""

    // @NotSerializedProperty
    // var prefab: PrefabSaveable? = null
    val prefab: PrefabSaveable? get() = prefab2?.getSampleInstance()
    val prefabOrDefault: PrefabSaveable get() = prefab ?: getSuperInstance(className)

    @NotSerializedProperty
    var prefab2: Prefab? = null

    @NotSerializedProperty
    override var parent: PrefabSaveable? = null


    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeBoolean("isCollapsed", isCollapsed)
    }

    override fun readBoolean(name: String, value: Boolean) {
        when(name){
            "isCollapsed" -> isCollapsed = value
            else -> super.readBoolean(name, value)
        }
    }

    fun getDefaultValue(name: String): Any? {
        return (prefabOrDefault)[name]
    }

    fun resetProperty(name: String): Any? {
        // how do we find the default value, if the root is null? -> create an empty copy
        val defaultValue = getDefaultValue(name)
        this[name] = defaultValue
        LOGGER.info("Reset $className/$name to $defaultValue")
        return defaultValue
    }

    private fun pathInRoot(root: PrefabSaveable? = null): ArrayList<Any> {
        if (this == root) return ArrayList()
        val parent = parent
        return if (parent != null) {
            val index = parent.getIndexOf(this)
            val list = parent.pathInRoot()
            list.add(name)
            list.add(index)
            list.add(parent.getTypeOf(this))
            return list
        } else ArrayList()
    }

    fun pathInRoot2() = pathInRoot2(null, false)
    fun pathInRoot2(root: PrefabSaveable? = null, withExtra: Boolean): Path {
        val path = pathInRoot(root)
        if (withExtra) {
            path.add("")
            path.add(-1)
            path.add(' ')
        }
        val size = path.size / 3
        val names = Array(size) { path[it * 3] as String }
        val ids = IntArray(size) { path[it * 3 + 1] as Int }
        val types = CharArray(size) { path[it * 3 + 2] as Char }
        return Path(names, ids, types)
    }

    // e.g. "ec" for child entities + child components
    open fun listChildTypes(): String = ""
    open fun getChildListByType(type: Char): List<PrefabSaveable> = emptyList()
    open fun getChildListNiceName(type: Char): String = ""
    open fun addChildByType(index: Int, type: Char, instance: PrefabSaveable) {}
    open fun getOptionsByType(type: Char): List<Option>? = null

    override fun add(child: PrefabSaveable) {
        val type = getTypeOf(child)
        val length = getChildListByType(type).size
        addChildByType(length, type, child)
    }

    override fun add(index: Int, child: PrefabSaveable) = addChildByType(index, getTypeOf(child), child)
    override fun deleteChild(child: PrefabSaveable) {
        val list = getChildListByType(getTypeOf(child))
        val index = list.indexOf(child)
        if (index < 0) return
        list as MutableList<*>
        list.remove(child)
    }

    open fun getIndexOf(child: PrefabSaveable): Int = getChildListByType(getTypeOf(child)).indexOf(child)
    open fun getTypeOf(child: PrefabSaveable): Char = ' '

    public abstract override fun clone(): PrefabSaveable
    open fun copy(clone: PrefabSaveable) {
        clone.name = name
        clone.description = description
        clone.isEnabled = isEnabled
        clone.isCollapsed = isCollapsed
        clone.prefab2 = prefab2
    }

    override fun onDestroy() {}

    @NotSerializedProperty
    override val symbol: String = ""

    @NotSerializedProperty
    override val defaultDisplayName: String
        get() = name

    @NotSerializedProperty
    override val children: List<PrefabSaveable>
        get() = getChildListByType(listChildTypes()[0])

    override fun createInspector(
        list: PanelListY, style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        PrefabInspector.currentInspector?.inspect(this, list, style) ?: LOGGER.warn("Missing inspector!")
    }

    companion object {
        private fun getSuperInstance(className: String): PrefabSaveable {
            return ISaveable.getSample(className) as PrefabSaveable
        }
    }

}