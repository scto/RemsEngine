package me.anno.objects

import me.anno.audio.AudioFXCache
import me.anno.audio.effects.Domain
import me.anno.audio.effects.Time
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFXx2D.getSize
import me.anno.gpu.GFXx2D.getSizeX
import me.anno.gpu.GFXx2D.getSizeY
import me.anno.io.FileReference
import me.anno.io.base.BaseWriter
import me.anno.objects.modes.LoopingState
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.utils.Maths.clamp
import me.anno.utils.Maths.fract
import me.anno.utils.Maths.mix
import me.anno.utils.OS
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.video.FFMPEGMetadata
import me.anno.video.MissingFrameException
import org.joml.Matrix4fArrayList
import org.joml.Vector4fc
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

// todo option for gaussian smoothing
// todo option for sample rate
// todo option for min and max index

class FourierTransform : Transform() {

    override fun getDefaultDisplayName(): String = "Fourier Transform"

    val meta get() = FFMPEGMetadata.getMeta(file, true)
    val forcedMeta get() = FFMPEGMetadata.getMeta(file, false)

    // todo support audio effects stack?
    var file = FileReference("")
    var getIndexParent: Transform? = null

    fun getIndexAndSize(): Int {

        var parent = parent
        if (parent == null) {
            mostRecentWarning = "Missing parent"
            return -1
        }

        val parent0 = parent
        val targetParent = getIndexParent
        if (targetParent != null && targetParent !== parent) {
            while (parent != null) {
                val nextParent = parent.parent
                if (nextParent == targetParent) {
                    // found it :)
                    return getIndexAndSize(parent, nextParent)
                }
                parent = nextParent
            }
            mostRecentWarning = "Index parent must be in direct line of inheritance"
        }

        return getIndexAndSize(this, parent0)

    }

    fun getIndexAndSize(child: Transform, parent: Transform): Int {
        val index = child.indexInParent
        val size = parent.drawnChildCount
        return getSize(kotlin.math.max(index, 0), size)
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4fc) {


        stack.next {

            // todo index = index in first parent with more than x children? we need references...
            // todo for that we need to drag transforms into other fields...

            val indexSize = getIndexAndSize()
            val index = getSizeX(indexSize)
            val size = getSizeY(indexSize)

            if (index < size) {
                // get the two nearest fourier transforms, and interpolate them
                val meta = forcedMeta
                if (meta != null) {
                    val sampleRate = 48000
                    val bufferIndex = AudioFXCache.getIndex(time, sampleRate)
                    val bufferIndex0 = floor(bufferIndex).toLong()
                    val bufferTime0 = Time(AudioFXCache.getTime(bufferIndex0 + 0, sampleRate))
                    val bufferTime1 = Time(AudioFXCache.getTime(bufferIndex0 + 1, sampleRate))
                    val bufferTime2 = Time(AudioFXCache.getTime(bufferIndex0 + 2, sampleRate))
                    val buff0 = getBuffer(meta, bufferIndex0 + 0, bufferTime0, bufferTime1)
                    val buff1 = getBuffer(meta, bufferIndex0 + 1, bufferTime1, bufferTime2)
                    if (buff0 != null && buff1 != null) {

                        val relativeIndex0 = index.toFloat() / size
                        val relativeIndex1 = (index + 1f) / size
                        val bufferSize = buff0.first.size / 2

                        var indexInBuffer0 = (relativeIndex0 * 0.5f * bufferSize).toInt()
                        var indexInBuffer1 = (relativeIndex1 * 0.5f * bufferSize).toInt()

                        if (indexInBuffer1 <= indexInBuffer0) indexInBuffer1 = indexInBuffer0 + 1
                        if (indexInBuffer0 >= bufferSize) indexInBuffer0 = bufferSize - 1
                        if (indexInBuffer1 > bufferSize) indexInBuffer1 = bufferSize

                        val fractIndex = fract(bufferIndex).toFloat()
                        val amplitude0 = getAmplitude(indexInBuffer0, indexInBuffer1, buff0)
                        val amplitude1 = getAmplitude(indexInBuffer0, indexInBuffer1, buff1)
                        val amplitude = mix(amplitude0, amplitude1, fractIndex)
                        val relativeAmplitude = amplitude / 32e3f
                        // todo interpolate all matching positions
                        stack.scale(1f, relativeAmplitude, 1f)
                    } else throw RuntimeException("null, why???")
                }
            }

            super.onDraw(stack, time, color)

            drawChildren(stack, time, color)

            // todo change the transform based on the audio levels
            // todo multiplicative effect for children

            // todo choose left / right / average?
            // (for stereo music this may be really cool)

            // todo change rotation as well?
            // todo and translation :D

        }
    }

