package me.anno.io.files

import me.anno.io.VoidOutputStream
import me.anno.io.files.Reference.getReference
import me.anno.utils.structures.Callback
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object FileRootRef : FileReference("root") {

    private val LOGGER = LogManager.getLogger(FileRootRef::class)

    override fun inputStream(lengthLimit: Long, closeStream: Boolean, callback: Callback<InputStream>) {
        callback.err(IOException("Cannot open root as stream"))
    }

    override fun outputStream(append: Boolean): OutputStream {
        LOGGER.warn("Cannot write to FileRootRef")
        return VoidOutputStream
    }

    override fun length() = 0L

    /**
     * Whatever you're trying to do, it's horrendous;
     * This function must not be called.
     * */
    override fun delete(): Boolean {
        throw Error("WTF are you trying to do? This call would have deleted your whole computer!")
    }

    override fun deleteOnExit() {
        delete()
    }

    override fun mkdirs(): Boolean = true

    override fun listChildren(): List<FileReference> {
        return File.listRoots().map { getReference(it.absolutePath) }
    }

    override fun getChildImpl(name: String): FileReference {
        val file = File.listRoots().firstOrNull { it.name == name } ?: return InvalidRef
        return getReference(file.absolutePath)
    }

    override fun getParent(): FileReference = InvalidRef

    override fun renameTo(newName: FileReference): Boolean {
        return false
    }

    override val isDirectory: Boolean get() = true
    override val exists: Boolean get() = true
    override val lastModified: Long get() = 0L
    override val lastAccessed: Long get() = 0L
    override val creationTime: Long get() = 0L
}