package me.anno.utils.types

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX.loadTexturesSync
import me.anno.gpu.drawing.DrawTexts.getTextSizeX
import me.anno.io.files.FileReference
import me.anno.io.json.saveable.JsonWriterBase
import me.anno.maths.Maths.fract
import me.anno.ui.base.Font
import me.anno.ui.base.text.TextPanel
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.structures.arrays.IntArrayList
import me.anno.utils.structures.lists.ExpensiveList
import me.anno.utils.types.Floats.f1
import me.anno.utils.types.Ints.toIntOrDefault
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

@Suppress("unused")
object Strings {

    @JvmStatic
    fun Int.joinChars0(): CharArray {
        val char = this
        return Character.toChars(char)
    }

    @JvmStatic
    fun Int.joinChars(): CharSequence {
        return String(joinChars0())
    }

    @JvmStatic
    fun List<Int>.joinChars(startIndex: Int = 0, endIndex: Int = size): CharSequence {
        val builder = StringBuilder(endIndex - startIndex)
        for (i in startIndex until endIndex) {
            builder.append(get(i).joinChars0())
        }
        return builder
    }

    @JvmStatic
    fun List<Int>.joinChars(startIndex: Int = 0, endIndex: Int = size, filter: (Int) -> Boolean): CharSequence {
        val builder = StringBuilder(endIndex - startIndex)
        for (i in startIndex until endIndex) {
            val char = get(i)
            if (filter(char)) {
                builder.append(char.joinChars0())
            }
        }
        return builder
    }

    @JvmStatic
    fun getLineWidth(line: List<Int>, endIndex: Int, tp: TextPanel) =
        getLineWidth(line, endIndex, tp.font)

    @JvmStatic
    fun getLineWidth(line: List<Int>, endIndex: Int, font: Font): Float {
        return if (endIndex == 0) 0f
        else {
            loadTexturesSync.push(true)
            val stringValue = line.joinChars(0, min(endIndex, line.size))
            val width = getTextSizeX(font, stringValue, -1, -1)
            loadTexturesSync.pop()
            width.toFloat()
        }
    }

    @JvmStatic
    fun getIndexFromText(characters: IntArrayList, localX: Float, tp: TextPanel) =
        getIndexFromText(characters, localX, tp.font)

    @JvmStatic
    fun getIndexFromText(characters: IntArrayList, localX: Float, font: Font): Int {
        val list = ExpensiveList(characters.size + 1) {
            getLineWidth(characters, it, font)
        }
        var index = list.binarySearch { it.compareTo(localX) }
        if (index < 0) index = -1 - index
        // find the closer neighbor
        if (index > 0 && index <= characters.size && abs(list[index - 1] - localX) < abs(list[index] - localX)) {
            index--
        }
        return index
    }

    @JvmStatic
    fun getIndexFromText(characters: List<Int>, localX: Float, tp: TextPanel) =
        getIndexFromText(characters, localX, tp.font)

    @JvmStatic
    fun getIndexFromText(characters: List<Int>, localX: Float, font: Font): Int {
        val list = ExpensiveList(characters.size + 1) {
            getLineWidth(characters, it, font)
        }
        var index = list.binarySearch { it.compareTo(localX) }
        if (index < 0) index = -1 - index
        // find the closer neighbor
        if (index > 0 && index <= characters.size && abs(list[index - 1] - localX) < abs(list[index] - localX)) {
            index--
        }
        return index
    }

    const val defaultImportType = "Text"

    @JvmStatic
    fun String.getImportType(): String =
        DefaultConfig["import.mapping.$this"]?.toString()
            ?: DefaultConfig["import.mapping.${lowercase()}"]?.toString()
            ?: DefaultConfig["import.mapping.*"]?.toString() ?: defaultImportType

    // 00:57:28.87 -> 57 * 60 + 28.87
    @JvmStatic
    fun String.parseTime(): Double {
        val parts = split(":").reversed()
        var seconds = parts[0].toDoubleOrNull() ?: 0.0
        if (parts.size > 1) seconds += 60.0 * (parts[1].toIntOrDefault(0))
        if (parts.size > 2) seconds += 3600.0 * (parts[2].toIntOrDefault(0))
        if (parts.size > 3) seconds += 24.0 * 3600.0 * (parts[3].toIntOrDefault(0))
        return seconds
    }

    // 00:57:28.87 -> 57 * 60 + 28.87
    @JvmStatic
    fun String.parseTimeOrNull(): Double? {

        val parts = split(":")
        val l = parts.size
        if (l > 4) return null

        var seconds = parts[l - 1].toDoubleOrNull() ?: return null
        if (parts.size < 2) return seconds

        val minutes = parts[l - 2].toIntOrDefault(-1)
        if (minutes < 0) return null
        seconds += 60.0 * minutes
        if (parts.size < 3) return seconds

        val hours = parts[l - 3].toIntOrDefault(-1)
        if (hours < 0) return null
        seconds += 3600.0 * hours
        if (parts.size < 4) return seconds

        val days = parts[0].toIntOrDefault(-1)
        if (days < 0) return null
        seconds += 24.0 * 3600.0 * days

        return seconds
    }

