package me.anno.tests.files

import me.anno.io.files.FileFileRef
import me.anno.utils.structures.Iterators.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadFilesSyncBroken {
    @Test
    fun testReadLinesSync() {
        val file = FileFileRef.createTempFile("lines", "txt")
        val lines = "hey\ndu\nwas machst\ndu so?\n\n"
        file.writeText(lines)
        val readLines = file.readLinesSync(1024).toList()
        assertEquals(lines.split('\n'), readLines)
        file.delete()
    }
}