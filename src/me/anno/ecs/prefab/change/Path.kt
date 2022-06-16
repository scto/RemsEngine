package me.anno.ecs.prefab.change

import me.anno.ecs.prefab.Prefab
import me.anno.engine.ECSRegistry
import me.anno.io.Base64.encodeBase64
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.JsonFormatter
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import org.apache.logging.log4j.LogManager
import java.text.ParseException
import kotlin.random.Random

/**
 * internal class to changes,
 * do not use in components/entities, as saving them won't work,
 * or maybe serialize them yourself then
 * */
class Path(
    var parent: Path?,
    var nameId: String, // reliable, and unique in this parent; generated by asset or random, never by user
    var index: Int, // just a guess
    var type: Char, // where the child is added
) : Saveable() {

    /** don't use this, use ROOT_PATH instead */
    constructor() : this(ROOT_PATH, "", 0, ' ')

    /** legacy constructor */
    constructor(names: Array<String>, indices: IntArray, types: CharArray) : this(
        names.toList(), indices.toList(), types.toList()
    )

    /** constructor for testing */
    constructor(names: List<String>, indices: List<Int>, types: List<Char>) : this(
        if (names.size > 1) Path(
            names.subList(0, names.lastIndex),
            indices.subList(0, names.lastIndex),
            types.subList(0, types.lastIndex)
        ) else ROOT_PATH, names.last(), indices.last(), types.last()
    )

    constructor(p1: Path, p2: Path) : this(
        p1.getNames() + p2.getNames(),
        p1.getIndices() + p2.getIndices(),
        p1.getTypes() + p2.getTypes()
    )

    fun fromRootToThis(includeRoot: Boolean, run: (index: Int, path: Path) -> Unit): Int {
        val parent = parent
        var index = -1
        if (parent != null && parent != ROOT_PATH) {
            index = parent.fromRootToThis(includeRoot, run) + 1
        }
        if (ROOT_PATH !== this || includeRoot) {
            run(index, this)
        }
        return index
    }

    fun appended(other: Path): Path {
        var ret = this
        other.fromRootToThis(false) { _, path ->
            ret = Path(ret, path.nameId, path.index, path.type)
        }
        return ret
    }

    fun lastType() = type
    fun lastIndex() = index
    fun lastNameId() = nameId

    fun firstIndex(): Int {
        val parent = parent
        return if (parent == null || parent == ROOT_PATH) index
        else parent.firstIndex()
    }

    fun isEmpty() = parent == null
    fun isNotEmpty() = parent != null

    val size: Int
        get() {
            // root = size 0
            return (parent?.size ?: -1) + 1
        }

    fun getSubPathIfMatching(other: Path, depth: Int): Path? {
        // depth 0:
        // a/b/c x a/b -> ignore
        // a/b/c x a/b/c -> fine, empty
        // a/b/c x d/c/s -> ignore
        // a/b/c x a/b/c/1/2 -> fine, 1/2
        // depth 1:
        // a/b/c x b/c/d -> fine, d
        // a/b/c x b/c -> fine, empty

        return if (other.startsWithInverseOffset(this, depth)) {
            // fine :)
            other.subList(size - depth)
        } else null
    }

    fun <V : Change> getSubPathIfMatching(change: V, depth: Int): V? {
        val subPath = getSubPathIfMatching(change.path, depth) ?: return null

        @Suppress("unchecked_cast")
        val clone = change.clone() as V
        clone.path = subPath
        return clone
    }

    fun subList(startIndex: Int): Path {
        // go to that index, and then copy the hierarchy
        var ret: Path = ROOT_PATH
        fromRootToThis(false) { index, path ->
            if (index >= startIndex) {
                ret = Path(ret, path.nameId, path.index, path.type)
            }
        }
        return ret
    }

    fun startsWithInverseOffset(path: Path, offset: Int): Boolean {
        return path.startsWithOffset(this, offset)
    }

    fun startsWith0(target: Path?): Boolean {
        // magic value ^^
        if (target == null || target == ROOT_PATH) return true
        return if (target == this) true else parent?.startsWith0(target) ?: false
    }

    /**
     * if this starts with target, returns ret such that target + ret == this
     * else returns null
     * */
    fun startsWith1(target: Path?): Path? {
        // magic value ^^
        if (target == null || target == ROOT_PATH) return this
        // reconstruct the rest path
        if (target == this) return ROOT_PATH
        val byParent = parent?.startsWith1(target) ?: return null
        return Path(byParent, nameId, index, type)
    }

    fun getNames(): List<String> {
        val list = ArrayList<String>()
        fromRootToThis(false) { _, path ->
            list.add(path.nameId)
        }
        return list
    }

    fun getTypes(): List<Char> {
        val list = ArrayList<Char>()
        fromRootToThis(false) { _, path ->
            list.add(path.type)
        }
        return list
    }

    fun getIndices(): List<Int> {
        val list = ArrayList<Int>()
        fromRootToThis(false) { _, path ->
            list.add(path.index)
        }
        return list
    }

    fun startsWithOffset(other: Path, offset: Int): Boolean {
        if (offset == 0) return startsWith0(other)
        else {
            if (size < other.size + offset) return false
            try {
                // this could be done more efficiently with a depth comparison, and then comparing backwards
                // but this function shouldn't be called that often, so we shouldn't need to worry about it
                // O(n) expensive + 2n * allocation
                val ownNames = getNames()
                val ownTypes = getTypes()
                other.fromRootToThis(false) { i, otherI ->
                    val selfI = i + offset
                    if (otherI.nameId != ownNames[selfI] || otherI.type != ownTypes[selfI]) {
                        throw FALSE
                    }
                }
            } catch (e: Throwable) {
                if (e == FALSE) {
                    return false
                } else {
                    throw e
                }
            }
            return true
        }
    }

    override fun hashCode(): Int {
        // indices must not be part of the hash, as they can change
        return nameId.hashCode() * 31 + type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this === ROOT_PATH && other == null) return true
        return other is Path &&
                nameId == other.nameId &&
                (parent ?: ROOT_PATH) == other.parent
    }

    fun added(nameId: String, index: Int, type: Char): Path {
        return Path(this, nameId, index, type)
    }

    operator fun plus(indexAndType: Triple<String, Int, Char>): Path {
        return Path(this, indexAndType.first, indexAndType.second, indexAndType.third)
    }

    operator fun plus(path: Path): Path {
        var newPath = this
        fun add(lePath: Path) {
            val lePathParent = lePath.parent
            if (lePathParent != null && lePathParent != ROOT_PATH) {
                add(lePathParent)
            }
            newPath = Path(newPath, lePath.nameId, lePath.index, lePath.type)
        }
        add(path)
        return newPath
    }

    override fun toString(): String {
        if (this == ROOT_PATH) return ""
        val parent = parent
        val notNullCode = if (type.code == 0) ' ' else type
        return (if (parent == null || parent == ROOT_PATH) "" else "$parent/") + "$notNullCode$index,$nameId"
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(null, "parent", parent)
        writer.writeString("name", nameId)
        writer.writeInt("index", index)
        writer.writeChar("type", type)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "parent" -> parent = value as? Path
            else -> super.readObject(name, value)
        }
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "name" -> this.nameId = value ?: ""
            "v" -> {
                if (value == null || value.isEmpty()) {
                    parent = null // we're root now
                } else {
                    val path = parse(value)
                    parent = path.parent
                    this.nameId = path.nameId
                    index = path.index
                    type = path.type
                }
            }
            else -> super.readString(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "index" -> index = value
            else -> super.readInt(name, value)
        }
    }

    override fun readChar(name: String, value: Char) {
        when (name) {
            "type" -> type = value
            else -> super.readChar(name, value)
        }
    }

    override val className: String = "Path"
    override val approxSize: Int = 1
    override fun isDefaultValue(): Boolean = this === ROOT_PATH

    companion object {

        private val LOGGER = LogManager.getLogger(Path::class)

        val FALSE = Throwable()
        val EXIT = Throwable()

        val ROOT_PATH = Path(null, "", 0, ' ')

        private val random = Random(System.nanoTime())

        fun generateRandomId(): String {
            return synchronized(random) {
                val value = random.nextLong() xor System.nanoTime()
                "#" + encodeBase64(value) // shorten the string a bit
            }
        }

        @JvmStatic
        fun main(array: Array<String>) {

            val p123 = Path(arrayOf("1", "2", "3"), intArrayOf(1, 2, 3), charArrayOf('a', 'b', 'c'))
            if (p123.size != 3) throw RuntimeException()

            val p12 = p123.parent!!
            val p1 = p12.parent!!
            val p0 = p1.parent!!

            if (p0 !== ROOT_PATH) throw RuntimeException()
            if (p0 == p1) throw RuntimeException()
            if (p0 == p12) throw RuntimeException()
            if (p0 == p123) throw RuntimeException()
            if (p1 == p12) throw RuntimeException()
            if (p1 == p123) throw RuntimeException()
            if (p12 == p123) throw RuntimeException()

            val p12123 = join(p12, p123)
            if (p12123.toString() != "a1,1/b2,2/a1,1/b2,2/c3,3") throw RuntimeException()

            if (p0.toString() == p1.toString()) throw RuntimeException()
            if (p0.toString() == p12.toString()) throw RuntimeException()
            if (p0.toString() == p123.toString()) throw RuntimeException()
            if (p1.toString() == p12.toString()) throw RuntimeException()
            if (p1.toString() == p123.toString()) throw RuntimeException()
            if (p12.toString() == p123.toString()) throw RuntimeException()

            val groundTruth = p123.toString()
            val copy = parse(groundTruth)
            val copied = copy.toString()
            val matchSerialized = groundTruth == copied
            val matchInstance = p123 == copy
            LOGGER.info("$matchSerialized, $matchInstance, $groundTruth vs $copied")
            if (!matchInstance || !matchSerialized) throw RuntimeException()

            val abc = Path(arrayOf("a", "b", "c"), intArrayOf(0, 1, 2), charArrayOf('x', 'x', 'x'))
            val bcd = Path(arrayOf("b", "c", "d"), intArrayOf(1, 2, 3), charArrayOf('x', 'x', 'x'))

            LOGGER.info("abc x abc, 0: '${abc.getSubPathIfMatching(abc, 0)}'")
            LOGGER.info("abc x abc, 1: '${abc.getSubPathIfMatching(abc, 1)}'")
            LOGGER.info("abc x bcd, 1: '${abc.getSubPathIfMatching(bcd, 1)}'")

            ECSRegistry.initNoGFX()
            LOGGER.info(p123)
            val cloned = TextReader.read(TextWriter.toText(p123, InvalidRef), InvalidRef, false).first()
            LOGGER.info(cloned)
            LOGGER.info(cloned == p123)

            val prefab = Prefab("Entity")
            val sample = prefab.getSampleInstance()
            if (sample.prefabPath != ROOT_PATH) throw RuntimeException()
            val c1 = prefab.add(ROOT_PATH, 'e', "Entity", "C1")
            /*val c2 = */prefab.add(c1, 'e', "Entity", "C2")
            // val c3 = prefab.add(c2, 'e', "Entity", "C3")

            val adds = prefab.adds

            for (i in adds.indices) {
                val x0 = TextWriter.toText(adds[i], InvalidRef)
                val x1 = TextReader.read(x0, InvalidRef, false)[0] as CAdd
                val x2 = TextWriter.toText(x1, InvalidRef)
                if (x0 != x2) {
                    LOGGER.info(JsonFormatter.format(x0))
                    LOGGER.info(JsonFormatter.format(x2))
                    throw RuntimeException()
                }
            }

            val json = TextWriter.toText(prefab, InvalidRef)
            val prefabClone = TextReader.read(json, InvalidRef, false)[0] as Prefab

            LOGGER.info(prefab.adds)

            LOGGER.info(JsonFormatter.format(json))

            LOGGER.info(prefabClone.adds)
            val json2 = TextWriter.toText(prefabClone, InvalidRef)
            LOGGER.info(JsonFormatter.format(json2))
            if (json != json2) throw RuntimeException()


        }

        fun parseInt(str: String, startIndex: Int, endIndex: Int): Int {
            var v = str[startIndex].code - 48
            for (index in startIndex + 1 until endIndex) {
                v = v * 10 + str[index].code - 48
            }
            return v
        }

        fun parse(str: String?): Path {
            if (str == null || str.isEmpty()) return ROOT_PATH
            var path = ROOT_PATH
            var startIndex = 0
            while (startIndex < str.length) {
                var endIndex = str.indexOf('/', startIndex)
                if (endIndex <= 0) endIndex = str.length
                // format: type,id,name
                val type = str[startIndex]
                val commaIndex = str.indexOf(',', startIndex + 2)
                if (commaIndex < 0) throw ParseException("Invalid path: '$str'", 0)
                val index = parseInt(str, startIndex + 1, commaIndex)
                val name = str.substring(commaIndex + 1, endIndex)
                path = Path(path, name, index, type)
                startIndex = endIndex + 1
            }
            return path
        }

        fun join(a: Path, b: Path): Path {
            return ROOT_PATH
                .appended(a)
                .appended(b)
        }

    }

}