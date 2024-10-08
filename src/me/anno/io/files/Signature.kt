package me.anno.io.files

import me.anno.ecs.prefab.PrefabReadable
import me.anno.graph.hdb.ByteSlice
import me.anno.io.Streams.readNBytes2
import me.anno.io.files.inner.SignatureFile
import me.anno.utils.Color.hex8
import me.anno.utils.async.Callback
import me.anno.utils.structures.lists.Lists.first2
import me.anno.utils.structures.lists.Lists.firstOrNull2
import me.anno.utils.types.size
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Most file formats specify a magic value in the first bytes.
 * This allows us to detect what kind of content a file contains.
 *
 * This class stores the data, and is a registry for known file signatures.
 * */
class Signature(
    val name: String,
    private val offset: Int,
    private val pattern: ByteArray
) {

    constructor(name: String, offset: Int, signature: String) : this(name, offset, signature.encodeToByteArray())

    constructor(name: String, offset: Int, signature: String, vararg extraBytes: Int) : this(
        name, offset,
        signature.encodeToByteArray() + ByteArray(extraBytes.size) { extraBytes[it].toByte() }
    )

    constructor(name: String, offset: Int, prefix: ByteArray, signature: String, extraBytes: ByteArray) : this(
        name, offset,
        prefix + signature.encodeToByteArray() + extraBytes
    )

    constructor(name: String, offset: Int, prefix: ByteArray, signature: String) : this(
        name, offset,
        prefix + signature.encodeToByteArray()
    )

    constructor(name: String, offset: Int, vararg bytes: Int) : this(
        name, offset,
        ByteArray(bytes.size) { bytes[it].toByte() }
    )

    val order = if (offset < 0) {
        // bad format: no identifier, so test it last
        1024 - pattern.size
    } else {
        // test long ones first, because they are more specific
        -pattern.size
    }

    fun matches(bytes: ByteBuffer): Boolean {
        val position = bytes.position()
        val size = min(bytes.remaining(), maxSampleSize)
        if (offset >= size) return false
        if (offset < 0) {
            // search the signature instead of requiring it
            search@ for (offset in 0 until size - pattern.size) {
                for (i in 0 until min(size - offset, pattern.size)) {
                    if (bytes[position + i + offset] != pattern[i]) {
                        continue@search
                    }
                }
                return true
            }
            return false
        } else {
            for (i in 0 until min(size - offset, pattern.size)) {
                if (bytes[position + i + offset] != pattern[i]) return false
            }
            return true
        }
    }

    fun matches(bytes: ByteArray, range: IntRange): Boolean {
        val bytesSize = range.size
        val size = min(bytesSize, maxSampleSize)
        if (offset >= bytesSize) return false
        if (offset < 0) {
            // search the signature instead of requiring it
            search@ for (offset in 0 until size - pattern.size) {
                val readOffset = offset + range.first
                for (i in 0 until min(size - offset, pattern.size)) {
                    if (bytes[i + readOffset] != pattern[i]) {
                        continue@search
                    }
                }
                return true
            }
            return false
        } else {
            val readOffset = offset + range.first
            for (i in 0 until min(size - offset, pattern.size)) {
                if (bytes[i + readOffset] != pattern[i]) return false
            }
            return true
        }
    }

    override fun toString() = "Signature { \"$name\" by [${pattern.joinToString { hex8(it.toInt()) }}] + $offset }"

    companion object {

        const val sampleSize = 128
        const val maxSampleSize = 4096

        fun findName(bytes: ByteArray) = find(bytes)?.name
        fun find(bytes: ByteArray): Signature? {
            return find(bytes, bytes.indices)
        }

        fun findName(bytes: ByteSlice) = find(bytes)?.name
        fun find(bytes: ByteSlice): Signature? {
            return find(bytes.bytes, bytes.range)
        }

        fun findName(bytes: ByteArray, range: IntRange) = find(bytes, range)?.name
        fun find(bytes: ByteArray, range: IntRange): Signature? {
            val nonHashed = signatures
            for (i in nonHashed.indices) {
                val s = nonHashed[i]
                if (s.matches(bytes, range)) {
                    return s
                }
            }
            return null
        }

        fun register(signature: Signature) {
            // alternatively could find the correct insert index
            // still would be O(n)
            var index = signatures.binarySearch {
                signature.order.compareTo(it.order)
            }
            if (index < 0) index = -1 - index
            signatures.add(index, signature)
        }

        @Suppress("unused")
        fun unregister(signature: Signature) {
            // could use binary search to find signature
            // still would be O(n)
            signatures.remove(signature)
        }

        fun findName(file: FileReference, callback: Callback<String?>) {
            find(file) { sig, err -> callback.call(sig?.name, err) }
        }

        fun find(file: FileReference, callback: Callback<Signature?>) {
            if (file is SignatureFile) return callback.ok(file.signature)
            if (!file.exists) return callback.ok(null)
            return when (file) {
                is PrefabReadable -> callback.ok(signatures.firstOrNull2 { it.name == "json" })
                else -> {
                    // reads the bytes, or 255 if at end of file
                    // how much do we read? 🤔
                    // some formats are easy, others require more effort
                    // maybe we could read them piece by piece...
                    file.inputStream(sampleSize.toLong()) { input, err ->
                        if (input != null) callback.ok(find(input.readNBytes2(sampleSize, false)))
                        else callback.err(null)
                    }
                }
            }
        }

        fun findNameSync(file: FileReference) = findSync(file)?.name
        fun findSync(file: FileReference): Signature? {
            if (file is SignatureFile) return file.signature
            if (!file.exists) return null
            return when (file) {
                is PrefabReadable -> signatures.first2 { it.name == "json" }
                else -> {
                    // reads the bytes, or 255 if at end of file
                    // how much do we read? 🤔
                    // some formats are easy, others require more effort
                    // maybe we could read them piece by piece...
                    file.inputStreamSync().use { input: InputStream ->
                        find(input.readNBytes2(sampleSize, false))
                    }
                }
            }
        }

        val bmp = Signature("bmp", 0, "BM")

        // source: https://en.wikipedia.org/wiki/List_of_file_signatures
        // https://www.garykessler.net/library/file_sigs.html
        @Suppress("SpellCheckingInspection")
        private val signatures = arrayListOf(
            Signature("bz2", 0, "BZh"),
            Signature("rar", 0, "Rar!", 0x1a, 0x07),
            Signature("zip", 0, "PK", 3, 4),
            Signature("zip", 0, "PK", 5, 6), // "empty archive" after wikipedia
            Signature("zip", 0, "PK", 7, 8), // "spanned archive"
            Signature("tar", 0, 0x1F, 0x9D), // lempel-ziv-welch
            Signature("tar", 0, 0x1F, 0xA0),// lzh
            // Signature("tar", 257, "ustar"), // this large offset is unfortunate; we'd have to adjust the signature readout for ALL others
            Signature("gzip", 0, 0x1F, 0x8B), // gz/tar.gz
            Signature("xz", 0, byteArrayOf(0xFD.toByte()), "7zXZ", byteArrayOf(0)), // xz compression
            Signature("lz4", 0, 0x04, 0x22, 0x4D, 0x18), // another compression
            Signature("7z", 0, "7z", 0xBC, 0xAF, 0x27, 0x1C),
            Signature("xar", 0, "xar!"), // file compression for apple stuff?
            Signature("oar", 0, "OAR"), // oar compression (mmh)
            Signature("java", 0, 0xCA, 0xFE, 0xBA, 0xBE), // java class
            Signature("text", 0, 0xEF, 0xBB, 0xBF), // UTF8
            Signature("text", 0, 0xFF, 0xFE), // UTF16
            Signature("text", 0, 0xFE, 0xFF),
            Signature("text", 0, 0xFF, 0xFE, 0, 0), // UTF32
            Signature("text", 0, 0xFE, 0xFF, 0, 0),
            Signature("text", 0, "+/v8"), // UTF7
            Signature("text", 0, "+/v9"), // UTF7
            Signature("text", 0, "+/v+"), // UTF7
            Signature("text", 0, "+/v/"), // UTF7
            Signature("text", 0, 0x0E, 0xFE, 0xFF), // SOSU compressed text
            Signature("pdf", 0, "%PDF"),
            Signature("wasm", 0, byteArrayOf(0), "asm"),
            Signature("ttf", 0, 0, 1, 0, 0, 0),// true type font
            Signature("woff1", 0, "wOFF"),
            Signature("woff2", 0, "wOF2"),
            Signature("lua-bytecode", 0, byteArrayOf(0x1B), "Lua"),
            Signature("shell", 0, "#!"),
            // images
            Signature("png", 0, byteArrayOf(0x89.toByte()), "PNG", byteArrayOf(0xd, 0xa, 0x1a, 0x0a)),
            Signature("jpg", 0, 0xFF, 0xD8, 0xFF, 0xDB),
            Signature("jpg", 0, 0xFF, 0xD8, 0xFF, 0xE0),
            Signature("jpg", 0, 0xFF, 0xD8, 0xFF, 0xEE),
            Signature("jpg", 0, 0xFF, 0xD8, 0xFF, 0xE1),
            bmp,
            Signature("psd", 0, "8BPS"), // photoshop image format
            Signature("hdr", 0, "#?RADIANCE"), // high dynamic range
            Signature("ico", 0, 0, 0, 1, 0, 1),// ico with 1 "image"
            Signature("ico", 0, 0, 0, 2, 0, 1),// cursor with 1 "image"
            Signature("dds", 0, "DDS "), // direct x image file format
            Signature("gif", 0, "GIF8"), // graphics interchange format, often animated
            Signature("gimp", 0, "gimp xcf "), // gimp image file
            Signature("qoi", 0, "qoif"),
            Signature("exr", 0, 0x76, 0x2f, 0x31, 0x01), // HDR image format, can be exported from Blender
            Signature("webp", 8, "WEBP"), // after RIFF header
            // tga has header at the end of the file, and only sometimes...
            // other
            Signature("xml", 0, "<?xml"), // plus other variations with UTF16, UTF32, ...
            // are we using 1.0??
            Signature("xml-re", 0, "<?xml version=\"1.0\" encoding=\"utf-8\"?><RemsEngine"),
            Signature("xml-re", 0, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<RemsEngine"),
            Signature("svg", 0, "<svg"),
            Signature("exe", 0, "MZ"),
            Signature("rem", 0, "RemsEngineZZ"), // deflate-compressed binary format for Rem's Engine
            // media (video/audio)
            Signature("media", 0, 0x1A, 0x45, 0xDF, 0xA3), // mkv, mka, mks, mk3d, webm
            Signature("media", 0, "ID3"),// mp3 container
            Signature("media", 0, 0xFF, 0xFB),// mp3
            Signature("media", 0, 0xFF, 0xF3),// mp3
            Signature("media", 0, 0xFF, 0xF2),// mp3
            Signature("media", 0, "OggS"),// ogg, opus
            Signature("media", 0, "RIFF"),// can be a lot of stuff, e.g., wav, avi
            Signature("media", 0, "FLV"),// flv
            Signature("media", 0, 0x47),// mpeg stream
            Signature("media", 0, 0x00, 0x00, 0x01, 0xBA), // m2p, vob, mpg, mpeg
            Signature("media", 0, 0x00, 0x00, 0x01, 0xB3),// mpg, mpeg
            Signature("media", 4, "ftypisom"), // mp4
            Signature("media", 4, "ftypmp42"), // mp4
            Signature("media", 4, "ftypdash"), // m4a
            Signature("media", 4, "ftyp"), // probably media... (I am unsure)
            Signature("media", 0, 0x30, 0x26, 0xb2, 0x75, 0x8e, 0x66, 0xcf, 0x11), // wmv, wma, asf (Windows Media file)
            // meshes
            Signature("vox", 0, "VOX "),
            Signature("fbx", 0, "Kaydara FBX Binary"),
            Signature("fbx", 0, "; FBX "), // text fbx, is followed by a version
            Signature("obj", -1, "\nmtllib "),
            Signature("obj", -1, "OBJ File"),
            Signature("obj", 0, "o "), // ^^, stripped, very compact obj
            Signature("mtl", -1, "newmtl "),
            Signature("mtl", 0, "# Blender MTL"), // ^^
            Signature("blend", 0, "BLENDER"),
            Signature("gltf", 0, "glTF"),
            Signature("gltf", -1, "\"scenes\""),
            Signature("mesh-draco", 0, "DRACO"),
            Signature("md2", 0, "IDP2"),
            Signature("md5mesh", 0, "MD5Version"),
            Signature("dae", -1, "<COLLADA"),
            Signature("maya", 0, "//Maya ASCII "),
            Signature("ply", 0, "ply"),
            // scenes and meshes from mitsuba renderer
            Signature("mitsuba-meshes", 0, byteArrayOf(0x1c, 0x04)),
            Signature("mitsuba-scene", 0, "<scene version="),
            Signature("mitsuba-scene", -1, "<scene version="),
            // unity support
            Signature("yaml", 0, "%YAML"),
            Signature("yaml-re", 0, "RemsEngine:\n - class"),
            // json, kind of
            Signature("json", 0, "["),
            Signature("json", 0, "[{"),
            Signature("json", 0, "[{\"class\":"),
            Signature("json", 0, "{"),
            Signature("sims", 0, "DBPF"),
            // windows link file
            Signature("lnk", 0, byteArrayOf(0x4c, 0, 0, 0)),
            // window url file
            Signature("url", 0, "[InternetShortcut]"),
        ).apply {
            // first long ones, then short ones; to be more specific first
            sortBy { it.order }
        }
    }
}