    class MiniCache(var data: Pair<FloatArray, FloatArray>? = null, var index: Long = 0)

    val lastBuffers = Array(10) { MiniCache() }

    fun getAmplitude(index0: Int, index1: Int, buffer: Pair<FloatArray, FloatArray>): Float {
        return (getAmplitude(index0, index1, buffer.first) + getAmplitude(index0, index1, buffer.second)) * 0.5f
    }

    fun getAmplitude(index0: Int, index1: Int, buffer: FloatArray): Float {
        var sum = 0f
        if (index1 <= index0) throw IllegalArgumentException()
        for (index in index0 until index1) {
            val real = buffer[index * 2]
            val imag = buffer[index * 2 + 1]
            sum += real * real + imag * imag
        }
        return sqrt(sum / (index1 - index0))
    }

    fun debugSpectrum(index: Int, buffer: FloatArray) {
        if (index == 0) {
            val avg = buffer.map { abs(it) }.average().toInt()
            val file = File(OS.desktop.file, buffer.hashCode().toString() + ".$avg.png")
            if (!file.exists()) {
                val size = kotlin.math.min(1024, buffer.size)
                val img = BufferedImage(size, size, 1)
                val max = buffer.map { abs(it) }.max()!!
                for (x in 0 until size) {
                    val bi0 = x * size / buffer.size
                    val bi1 = kotlin.math.max(bi0 + 1, (x + 1) * size / buffer.size)
                    val bufferValue = buffer.toList().subList(bi0, bi1).map { abs(it) }.max()!!
                    val h = size - clamp((bufferValue * size / max).toInt(), 1, size)
                    for (y in 0 until h) {
                        img.setRGB(x, y, 0)
                    }
                    for (y in h until size) {
                        img.setRGB(x, y, -1)
                    }
                }
                ImageIO.write(img, "png", file)
            }
        }
    }

    var loopingState = LoopingState.PLAY_LOOP
    // todo option to change the domain? may be nice, but otherwise, it's really fast...

    fun getKey(sampleIndex0: Long): AudioFXCache.PipelineKey {
        val sampleRate = 48000
        val t0 = Time(AudioFXCache.getTime(sampleIndex0 + 0, sampleRate))
        val t1 = Time(AudioFXCache.getTime(sampleIndex0 + 1, sampleRate))
        return getKey(sampleIndex0, t0, t1)
    }

    fun getKey(sampleIndex0: Long, sampleTime0: Time, sampleTime1: Time): AudioFXCache.PipelineKey {
        return AudioFXCache.PipelineKey(
            sampleIndex0,
            file,
            sampleTime0,
            sampleTime1,
            1.0,
            false,
            "",
            loopingState,
            null
        )
    }

    fun getBuffer(
        meta: FFMPEGMetadata,
        bufferIndex: Long,
        sampleTime0: Time,
        sampleTime1: Time
    ): Pair<FloatArray, FloatArray>? {
        val data = AudioFXCache.getBuffer0(meta, getKey(bufferIndex, sampleTime0, sampleTime1), false)
        if (isFinalRendering && data == null) throw MissingFrameException(file)
        if (data == null) return null
        val data2 = data.getBuffersOfDomain(Domain.FREQUENCY_DOMAIN)
        for (buffer in lastBuffers) {
            if (buffer.data == null) {
                buffer.data = data2
                buffer.index = bufferIndex
                return data2
            }
        }
        return data2
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(list, style, getGroup)
        val fourier = getGroup("Fourier Transform", "", "")
        fourier.add(vi("Audio File", "", null, file, style) { file = it })
    }

    override fun drawChildrenAutomatically(): Boolean = false

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "file" -> file = value.toGlobalFile()
            else -> super.readString(name, value)
        }
    }

    override fun getClassName(): String = "FourierTransform"

}