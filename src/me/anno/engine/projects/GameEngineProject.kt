package me.anno.engine.projects

import me.anno.Engine
import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.EngineBase
import me.anno.engine.Events
import me.anno.engine.ScenePrefab
import me.anno.engine.ui.render.PlayMode
import me.anno.engine.ui.scenetabs.ECSSceneTabs
import me.anno.extensions.events.Event
import me.anno.gpu.GFX
import me.anno.io.saveable.NamedSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.files.Signature
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.ui.base.progress.ProgressBar
import me.anno.utils.OS
import me.anno.utils.files.LocalFile.toGlobalFile
import org.apache.logging.log4j.LogManager
import kotlin.concurrent.thread

class GameEngineProject() : NamedSaveable() {

    /**
     * is called when a project has been loaded
     * */
    class ProjectLoadedEvent(val project: GameEngineProject) : Event()

    companion object {

        var currentProject: GameEngineProject? = null

        private val LOGGER = LogManager.getLogger(GameEngineProject::class)
        fun readOrCreate(location: FileReference?): GameEngineProject? {
            location ?: return null
            if (location == InvalidRef) return null
            return if (location.exists) {
                if (location.isDirectory) {
                    val configFile = location.getChild("Project.json")
                    if (configFile.exists) {
                        val instance = JsonStringReader.readFirstOrNull(configFile, location, GameEngineProject::class)
                        instance?.location = location
                        instance
                    } else {
                        val project = GameEngineProject(location)
                        configFile.writeText(JsonStringWriter.toText(project, location))
                        project
                    }
                } else {
                    // probably the config file
                    readOrCreate(location.getParent())
                }
            } else GameEngineProject(location)
        }
    }

    constructor(location: FileReference) : this() {
        this.location = location
        location.tryMkdirs()
    }

    var location: FileReference = InvalidRef // a folder
    var lastScene: String = ""
    val openTabs = HashSet<String>()

    val configFile get() = location.getChild("Project.json")

    val assetIndex = HashMap<String, HashSet<FileReference>>()
    var maxIndexDepth = 5

    fun addToIndex(file: FileReference, type: String) {
        assetIndex.getOrPut(type) { HashSet() }.add(file)
    }

    private var isValid = true

    /**
     * save project config after a small delay
     * */
    fun saveMaybe() {
        if (isValid) {
            isValid = false
            Events.addEvent {
                if (!isValid) {
                    isValid = true
                    configFile.writeText(JsonStringWriter.toText(this, location))
                    LOGGER.info("Saved Project")
                }
            }
        }
    }

    fun init() {

        EngineBase.workspace = location

        // if last scene is invalid, create a valid scene
        if (lastScene == "" || lastScene.startsWith("tmp://")) {
            lastScene = location.getChild("Scene.json").absolutePath
            LOGGER.info("Set scene to $lastScene")
        }

        val lastSceneRef = lastScene.toGlobalFile(location)
        if (!lastSceneRef.exists && lastSceneRef != InvalidRef) {
            val prefab = Prefab("Entity", ScenePrefab)
            lastSceneRef.getParent().tryMkdirs()
            lastSceneRef.writeText(JsonStringWriter.toText(prefab, InvalidRef))
            LOGGER.warn("Wrote new scene to $lastScene")
        }

        assetIndex.clear()

        // may be changed by ECSSceneTabs otherwise
        val lastScene = lastScene

        // open all tabs
        for (tab in openTabs.toList()) {
            try {
                ECSSceneTabs.open(tab.toGlobalFile(EngineBase.workspace), PlayMode.EDITING, false)
            } catch (e: Exception) {
                LOGGER.warn("Could not open $tab", e)
            }
        }

        // make last scene current
        try {
            ECSSceneTabs.open(lastSceneRef, PlayMode.EDITING, true)
        } catch (e: Exception) {
            LOGGER.warn("Could not open $lastScene", e)
        }

        // to do if is Web, provide mechanism to index files...
        // we can't really do that anyway...
        if (!OS.isWeb) {
            thread(name = "Indexing Resources") {
                val progressBar = GFX.someWindow.addProgressBar(object : ProgressBar("Indexing Assets", "Files", 1.0) {
                    override fun formatProgress(): String {
                        return "$name: ${progress.toLong()} / ${total.toLong()} $unit"
                    }
                })
                val filesToIndex = ArrayList<FileReference>()
                indexFolder(progressBar, location, maxIndexDepth, filesToIndex)
                while (!Engine.shutdown && filesToIndex.isNotEmpty() && !progressBar.isCancelled) {
                    if (Engine.shutdown) break
                    progressBar.progress += 1.0
                    val fileToIndex = filesToIndex.removeFirst()
                    indexResource(fileToIndex)
                }
                progressBar.finish(true)
            }
        }
    }

    fun indexFolder(
        progressBar: ProgressBar,
        file: FileReference, depth: Int,
        resourcesToIndex: MutableCollection<FileReference>
    ) {
        val depthM1 = depth - 1
        if (!file.isDirectory || Engine.shutdown || progressBar.isCancelled) return
        val children = file.listChildren()
        progressBar.total += children.size
        progressBar.progress += 1.0
        for (child in children) {
            if (child.isDirectory) {
                if (depthM1 >= 0) {
                    indexFolder(progressBar, child, depthM1, resourcesToIndex)
                }
            } else resourcesToIndex.add(child)
        }
    }

    fun indexResource(file: FileReference) {
        if (file.isDirectory) return
        Signature.findName(file) { sign, _ ->
            when (sign) {
                "png", "jpg", "exr", "qoi", "webp", "dds", "hdr", "ico", "gimp", "bmp" -> {
                    addToIndex(file, "Image") // cpu-side name
                    addToIndex(file, "Texture") // gpu-side name
                }
                "blend", "gltf", "dae", "md2", "vox", "fbx", "obj", "ma" -> addToIndex(file, "Mesh")
                "media", "gif" -> addToIndex(file, "Media")
                "pdf" -> addToIndex(file, "PDF")
                "ttf", "woff1", "woff2" -> addToIndex(file, "Font")
                else -> {
                    val timeout = 0L // because we don't really need it
                    val prefab = PrefabCache[file, Prefab.maxPrefabDepth, timeout, false]
                    if (prefab != null) {
                        addToIndex(file, prefab.clazzName)
                    }
                }
            }
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("lastScene", lastScene)
        writer.writeStringList("openTabs", openTabs.toList())
        writer.writeInt("maxIndexDepth", maxIndexDepth)
        // location doesn't really need to be saved
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "lastScene" -> lastScene = value as? String ?: (value as? FileReference)?.absolutePath ?: return
            "maxIndexDepth" -> maxIndexDepth = value as? Int ?: return
            "openTabs" -> {
                val values = value as? List<*> ?: return
                openTabs.clear()
                openTabs.addAll(values.filterIsInstance<String>())
                openTabs.addAll(values.filterIsInstance<FileReference>().map { it.toLocalPath(location) })
            }
            else -> super.setProperty(name, value)
        }
    }

    override val approxSize get() = 1_000_000
}