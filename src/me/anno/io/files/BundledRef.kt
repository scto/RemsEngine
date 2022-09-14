package me.anno.io.files

import me.anno.io.BufferedIO.useBuffered
import me.anno.utils.LOGGER
import java.io.*
import java.net.URI
import java.util.zip.ZipInputStream

// internally in the JAR
class BundledRef(
    private val resName: String, absolute: String = "$prefix$resName",
    override val isDirectory: Boolean
) : FileReference(absolute) {

    constructor(resName: String) : this(resName, "$prefix$resName", false)

    // todo for the most important directories, e.g. asset directories,
    //  we could add index.txt files or sth like that, where all sub-files are listed
    // done for desktop: or we could identify where the zip jar is located, and traverse/index it

    override fun getChild(name: String) = getReference(zipFileForDirectory, name)

    override fun inputStream(lengthLimit: Long, callback: (it: InputStream?, exc: Exception?) -> Unit) {
        // needs to be the same package
        val stream = BundledRef::class.java.classLoader.getResourceAsStream(resName)
        callback(stream?.useBuffered(), if (stream == null) FileNotFoundException(absolutePath) else null)
    }

    override fun outputStream(append: Boolean): OutputStream {
        throw IllegalAccessException("Cannot write to internal files")
    }

    override val exists: Boolean = true // mmh...
    override fun length(): Long {
        var length = 0L
        inputStreamSync().use {
            when (it) {
                is ByteArrayInputStream ->
                    length = it.available().toLong()
                else -> {
                    // todo this doesn't work :/
                    // https://stackoverflow.com/questions/34360826/get-the-size-of-a-resource might work, when we find the correct jar
                    var test = 1L shl 16 // 65k .. as large as needed
                    while (test > 0L) {
                        val skipped = it.skip(test)
                        if (skipped <= 0) break
                        length += skipped
                        test = test shl 1
                    }
                }
            }
        }
        return length
    }

    override fun delete(): Boolean {
        throw IllegalAccessException("Cannot write to internal files")
    }

    override fun mkdirs(): Boolean {
        throw IllegalAccessException("Cannot write to internal files")
    }

    override fun renameTo(newName: FileReference): Boolean {
        throw IllegalAccessException("Cannot write to internal files")
    }

    private val cachedParent by lazy {
        // check whether / is in path
        val li = resName.lastIndexOf('/')
        if (li >= 0) {
            val newName = resName.substring(0, li)
            val newPath = absolutePath.substring(0, li + prefix.length)
            BundledRef(newName, newPath, true)
        } else jarAsZip2
    }

    override fun getParent() = cachedParent

    override val lastModified: Long = 0L
    override val lastAccessed: Long = 0L

    override fun toUri(): URI {// mmh...
        return URI(absolutePath)
    }

    companion object {

        fun parse(str: String): FileReference {
            if (!str.startsWith(prefix, true)) throw IllegalArgumentException()
            val mainName = str.substring(prefix.length)
            if (mainName.indexOf('/') >= 0 && jarAsZip.isNotEmpty()) {

                // find whether any prefix is good enough, like the search for the files
                val index = jarAsZip
                val parts = str.lowercase().split('/', '\\')

                // binary search? let's do linear first
                for (i in parts.lastIndex downTo 0) {
                    val substr = parts.subList(0, i).joinToString("/")
                    if (substr in index) {
                        // great :), now go into that file
                        val baseFile = BundledRef(substr, prefix + substr, false)
                        return appendPath(baseFile, i, parts)
                    }
                }
            }
            // is directory may be false...
            return BundledRef(mainName, str, false)
        }

        const val prefix = "res://"

        private val jarAsZip3 by lazy {
            try {
                File(
                    Companion::class.java
                        .protectionDomain
                        .codeSource
                        .location
                        .toURI()
                )
            } catch (e: NullPointerException) {
                e.printStackTrace()
                null
            }
        }

        private val jarAsZip2 by lazy { getReference(jarAsZip3) }

        private val jarAsZip by lazy {

            // we would just need the file structure:
            // what folders are actually folders, and which are not;

            // todo when we ship the game, we can just pack this data into some kind of txt file
            // todo required data: HashSet<FileNameLowerCase>

            // find this jar file as zip
            try {
                // we only look at the file names, so it should be relatively quick
                // ... needs a whole second, unfortunately :/
                val t0 = System.nanoTime()
                val zos = ZipInputStream(jarAsZip3!!.inputStream().buffered())
                val index = HashSet<String>(4096)
                while (true) {
                    val entry = zos.nextEntry ?: break
                    if (entry.isDirectory) continue
                    index.add(entry.name.lowercase())
                }
                zos.close()
                val t1 = System.nanoTime()
                LOGGER.info("Used ${(t1 - t0) * 1e-9f}s for indexing internal assets")
                index
            } catch (e: NullPointerException) {
                e.printStackTrace()
                emptySet()
            }
        }
    }

}