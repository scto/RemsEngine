package me.anno.io.files.inner

import me.anno.io.EmptyInputStream
import me.anno.io.VoidOutputStream
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.maths.Maths.max
import me.anno.utils.async.Callback
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * a file, which is inside another file,
 * e.g., inside a zip file, or inside a mesh
 * */
abstract class InnerFile(
    absolutePath: String,
    val relativePath: String,
    final override val isDirectory: Boolean,
    val _parent: FileReference
) : FileReference(absolutePath) {

    var folder: InnerFile? = null

    val lcName = name.lowercase()

    init {
        if (_parent is InnerFolder) {
            val pc = _parent.children
            val pcl = _parent.childrenList
            synchronized(pc) {
                val old = _parent.children.put(name, this)
                pcl.add(this)
                if (old != null) {
                    pcl.remove(old)
                    old.folder = this
                    pc[name] = old
                    // LOGGER.warn("Overrode $old")
                }
            }
        }
    }

    // assigned typically anyway; millisecond timestamps like usual for Java
    override var lastModified = 0L
    override var lastAccessed = 0L
    override var creationTime = 0L

    var compressedSize = 0L
    var size = 65536L // we don't know in this class
    var data: ByteArray? = null
    var isEncrypted = false

    override fun length(): Long = size

    fun markAsModified() {
        lastModified = max(lastModified + 1, System.currentTimeMillis())
    }

    override fun invalidate() {
        super.invalidate()
        markAsModified()
    }

    override fun inputStream(lengthLimit: Long, closeStream: Boolean, callback: Callback<InputStream>) {
        val data = data
        when {
            size <= 0 -> callback.call(EmptyInputStream, null)
            data != null -> callback.call(ByteArrayInputStream(data), null)
            else -> callback.ok(inputStreamSync())
        }
    }

    override fun readBytes(callback: Callback<ByteArray>) {
        val data = data
        if (data != null) callback.ok(data)
        else super.readBytes(callback)
    }

    override fun readBytesSync(): ByteArray {
        return data ?: super.readBytesSync()
    }

    override fun readText(callback: Callback<String>) {
        callback.ok(readTextSync())
    }

    override fun readTextSync(): String {
        return readBytesSync().decodeToString()
    }

    override fun outputStream(append: Boolean): OutputStream {
        LOGGER.warn("Writing into zip files is not yet supported, '$absolutePath'")
        return VoidOutputStream
    }

    fun get(path: String) = getLc(path.replace('\\', '/').lowercase())

    open fun getLc(path: String): FileReference? {
        if (path.isEmpty()) return InnerFolderCache.readAsFolder(this, false)
        val m = InnerFolderCache.readAsFolder(this, false)
        return m?.getLc(path)
    }

    override fun getChildImpl(name: String): FileReference {
        return InnerFolderCache.readAsFolder(this, false)?.getChild(name) ?: InvalidRef
    }

    override val exists: Boolean = true

    override fun deleteOnExit() {
        delete()
    }

    override fun delete(): Boolean {
        LOGGER.warn("Writing into zip files is not yet supported")
        return false
    }

    override fun mkdirs(): Boolean {
        return false
    }

    override fun listChildren(): List<FileReference> {
        return zipFileForDirectory?.listChildren() ?: emptyList()
    }

    override fun getParent(): FileReference {
        return _parent
    }

    override fun renameTo(newName: FileReference): Boolean {
        LOGGER.warn("Renaming inside zip files is not yet supported")
        return false
    }

    companion object {

        private val LOGGER = LogManager.getLogger(InnerFile::class)

        @JvmStatic
        fun createMainFolder(zipFileLocation: FileReference): Pair<InnerFolder, HashMap<String, InnerFile>> {
            val file = InnerFolder(zipFileLocation)
            return file to createRegistry(file)
        }

        @JvmStatic
        fun createRegistry(file: InnerFile): HashMap<String, InnerFile> {
            val registry = HashMap<String, InnerFile>()
            registry[""] = file
            return registry
        }
    }
}