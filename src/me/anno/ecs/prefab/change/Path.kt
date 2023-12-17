package me.anno.ecs.prefab.change

import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.utils.types.Booleans.toInt
import java.text.ParseException
import java.util.concurrent.ThreadLocalRandom

/**
 * internal class to changes,
 * do not use in components/entities, as saving them won't work,
 * or maybe serialize them yourself then
 * */
class Path(
    var parent: Path?,
    var nameId: String, // reliable, and unique in this parent; generated by asset or random, never by user
    var index: Int, // just a guess; used to accelerate access to children
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

    val depth: Int
        get() {
            val p = parent
            return if (p != null) p.depth + 1
            else (this != ROOT_PATH).toInt()
        }

    fun fromRootToThis(includeRoot: Boolean, run: (index: Int, path: Path) -> Unit): Int {
        val parent = parent
        var index = 0
        if (parent != null && parent != ROOT_PATH) {
            index = parent.fromRootToThis(includeRoot, run) + 1
        }
        if (ROOT_PATH !== this || includeRoot) {
            run(index, this)
        }
        return index
    }

    fun lastType() = type
    fun lastIndex() = index
    fun lastNameId() = nameId

    fun isEmpty() = parent == null
    fun isNotEmpty() = parent != null

    val size: Int
        get() = depth

    /**
     * depth 0:
     *    a/b/c x a/b -> null
     *    a/b/c x a/b/c -> empty
     *    a/b/c x d/c/s -> null
     *    a/b/c x a/b/c/1/2 -> 1/2
     * depth 1:
     *    a/b/c x b/c/d -> d
     *    a/b/c x b/c -> empty
     * */
    fun getRestIfStartsWith(other: Path, offset: Int): Path? {
        return if (other.startsWith(this, offset)) {
            other.subList(size - offset)
        } else null
    }

    fun subList(startIndex: Int): Path {
        // go to that index, and then copy the hierarchy
        var ret = ROOT_PATH
        fromRootToThis(false) { index, path ->
            if (index >= startIndex) {
                ret = Path(ret, path.nameId, path.index, path.type)
            }
        }
        return ret
    }

    fun getNameIds() = accumulate { it.nameId }
    fun getTypes() = accumulate { it.type }
    fun getIndices() = accumulate { it.index }

    fun <V> accumulate(func: (Path) -> V): List<V> {
        val list = ArrayList<V>()
        var item = this
        while (item != ROOT_PATH) {
            list.add(func(item))
            item = item.parent ?: break
        }
        list.reverse()
        return list
    }

    fun startsWith(other: Path, offset: Int = 0): Boolean {
        val sizeToTest = other.size - offset
        val stepsToSkip = size - sizeToTest
        if (stepsToSkip < 0) return false
        var self = this
        for (i in 0 until stepsToSkip) {
            self = self.parent ?: ROOT_PATH
        }
        return other.equalsN(self, sizeToTest)
    }

    /**
     * if this starts with target, returns ret such that target + ret == this
     * else returns null
     * */
    fun startsWithGetRest(target: Path?): Path? {
        // magic value ^^
        if (target == null || target == ROOT_PATH) return this
        // reconstruct the rest path
        if (target == this) return ROOT_PATH
        val byParent = parent?.startsWithGetRest(target) ?: return null
        return Path(byParent, nameId, index, type)
    }

    override fun hashCode(): Int {
        // indices must not be part of the hash, as they can change
        return nameId.hashCode() * 31 + type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this === ROOT_PATH && other == null) return true
        return other is Path && equalsN(other, Int.MAX_VALUE)
    }

    fun equalsN(other: Path, depth: Int): Boolean {
        var self = this
        var otherI = other
        for (i in 0 until depth) {
            if (self.nameId != otherI.nameId) {
                return false
            }
            self = self.parent ?: ROOT_PATH
            otherI = otherI.parent ?: ROOT_PATH
            if (self === otherI) return true
        }
        return true
    }

    fun added(nameId: String, index: Int, type: Char): Path {
        return Path(this, nameId, index, type)
    }

    operator fun plus(indexAndType: Triple<String, Int, Char>): Path {
        return Path(this, indexAndType.first, indexAndType.second, indexAndType.third)
    }

    operator fun plus(path: Path): Path {
        if (path == ROOT_PATH) return this
        if (this == ROOT_PATH) return path
        var result = this
        fun add(lePath: Path) {
            val lePathParent = lePath.parent
            if (lePathParent != null && lePathParent != ROOT_PATH) {
                add(lePathParent)
            }
            result = Path(result, lePath.nameId, lePath.index, lePath.type)
        }
        add(path)
        return result
    }

    override fun toString(): String {
        return toString("/")
    }

    fun toString(separator: String): String {
        if (this == ROOT_PATH) return ""
        val parent = parent
        val notNullCode = if (type.code == 0) ' ' else type
        return (if (parent == null || parent == ROOT_PATH) "" else "${parent.toString(separator)}$separator") + "$notNullCode$index,$nameId"
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

    override fun readString(name: String, value: String) {
        when (name) {
            "name" -> this.nameId = value ?: ""
            "v" -> {
                if (value.isEmpty()) {
                    parent = null // we're root now
                } else {
                    val path = fromString(value)
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

    override val className: String get() = "Path"
    override val approxSize get() = 1
    override fun isDefaultValue(): Boolean = this === ROOT_PATH

    companion object {

        val FALSE = Throwable()
        val EXIT = Throwable()

        val ROOT_PATH = Path(null, "", 0, ' ')

        private const val randomIdChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz--"
        fun generateRandomId(): String {
            var value = ThreadLocalRandom.current().nextLong()
            val result = CharArray(11)
            result[0] = '#'
            for (i in 1 until result.size) {
                result[i] = randomIdChars[(value and 63).toInt()]
                value = value ushr 6
            }
            return String(result)
        }

        fun parseInt(str: String, startIndex: Int, endIndex: Int): Int {
            var v = str[startIndex].code - 48
            for (index in startIndex + 1 until endIndex) {
                v = v * 10 + str[index].code - 48
            }
            return v
        }

        fun fromString(str: String?): Path {
            if (str.isNullOrEmpty()) return ROOT_PATH
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
    }
}