    @JvmStatic
    fun Double.formatTime(fractions: Int = 0): String {
        val fractionString = if (fractions > 0) {
            "%.${fractions}f".format(Locale.ENGLISH, fract(this)).substring(1)
        } else ""
        val seconds = toLong()
        if (seconds < 60) return "${seconds}${fractionString}s"
        if (seconds < 3600) return "${seconds / 60}m ${seconds % 60}${fractionString}s"
        return "${seconds / 3600}h ${(seconds / 60) % 60}m ${seconds % 60}${fractionString}s"
    }

    @JvmStatic
    fun Double?.formatTime2(fractions: Int): String {
        if (this == null || this.isNaN()) return "Unknown"
        if (fractions > 0) {
            val fractionString = "%.${fractions}f".format(Locale.ENGLISH, fract(this))
            return formatTime2(0) + fractionString.substring(1)
        }
        val seconds = toLong()
        return "${format2(seconds / 3600)}:${format2((seconds / 60) % 60)}:${format2(seconds % 60)}"
    }

    @JvmStatic
    fun format2(i: Long) = if (i < 10) "0$i" else i.toString()

    @JvmStatic
    fun format2(i: Int) = if (i < 10) "0$i" else i.toString()

    @JvmStatic
    fun String.withLength(length: Int, atTheStart: Boolean = true): String {
        val spaces = length - this.length
        if (spaces <= 0) return this
        val builder = StringBuilder(1 + spaces)
        if (!atTheStart) builder.append(this)
        for (i in 0 until spaces) builder.append(' ')
        if (atTheStart) builder.append(this)
        return builder.toString()
    }

    @JvmStatic
    fun formatDownload(fileName: String, dt: Long, dl: Long, length1: Long, contentLength: Long): String {
        val speed = dl * 1e9 / dt
        val remaining = contentLength - length1
        val remainingTime = remaining / speed
        return "Downloading " +
                "$fileName " +
                "(${length1.formatFileSize()}/${contentLength.formatFileSize()}, ${(length1 * 100f / contentLength).f1()}%) " +
                "with ${speed.toLong().formatFileSize()}/s, " +
                "${remainingTime.formatTime(0)} remaining"
    }

    @JvmStatic
    fun formatDownloadEnd(fileName: String, dst: FileReference) = "Downloaded $fileName ($dst)"

    @JvmStatic
    fun incrementTab(x0: Float, tabSize: Float, relativeTabSize: Float): Float {
        var x = x0
        val r = x / tabSize
        x = (floor(r) + 1f) * tabSize
        if ((1f - fract(r)) * relativeTabSize < 1f) {// smaller than a space? -> add a tab
            x += tabSize
        }
        return x
    }

    @JvmStatic
    fun addPrefix(prefix: String?, suffix: String): String {
        return if (prefix == null) suffix
        else "$prefix$suffix"
    }

    @JvmStatic
    fun addSuffix(prefix: String, suffix: String?): String {
        return if (suffix == null) prefix
        else "$prefix$suffix"
    }

    @JvmStatic
    fun addPrefix(prefix: String?, mid: String, suffix: String): String {
        return if (prefix == null) suffix
        else "$prefix$mid$suffix"
    }

    @JvmStatic
    fun addSuffix(prefix: String, mid: String, suffix: String?): String {
        return if (suffix == null) prefix
        else "$prefix$mid$suffix"
    }

    @JvmStatic
    fun filterAlphaNumeric(str: String): String {
        return str.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
    }

    // made external, so it avoids useless allocations
    @JvmStatic
    private fun append(i: Int, lastI: Int, value: String, data: StringBuilder, str: String): Int {
        if (i > lastI) data.append(value, lastI, i)
        data.append(str)
        return i + 1
    }

    @JvmStatic
    fun writeEscaped(value: String, data: StringBuilder) {
        var i = 0
        var lastI = 0
        while (i < value.length) {
            when (value[i]) {
                '\\' -> lastI = append(i, lastI, value, data, "\\\\")
                '\t' -> lastI = append(i, lastI, value, data, "\\t")
                '\r' -> lastI = append(i, lastI, value, data, "\\r")
                '\n' -> lastI = append(i, lastI, value, data, "\\n")
                '"' -> lastI = append(i, lastI, value, data, "\\\"")
                '\b' -> lastI = append(i, lastI, value, data, "\\b")
                12.toChar() -> lastI = append(i, lastI, value, data, "\\f")
                else -> {
                } // nothing
            }
            i++
        }
        if (i > lastI) data.append(value, lastI, i)
    }

