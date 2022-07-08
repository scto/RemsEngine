package me.anno.engine.ui

import me.anno.config.DefaultConfig
import me.anno.ecs.interfaces.ControlReceiver
import me.anno.ecs.interfaces.CustomEditMode
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.ui.render.PlayMode
import me.anno.engine.ui.render.SceneView
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.studio.Inspectable
import me.anno.ui.Panel
import me.anno.ui.custom.Type
import me.anno.ui.custom.UITypeLibrary
import me.anno.ui.editor.PropertyInspector

object EditorState {

    lateinit var projectFile: FileReference
    var isGaming = false

    var prefabSource: FileReference = InvalidRef
    val prefab get() = PrefabCache[prefabSource]

    // todo box selecting with shift

    var control: ControlReceiver? = null
        set(value) {
            field = value
            if (value != null) editMode = null
        }

    var editMode: CustomEditMode? = null
        set(value) {
            field = value
            if (value != null) control = null
        }

    // todo we should be able to edit multiple values of the same type at the same time
    var selection: List<Inspectable> = emptyList()
    var fineSelection: List<Inspectable> = selection

    fun select(major: Inspectable?, minor: Inspectable? = major, add: Boolean = false) {
        if (add) {
            if (major != null) selection = added(selection, major)
            if (minor != null) fineSelection = added(fineSelection, minor)
            lastSelection = major ?: minor
        } else {
            selection = if (major == null) emptyList() else listOf(major)
            fineSelection = if (minor == null) emptyList() else listOf(minor)
            lastSelection = major ?: minor
        }
    }

    private fun <V> added(list: List<V>, element: V): List<V> {
        return if (list is MutableList) {
            try {
                list.add(element)
                list
            } catch (e: UnsupportedOperationException) {
                list + element
            }
        } else {
            list + element
        }
    }

    fun unselect(element: Inspectable) {
        selection = selection.filter { it != element }
        fineSelection = fineSelection.filter { it != element }
        if (lastSelection == element) lastSelection = null
    }

    val typeList = listOf<Pair<String, () -> Panel>>(
        // todo not all stuff here makes sense
        // todo some stuff is (maybe) missing, e.g. animation panels, particle system editors, ...
        Dict["Scene View", "ui.customize.sceneView"] to { SceneView(this, PlayMode.EDITING, DefaultConfig.style) },
        Dict["Tree View", "ui.customize.treeView"] to { ECSTreeView(this, DefaultConfig.style) },
        Dict["Properties", "ui.customize.inspector"] to { PropertyInspector({ selection }, DefaultConfig.style) },
        // Dict["Cutting Panel", "ui.customize.cuttingPanel"] to { CuttingView(DefaultConfig.style) },
        // Dict["Timeline", "ui.customize.timeline"] to { TimelinePanel(DefaultConfig.style) },
        // Dict["Animations", "ui.customize.graphEditor"] to { GraphEditor(DefaultConfig.style) },
        Dict["Files", "ui.customize.fileExplorer"] to { ECSFileExplorer(projectFile, DefaultConfig.style) }
    ).map { Type(it.first, it.second) }.toMutableList()

    val uiLibrary = UITypeLibrary(typeList)

    var lastSelection: Inspectable? = null

}