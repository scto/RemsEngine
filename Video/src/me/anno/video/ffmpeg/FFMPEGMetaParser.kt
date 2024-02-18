package me.anno.video.ffmpeg

import me.anno.Time
import me.anno.io.utils.StringMap
import me.anno.utils.types.Strings.shorten
import me.anno.utils.structures.lists.Lists.indexOf2
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager

class FFMPEGMetaParser : StringMap() {

    var lastLineTime = 0L
    val list = ArrayList<String>()

    companion object {
        @JvmStatic
        private val LOGGER = LogManager.getLogger(FFMPEGMetaParser::class)
        var debug = false
        val invalidCodec = "INVALID"
    }

    /**
     * video (mp4):
    Output #0, rawvideo, to 'pipe:':
    Metadata:
    major_brand     : mp42
    minor_version   : 0
    compatible_brands: mp41isom
    title           : HITMAN 2
    artist          : Microsoft Game DVR
    encoder         : Lavf58.29.100
    Stream #0:0(und): Video: rawvideo (I420 / 0x30323449), yuv420p, 1728x1080 [SAR 1:1 DAR 8:5], q=2-31, 537477 kb/s, 24 fps, 24 tbn, 24 tbc (default)
    Metadata:
    creation_time   : 2020-03-19T13:25:46.000000Z
    handler_name    : VideoHandler
    encoder         : Lavc58.54.100 rawvideo

     * image(webp, not argb):
    Output #0, rawvideo, to 'pipe:':
    Metadata:
    encoder         : Lavf58.29.100
    Stream #0:0: Video: rawvideo (I420 / 0x30323449), yuv420p, 530x735, q=2-31, 112190 kb/s, 24 fps, 24 tbn, 24 tbc
    Metadata:
    encoder         : Lavc58.54.100 rawvideo
     *
     * */

    fun getDepth(line: String): Int {
        for (i in line.indices) {
            if (line[i] != ' ') return i / 2
        }
        return line.length / 2
    }

    fun String.specialSplit(list: MutableList<String>): MutableList<String> {
        list.clear()
        var i0 = 0
        var i = 0
        fun put() {
            if (i > i0) {
                list += substring(i0, i)
            }
            i0 = i + 1
        }
        while (i < length) {
            when (this[i]) {
                ',', '(', ')', '[', ']', ':' -> {
                    put()
                    list += this[i].toString()
                }
                ' ' -> {
                    put()
                }
            }
            i++
        }
        put()
        return list
    }

    var level0Type = ""
    var level1Type = ""

    fun parseLine(line: String, stream: FFMPEGStream) {

        lastLineTime = Time.nanoTime
        if (line.isBlank2()) return
        if ("Server returned" in line) {
            stream.codec = invalidCodec
            stream.width = 1
            stream.height = 1
            LOGGER.warn(line)
            return
        }

        // if(debug) LOGGER.debug(line)
        val depth = getDepth(line)
        val data = line.trim().specialSplit(list)
        if (debug) LOGGER.debug("$depth $data")

        fun parseSize() {
            for (i in data.indices) {
                val it = data[i]
                val idx = it.indexOf('x')
                if (idx > 0) {
                    val width = it.substring(0, idx).toIntOrNull() ?: continue
                    val height = it.substring(idx + 1).toIntOrNull() ?: continue
                    if (width > 0 && height > 0) {
                        // we got our info <3
                        stream.width = width
                        stream.height = height
                        if (debug) LOGGER.debug("Found size $width x $height")
                        return
                    }
                }
            }
        }

        fun parseOutput() {
            val videoTypeIndex = data.indexOf("rawvideo")
            if (debug) LOGGER.debug("Parsing output: ${data.joinToString { it.shorten(200) }}")
            if (videoTypeIndex > -1 && videoTypeIndex + 2 < data.size && data[videoTypeIndex + 1] == "(") {
                var codec = data[videoTypeIndex + 2]
                if (data[videoTypeIndex + 3] == "[") {
                    val eidx = data.indexOf2("]", videoTypeIndex + 3, true)
                    if (eidx > 0) {
                        codec += data.subList(videoTypeIndex + 3, eidx + 1).joinToString("")
                    }
                }
                if (debug) LOGGER.debug("Found codec $codec")
                stream.codec = codec
            }
            parseSize()
        }

        fun analyzeIO() {
            if (level0Type == "Output" && data[0] == "Stream") {
                parseOutput()
            }
            if (level0Type == "Input" && data[0] == "Stream") {
                try {
                    val fpsIndex = data.indexOf("fps") - 1
                    if (fpsIndex > -1) {
                        stream.srcFPS = data[fpsIndex].toDouble()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        when (depth) {
            0 -> {
                level0Type = data[0]
            }
            1 -> {
                // to do parse dar for correct ratio? ... can be corrected manually...
                level1Type = data[0]
                when (level1Type) {
                    "Duration" -> {
                        if (level0Type == "Input") {
                            // [Duration, :, 00, :, 00, :, 31.95, ,, start, :, 0.000000, ,, bitrate, :, 10296, kb/s]
                            val durParts = data.subList(2, data.indexOf(","))
                            try {
                                if (!durParts.withIndex()
                                        .all { (index, value) -> ((index % 2) == 0) || value == ":" }
                                ) {
                                    throw RuntimeException("Invalid ffmpeg-duration? $data")
                                }
                                val duration = when (durParts.size) {
                                    1 -> durParts[0].toDoubleOrNull() ?: 0.01
                                    3 -> durParts[0].toDouble() * 60 + durParts[2].toDouble()
                                    5 -> durParts[0].toDouble() * 3600 + durParts[2].toDouble() * 60 + durParts[4].toDouble()
                                    7 -> durParts[0].toDouble() * 3600 * 24 + durParts[2].toDouble() * 3600 + durParts[4].toDouble() * 60 + durParts[6].toDouble()
                                    else -> throw RuntimeException("Invalid ffmpeg-duration? $data")
                                }
                                stream.srcDuration = duration
                                // ("duration: $duration")
                            } catch (e: Exception) {
                                LOGGER.warn(e)
                            }
                        }
                    }
                }
                analyzeIO()
            }
            else -> analyzeIO()
        }
    }

}