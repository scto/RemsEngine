package me.anno.tests.mesh

import me.anno.config.DefaultConfig.style
import me.anno.engine.ECSRegistry
import me.anno.ui.debug.TestStudio.Companion.testUI
import me.anno.ui.editor.files.FileExplorer
import me.anno.utils.OS.downloads

fun main() {
    testUI("Vox") {
        ECSRegistry.initMeshes()
        FileExplorer(downloads.getChild("MagicaVoxel/vox"), true, style)
    }
}