    @JvmStatic
    fun writeEscaped(value: String, data: JsonWriterBase) {
        for (index in value.indices) {
            when (val char = value[index]) {
                '\\' -> {
                    data.append('\\')
                    data.append('\\')
                }
                '\t' -> {
                    data.append('\\')
                    data.append('t')
                }
                '\r' -> {
                    data.append('\\')
                    data.append('r')
                }
                '\n' -> {
                    data.append('\\')
                    data.append('n')
                }
                '"' -> {
                    data.append('\\')
                    data.append('"')
                }
                '\b' -> {
                    data.append('\\')
                    data.append('b')
                }
                12.toChar() -> {
                    data.append('\\')
                    data.append('f')
                }
                else -> data.append(char)
            }
        }
    }

    /**
     * allocation free isBlank()
     * */
    @JvmStatic
    fun CharSequence.isBlank2(): Boolean {
        for (index in 0 until length + 0) {
            when (this[index]) {
                '\u0009', in '\u000a'..'\u000d',
                '\u0020', '\u0085', '\u00a0',
                '\u1680', '\u180e',
                in '\u2000'..'\u200D',
                '\u2028', '\u2029', '\u202f',
                '\u205f', '\u2060', '\u3000',
                '\ufeff' -> Unit
                else -> return false
            }
        }
        return true
    }

    /**
     * allocation free ifBlank()
     * */
    @JvmStatic
    fun <V : CharSequence> V.ifBlank2(other: V): V {
        return if (isBlank2()) other else this
    }

    @JvmStatic
    fun isNumber(s: String): Boolean = s.toDoubleOrNull() != null

    @JvmStatic
    fun isName(s: String): Boolean {
        if (s.isEmpty()) return false
        val s0 = s[0]
        return if (s0 in 'A'..'Z' || s0 in 'a'..'z') {
            for (i in 1 until s.length) {
                when (s[i]) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '_' -> {
                    }
                    else -> return false
                }
            }
            true
        } else false
    }

    @JvmStatic
    fun isArray(s: String): Boolean {
        // todo only names and such are allowed, only commas, and only valid numbers...
        // very complex -> currently just say no
        return false
    }

    @JvmStatic
    fun countLines(str: String): Int {
        var ctr = 1
        for (i in str.indices) {
            if (str[i] == '\n') ctr++
        }
        return ctr
    }

    @JvmStatic
    fun CharSequence.toInt(i0: Int = 0, i1: Int = length) = toLong(i0, i1).toInt()

    @JvmStatic
    fun CharSequence.toLong(i0: Int = 0, i1: Int = length): Long {
        val c0 = this[i0]
        if (c0 == '-') return -toLong(i0 + 1, i1)
        var number = c0.code - 48L
        for (i in i0 + 1 until i1) {
            number = 10 * number + this[i].code - 48
        }
        return number
    }

    @JvmStatic
    fun CharSequence.toFloat(i0: Int = 0, i1: Int = length) = toDouble(i0, i1).toFloat()

    @JvmStatic
    fun CharSequence.toDouble(i0: Int = 0, i1: Int = length): Double {
        // support NaN, +/-Infinity
        if (startsWith("NaN", i0)) return Double.NaN
        if (startsWith("Inf", i0) || startsWith("+Inf", i0)) return Double.POSITIVE_INFINITY
        if (startsWith("-Inf", i0)) return Double.NEGATIVE_INFINITY
        var sign = 1.0
        var number = 0L
        when (val char = this[i0]) {
            '-' -> sign = -1.0
            in '0'..'9' -> number = char.code - 48L
            '.' -> return readAfterDot(i0, i0 + 1, i1, sign, 0.0)
            // else should not happen
        }
        for (i in i0 + 1 until i1) {
            when (val char = this[i]) {
                in '0'..'9' -> number = number * 10 + char.code - 48
                '.' -> return readAfterDot(i0, i + 1, i1, sign, number.toDouble())
                'e', 'E' -> return sign * number * 10.0.pow(toInt(i + 1))
                else -> throw NumberFormatException(subSequence(i0, i1).toString())
            }
        }
        return sign * number.toFloat()
    }

    @JvmStatic
    private fun CharSequence.readAfterDot(ix: Int, i0: Int, i1: Int, sign: Double, number: Double): Double {
        var exponent = 0.1
        var fraction = 0.0
        for (i in i0 until i1) {
            when (val char2 = this[i]) {
                in '0'..'9' -> {
                    fraction += exponent * (char2.code - 48)
                    exponent *= 0.1f
                }
                'e', 'E' -> return sign * (number + fraction) * 10.0.pow(toInt(i + 1))
                else -> throw NumberFormatException(subSequence(ix, i1).toString())
            }
        }
        return sign * (number + fraction)
    }

    @JvmStatic
    fun String.iff(condition: Boolean): String {
        return if (condition) this else ""
    }
}