package me.anno.studio.rems

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.baseTheme
import me.anno.gpu.GFX
import me.anno.gpu.GFX.gameTime
import me.anno.input.ActionManager
import me.anno.input.Input.keyUpCtr
import me.anno.installer.Installer.checkInstall
import me.anno.io.FileReference
import me.anno.language.translation.Dict
import me.anno.objects.Camera
import me.anno.objects.Transform
import me.anno.studio.GFXSettings
import me.anno.studio.StudioBase
import me.anno.studio.cli.RemsCLI
import me.anno.studio.project.Project
import me.anno.studio.rems.CheckVersion.checkVersion
import me.anno.ui.editor.PropertyInspector
import me.anno.ui.editor.TimelinePanel
import me.anno.ui.editor.UILayouts
import me.anno.ui.editor.sceneTabs.SceneTabs.currentTab
import me.anno.ui.editor.sceneView.ISceneView
import me.anno.ui.editor.treeView.TreeView
import me.anno.utils.OS

// Launch4j

// todo change the blue timeline color (3rd channel) to sth brighter

// todo comparison black and white to see what is too dark (?)
// todo when mixing strings, \n and spaces have a special role:
// todo - substring should not be longer in a line than the total

// todo tree view distances are broken somehow...

// todo settings should open in a window, that does not cover everything, just maybe 90%
// todo project settings and render settings maybe should be windows instead of being inspectables

// todo if a resource is requested, and there is a mutex limitation, it should be rejected instead of queued
// so we don't put strain on the cpu & memory, if we don't really need the resource
// this is the case for audio in the timeline, when just scrolling

// todo calculate in-between frames for video by motion vectors; https://www.youtube.com/watch?v=PEe-ZeVbTLo ?
// 30 -> 60 fps
// the topic is complicated...

// todo mode to move vertices of rectangle one by one (requires clever transforms, or a new type of GFXTransform)
// for perspective matching: e.g. moving a fake image onto another image

// todo create proxies only for sections of video
// todo proxies for faster playback? e.g. every 10th frame? idk...

// proxy creation uses 100% cpu... prevent that somehow, or decrease process priority?
// it uses 36% on its own -> heavier weight?
// -> idk how on Windows; done for Linux

// nearby frame compression (small changes between frames, could use lower resolution) on the gpu side? maybe...
// -> would maybe allow 60fps playback better


// todo Version 2:
// todo make stuff component based and compile shaders on the fly...
// would allow easy system for pdf particles and such... maybe...

// todo color to alpha
// todo alpha to color ???
// todo discard alpha being > or < than x / map alpha

// todo splines for the polygon line

// todo draw frame by frame, only save x,y,radius?
// todo record drawing, and use meta-ball like shapes (maybe)

// todo translations for everything...
// todo limit the history to entries with 5x the same name? how exactly?...

// todo saturation/lightness controls by hue

// to do Mod with "hacked"-text effect for text: swizzle characters and introduce others?

object RemsStudio : StudioBase(true, "Rem's Studio", 10103) {

    // private val LOGGER = LogManager.getLogger(RemsStudio::class)

    override fun onGameInit() {
        RemsConfig.init()
        gfxSettings = GFXSettings.get(DefaultConfig["editor.gfx", GFXSettings.LOW.id], GFXSettings.LOW)
        workspace = DefaultConfig["workspace.dir", FileReference(OS.documents, configName)]
        checkInstall()
        checkVersion()
    }

    override fun createUI() {
        Dict.loadDefault()
        UILayouts.createWelcomeUI()
        StudioActions.register()
        ActionManager.init()
    }

    var gfxSettings = GFXSettings.LOW
        set(value) {
            field = value
            DefaultConfig["editor.gfx"] = value.id
            DefaultConfig.putAll(value.data)
        }

    var project: Project? = null

    var editorTime = 0.5

    var editorTimeDilation = 0.0
        set(value) {
            if (value != field) updateAudio()
            field = value
        }

    val isPaused get() = editorTimeDilation == 0.0
    val isPlaying get() = editorTimeDilation != 0.0

    val targetDuration get() = project!!.targetDuration
    val targetSampleRate get() = project!!.targetSampleRate
    val targetFPS get() = project!!.targetFPS
    val targetWidth get() = project?.targetWidth ?: GFX.windowWidth
    val targetHeight get() = project?.targetHeight ?: GFX.windowHeight
    val targetOutputFile get() = project!!.targetOutputFile
    val motionBlurSteps get() = project!!.motionBlurSteps
    val shutterPercentage get() = project!!.shutterPercentage
    val history get() = currentTab?.history
    val nullCamera get() = project?.nullCamera

    var root = Transform()

    var currentlyDrawnCamera: Camera? = nullCamera

    val selection = ArrayList<String>()

    override fun onGameLoopStart() {
        saveStateMaybe()
        Selection.update()
    }

    override fun onGameLoopEnd() {

    }

    override fun onGameClose() {

    }

    fun saveStateMaybe() {
        DefaultConfig.saveMaybe("main.config")
        baseTheme.values.saveMaybe("style.config")
    }

    private var lastCode: Any? = null
    fun incrementalChange(title: String, run: () -> Unit) =
        incrementalChange(title, title, run)

    fun incrementalChange(title: String, groupCode: Any, run: () -> Unit) {
        val history = history ?: return run()
        val code = groupCode to keyUpCtr
        if (lastCode != code) {
            change(title, code, run)
            lastCode = code
        } else {
            run()
            history.update(title, code)
        }
        currentTab?.hasChanged = true
        updateSceneViews()
    }

    fun largeChange(title: String, run: () -> Unit) {
        change(title, gameTime, run)
        lastCode = null
        currentTab?.hasChanged = true
        updateSceneViews()
    }

    private fun change(title: String, code: Any, run: () -> Unit) {
        val history = history ?: return run()
        if (history.isEmpty()) {
            history.put("Start State", Unit)
        }
        run()
        history.put(title, code)
    }

    fun updateSceneViews() {
        // if(gameTime > 1e10) throw RuntimeException()
        // LOGGER.info("UpdateSceneViews ${gameTime / 1e9f}")
        for (window in GFX.windowStack) {
            for (panel in window.panel.listOfVisible) {
                when (panel) {
                    is TreeView, is ISceneView, is TimelinePanel -> {
                        panel.invalidateDrawing()
                    }
                    is PropertyInspector -> {
                        panel.invalidate()
                    }
                }
            }
        }
    }

    // UI with traditional editor?
    // - adding effects
    // - simple mask overlays
    // - simple color correction

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            run()
        } else {
            RemsCLI.main(args)
        }
    }

}