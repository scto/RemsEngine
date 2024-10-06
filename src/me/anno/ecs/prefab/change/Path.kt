package me.anno.ecs.prefab.change

import me.anno.io.base.BaseWriter
import me.anno.io.saveable.Saveable
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.NumberFormatter.reverse
import java.util.concurrent.ThreadLocalRandom

/**
 * marks where a change (add or set) shall be applied;
 * like a file
 * */
class Path(
    var parent: Path?,
    var nameId: String, // reliable, and unique in this parent; generated by asset or random, never by user
    var index: Int, // just a guess; used to accelerate access to children
    var type: Char, // where the child is added
) : Saveable() {

    /** don't use this, use ROOT_PATH instead */
    constructor() : this(ROOT_PATH, "", 0, ' ')

    val depth: Int get() = calculateDepth()

    private fun calculateDepth(): Int {
        var depth = 0
        var node = this
        while (true) {
            node = node.parent ?: return depth
            depth++
        }
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
        fromRootToThis(false) { index, node ->
            if (index >= startIndex) {
                ret = Path(ret, node.nameId, node.index, node.type)
            }
        }
        return ret
    }

    fun getHead(): Path {
        return appendHeadOnto(ROOT_PATH)
    }

    fun appendHeadOnto(parent: Path): Path {
        return Path(parent, nameId, index, type)
    }

    fun getNameIds() = accumulate { it.nameId }
    fun getTypes() = accumulate { it.type }
    fun getIndices() = accumulate { it.index }

    fun <V> accumulate(func: (Path) -> V): List<V> {
        val list = ArrayList<V>(depth)
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
        return toString(this, separator)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(null, "parent", parent)
        writer.writeString("name", nameId)
        writer.writeInt("index", index)
        writer.writeChar("type", type)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "parent" -> parent = value as? Path
            "name" -> nameId = value as? String ?: return
            "v" -> { // legacy
                if (value !is String) return
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
            "index" -> index = value as? Int ?: return
            "type" -> type = value as? Char ?: return
            else -> super.setProperty(name, value)
        }
    }

    override val approxSize get() = 1
    override fun isDefaultValue(): Boolean = this === ROOT_PATH

    override fun onReadingEnded() {
        println("read path $this")
    }

    companion object {

        val EXIT = Throwable()
        val ROOT_PATH = Path(null, "", 0, ' ')

        private fun toString(node0: Path, separator: String): String {
            val builder = StringBuilder()
            var node = node0
            while (true) {
                val type = node.type
                val notNullCode = if (type.code == 0) ' ' else type
                val i0 = builder.length
                builder.append(notNullCode).append(node.index).append(',').append(node.nameId)
                builder.reverse(i0, builder.length)
                node = node.parent ?: break
                if (node == ROOT_PATH) break
                builder.append(separator)
            }
            builder.reverse()
            return builder.toString()
        }

        private const val randomIdChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz--"
        fun generateRandomId(): String {
            var value = ThreadLocalRandom.current().nextLong()
            val result = CharArray(11)
            result[0] = '#'
            for (i in 1 until result.size) {
                result[i] = randomIdChars[(value and 63).toInt()]
                value = value ushr 6
            }
            return result.concatToString()
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
                assertTrue(commaIndex >= 0) { "Invalid path: '$str'" }
                val index = parseInt(str, startIndex + 1, commaIndex)
                val name = str.substring(commaIndex + 1, endIndex)
                path = Path(path, name, index, type)
                startIndex = endIndex + 1
            }
            return path
        }
    }
}