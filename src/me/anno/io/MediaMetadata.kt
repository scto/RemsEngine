package me.anno.io

import me.anno.audio.AudioReadable
import me.anno.cache.CacheSection
import me.anno.cache.ICacheData
import me.anno.image.ImageReadable
import me.anno.io.files.FileReference
import me.anno.io.files.Reference.getReference
import me.anno.io.files.SignatureCache
import me.anno.utils.Sleep
import me.anno.utils.Warning
import me.anno.utils.async.Callback
import me.anno.utils.structures.tuples.IntPair
import me.anno.utils.types.Strings.formatTime
import me.anno.utils.types.Strings.shorten
import org.apache.logging.log4j.LogManager
import java.io.InputStream

/**
 * Metadata for audio/video/images.
 * Typically generated by FFMPEG, but some special cases are handled by our own readers.
 * */
class MediaMetadata(val file: FileReference, signature: String?) : ICacheData {

    var duration = 0.0

    var hasAudio = false // has sound/music/audio
    var hasVideo = false // has image or video data

    var audioStartTime = 0.0
    var audioSampleRate = 0
    var audioDuration = 0.0
    var audioSampleCount = 0L // 24h * 3600s/h * 48k = 4B -> Long
    var audioChannels = 0

    var videoStartTime = 0.0
    var videoFPS = 0.0
    var videoDuration = 0.0
    var videoWidth = 0
    var videoHeight = 0
    var videoFrameCount = 0

    var ready = true

    override fun toString(): String {
        return "FFMPEGMetadata(file: ${file.absolutePath.shorten(200)}, audio: ${
            if (hasAudio) "[$audioSampleRate Hz, $audioChannels ch]" else "false"
        }, video: ${
            if (hasVideo) "[$videoWidth x $videoHeight, $videoFrameCount]" else "false"
        }, duration: ${duration.formatTime(3)}), channels: $audioChannels"
    }

    init {
        load(signature)
    }

    fun load(signature: String?) {
        // type handlers
        for (thi in typeHandlers.indices) {
            val th = typeHandlers[thi]
            if (th.reader(file, this)) {
                return
            }
        }
        // signature handlers
        val signature1 = signature ?: SignatureCache[file, false]?.name
        for (shi in signatureHandlers.indices) {
            val sh = signatureHandlers[shi]
            if (sh.reader(file, signature1, this)) {
                return
            }
        }
        LOGGER.debug(
            "{}'s signature wasn't registered in FFMPEGMetadata.kt: '{}'",
            file.absolutePath, signature1
        )
    }

    fun setImage(wh: IntPair) {
        setImage(wh.first, wh.second)
    }

    fun setImage(w: Int, h: Int) {
        hasVideo = true
        videoWidth = w
        videoHeight = h
        videoFrameCount = 1
        duration = Double.POSITIVE_INFINITY // actual value isn't really well-defined
    }

    fun setImageByStream(callback: (InputStream) -> Any): Boolean {
        ready = false
        file.inputStream { it, exc ->
            if (it != null) {
                val size = callback(it)
                if (size is IntPair) {
                    setImage(size)
                } else if (size is Exception) {
                    size.printStackTrace()
                }
            } else exc?.printStackTrace()
            ready = true
        }
        return true
    }

    companion object {

        data class Handler<V>(val priority: Int, val signature: String, val reader: V)

        private val typeHandlers = ArrayList<Handler<(FileReference, MediaMetadata) -> Boolean>>()
        private val signatureHandlers = ArrayList<Handler<(FileReference, String?, MediaMetadata) -> Boolean>>()

        private fun <V> registerHandler(list: ArrayList<Handler<V>>, priority: Int, key: String, value: V) {
            synchronized(signatureHandlers) {
                var idx = list.binarySearch { it.priority.compareTo(priority) }
                if (idx < 0) idx = -idx - 1
                list.add(idx, Handler(priority, key, value))
            }
        }

        fun registerHandler(order: Int, key: String, handler: (file: FileReference, dst: MediaMetadata) -> Boolean) {
            registerHandler(typeHandlers, order, key, handler)
        }

        fun registerSignatureHandler(
            order: Int, key: String,
            handler: (file: FileReference, signature: String?, dst: MediaMetadata) -> Boolean
        ) {
            registerHandler(signatureHandlers, order, key, handler)
        }

        fun unregister(keys: String) {
            val keys1 = keys.split(',')
            synchronized(signatureHandlers) {
                typeHandlers.removeAll { it.signature in keys1 }
                signatureHandlers.removeAll { it.signature in keys1 }
            }
        }

        @JvmStatic
        private val LOGGER = LogManager.getLogger(MediaMetadata::class)

        @JvmStatic
        private val metadataCache = CacheSection("Metadata")

        @JvmStatic
        private fun createMetadata(file: FileReference, i: Long): MediaMetadata {
            Warning.unused(i)
            return MediaMetadata(file, null)
        }

        @JvmStatic
        private fun createMetadata(file: FileReference, signature: String?): MediaMetadata {
            return MediaMetadata(file, signature ?: "")
        }

        @JvmStatic
        fun getMeta(path: String, async: Boolean): MediaMetadata? {
            return getMeta(getReference(path), async)
        }

        @JvmStatic
        fun getMeta(file: FileReference, async: Boolean): MediaMetadata? {
            val meta = metadataCache.getFileEntry(
                file, false, 300_000,
                async, Companion::createMetadata
            ) ?: return null
            if (!async) Sleep.waitUntil(true) { meta.ready }
            return meta
        }

        @JvmStatic
        fun getMetaAsync(file: FileReference, callback: Callback<MediaMetadata>) {
            metadataCache.getFileEntryAsync(
                file, false, 300_000,
                true, Companion::createMetadata,
                callback
            )
        }

        @JvmStatic
        fun getMeta(file: FileReference, signature: String?, async: Boolean): MediaMetadata? {
            val meta = metadataCache.getFileEntry(file, false, 300_000, async) { f, _ ->
                createMetadata(f, signature)
            } ?: return null
            if (!async) Sleep.waitUntil(true) { meta.ready }
            return meta
        }

        init {
            registerHandler(100, "audio-readable") { file, dst ->
                if (file is AudioReadable) {
                    dst.hasAudio = true
                    dst.audioChannels = file.channels
                    dst.audioSampleRate = file.sampleRate
                    dst.audioSampleCount = file.sampleCount
                    dst.audioDuration = file.duration
                    dst.duration = dst.audioDuration
                    true
                } else false
            }
            registerHandler(150, "image-readable") { file, dst ->
                if (file is ImageReadable) {
                    dst.setImage(file.readSize())
                    true
                } else false
            }
        }
    }
}