package me.anno.io.zip

import me.anno.io.files.FileReference
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

class InnerLinkFile(
    absolutePath: String,
    relativePath: String,
    _parent: FileReference,
    val link: FileReference
) : InnerFile(absolutePath, relativePath, link.isDirectory, _parent) {

    constructor(folder: InnerFolder, name: String, content: FileReference) : this(
        "${folder.absolutePath}/$name",
        "${folder.relativePath}/$name",
        folder,
        content
    )

    init {
        if (link is InnerFile) {
            data = link.data
            lastModified = link.lastModified
            lastAccessed = link.lastAccessed
            size = link.size
            compressedSize = link.compressedSize
        }
    }

    override val isSomeKindOfDirectory: Boolean
        get() = link.isSomeKindOfDirectory

    override fun getInputStream(callback: (InputStream?, Exception?) -> Unit) {
        link.inputStream(Long.MAX_VALUE, callback)
    }

    override fun readBytesSync(): ByteArray = link.readBytesSync()
    override fun readTextSync(): String = link.readTextSync()
    override fun readByteBufferSync(native: Boolean): ByteBuffer = link.readByteBufferSync(native)
    override fun readText(charset: Charset, callback: (String?, Exception?) -> Unit) {
        link.readText(charset, callback)
    }

    override fun readBytes(callback: (it: ByteArray?, exc: Exception?) -> Unit) {
        link.readBytes(callback)
    }

    override fun readByteBuffer(native: Boolean, callback: (ByteBuffer?, Exception?) -> Unit) {
        link.readByteBuffer(native, callback)
    }

    override fun length() = link.length()

    override val exists: Boolean = link.exists

}