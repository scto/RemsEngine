package me.anno.video

import me.anno.gpu.GFX
import me.anno.io.FileReference
import me.anno.utils.ShutdownException
import me.anno.utils.Sleep.waitUntil
import me.anno.video.IsFFMPEGOnly.isFFMPEGOnlyExtension
import me.anno.video.formats.ARGBFrame
import me.anno.video.formats.BGRAFrame
import me.anno.video.formats.I420Frame
import me.anno.video.formats.RGBFrame
import org.apache.logging.log4j.LogManager
import java.io.InputStream
import kotlin.concurrent.thread

class FFMPEGVideo(
    file: FileReference,
    w: Int, h: Int,
    private val frame0: Int,
    bufferLength: Int,
    val frameCallback: (VFrame, Int) -> Unit
) : FFMPEGStream(file, isProcessCountLimited = !file.extension.isFFMPEGOnlyExtension()) {

    /*init {
        LOGGER.info("${file.name.substring(0, min(10, file.name.length))} $w x $h $frame0/$bufferLength")
    }*/

    override fun process(process: Process, arguments: List<String>) {
        thread(name = "${file?.name}:error-stream") {
            val out = process.errorStream.bufferedReader()
            val parser = FFMPEGMetaParser()
            try {
                while (true) {
                    val line = out.readLine() ?: break
                    // if('!' in line || "Error" in line) LOGGER.warn("ffmpeg $frame0 ${arguments.joinToString(" ")}: $line")
                    parser.parseLine(line, this)
                }
            } catch (e: ShutdownException) {
                // ...
            }
        }
        thread(name = "${file?.name}:input-stream") {
            val frameCount = arguments[arguments.indexOf("-vframes") + 1].toInt()
            val input = process.inputStream
            try {
                readFrame(input)
                for (i in 1 until frameCount) {
                    readFrame(input)
                }
            } catch (e: OutOfMemoryError) {
                LOGGER.warn("Engine has run out of memory!!")
            } catch (e: ShutdownException) {
                // ...
            }
            input.close()
        }
    }

    val frames = ArrayList<VFrame>(bufferLength)

    var isFinished = false

    // todo what do we do, if we run out of memory?
    private fun readFrame(input: InputStream) {
        waitUntil(true) { w != 0 && h != 0 && codec.isNotEmpty() }
        if (!isDestroyed && !isFinished) {
            try {
                val frame = when (codec) {
                    "I420" -> I420Frame(w, h)
                    "ARGB" -> ARGBFrame(w, h)
                    "BGRA" -> BGRAFrame(w, h)
                    "RGB" -> RGBFrame(w, h)
                    // "Y4" -> YUVAFrame(w, h)
                    else -> throw RuntimeException("Unsupported Codec $codec for $file")
                }
                frame.load(input)
                synchronized(frames) {
                    frameCallback(frame, frames.size)
                    frames.add(frame)
                }
            } catch (e: LastFrame) {
                frameCountByFile[file!!] = frames.size + frame0
                isFinished = true
            } catch (e: Exception) {
                e.printStackTrace()
                frameCountByFile[file!!] = frames.size + frame0
                isFinished = true
            }
        }
        if (isDestroyed) destroy()
    }

    var isDestroyed = false
    override fun destroy() {
        synchronized(frames) {
            if (frames.isNotEmpty()) {
                val f0 = frames[0]
                // delete them over time? it seems like it's really expensive on my Envy x360 xD
                frames.forEach { GFX.addGPUTask(f0.w, f0.h) { it.destroy() } }
            }
            frames.clear()
            isDestroyed = true
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(FFMPEGVideo::class.java)
    }

}