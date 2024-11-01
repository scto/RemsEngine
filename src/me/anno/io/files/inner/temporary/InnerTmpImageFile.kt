package me.anno.io.files.inner.temporary

import me.anno.utils.async.Callback
import me.anno.image.Image
import me.anno.image.ImageReadable
import me.anno.io.files.FileReference
import me.anno.utils.structures.tuples.IntPair
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class InnerTmpImageFile(val image: Image, ext: String = "png") : InnerTmpFile(ext), ImageReadable {

    init {
        val size = image.sizeGuess()
        this.size = size
        this.compressedSize = size
    }

    val text = lazy { "" } // we could write a text based image here
    val bytes = lazy {
        val bos = ByteArrayOutputStream(1024)
        image.write(bos, "png")
        bos.toByteArray()
    }

    override fun isSerializedFolder(): Boolean = false
    override fun listChildren(): List<FileReference> = emptyList()

    override fun inputStreamSync() = ByteArrayInputStream(readBytesSync())
    override fun readBytes(callback: Callback<ByteArray>) {
        callback.ok(readBytesSync())
    }

    override fun readBytesSync(): ByteArray = bytes.value

    override fun readCPUImage(): Image = image
    override fun readGPUImage(): Image = image
    override fun readSize(): IntPair = IntPair(image.width, image.height)
}