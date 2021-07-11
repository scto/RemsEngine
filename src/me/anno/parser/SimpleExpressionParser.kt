package me.anno.parser

import me.anno.utils.structures.lists.CountingList.Companion.isCounted
import me.anno.parser.Functions.applyFunc0
import me.anno.parser.Functions.applyFunc1
import me.anno.parser.Functions.applyFunc2
import me.anno.parser.Functions.applyFunc3
import me.anno.parser.Functions.applyFunc4
import me.anno.parser.Functions.applyFunc5
import me.anno.parser.Functions.constants
import me.anno.parser.Functions.functions0
import me.anno.parser.Functions.functions1
import me.anno.parser.Functions.functions2
import me.anno.parser.Functions.functions3
import me.anno.parser.Functions.functions4
import me.anno.parser.Functions.functions5
import me.anno.utils.structures.lists.CountingList
import org.apache.logging.log4j.LogManager
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.StrictMath.pow
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.max

/**
 * intended for SMALL calculations
 * doesn't care about helpful errors much or top performance
 * no assignments, because it shall be small;
 * no intention for global variables (because it's for artists; it must be good enough via other means)
 * */
object SimpleExpressionParser {

    // todo make 0.5 + .1 work, .1 is not understood

    // todo give drivers access to global time and parent time?
    // todo give them access to the keyframes
    // todo node editor...
    // todo give them access to all attributes XD

    private val LOGGER = LogManager.getLogger(SimpleExpressionParser::class)

    fun preparse(str: String) = str.splitInternally()

    fun String.splitInternally(): CountingList {

        val list = CountingList(max(2, length / 3))
        var i0 = 0
        var i = -1

        fun putRemaining() {
            if (i > i0) {
                list += substring(i0, i)
                // ("put remaining ${list.last()} at char ${this[i]}")
            }// else LOGGER.warn("put nothing at ${this.getOrNull(i)}")
            i0 = i + 1
        }

        val length = length
        while (++i < length) {

            when (val char = this[i]) {
                in '0'..'9' -> {
                    putRemaining()
                    var j = i
                    searchDigits@ while (++j < length) {
                        when (this[j]) {
                            in '0'..'9', '.' -> {
                            }
                            'e', 'E' -> {
                                j++
                                when (this.getOrNull(j)) {
                                    '+', '-' -> {
                                        j++
                                    }
                                    in '0'..'9' -> {
                                    }
                                    null -> throw RuntimeException("Number without full exponent!")
                                }
                                when (this.getOrNull(j)) {
                                    in '0'..'9' -> {
                                        j++
                                    }
                                    null -> throw RuntimeException("Number without full exponent!")
                                }
                                while (j < length) {
                                    when (this[j]) {
                                        in '0'..'9' -> {
                                            j++
                                        }
                                        else -> break@searchDigits
                                    }
                                }
                            }
                            else -> break@searchDigits
                        }
                    }
                    val number = substring(i, j)
                    list += number.toDoubleOrNull() ?: throw RuntimeException("Invalid number: $number")
                    i = j - 1
                    i0 = i + 1
                }
                '+', '-', '*', '/', '(', '[', '{', '}', ']', ')', '|', '&', ',', ';', '^', '!', '~', '%', '?', ':' -> {
                    putRemaining()
                    list += char
                }
                '<', '>' -> {
                    putRemaining()
                    if (i + 1 < length && this[i + 1] == '=') {
                        list += when (char) {
                            '<' -> "<="
                            '>' -> ">="
                            else -> throw RuntimeException()
                        }
                        i++
                        i0++
                    } else {
                        list += char
                    }
                }
                ' ' -> putRemaining()
                else -> {
                }
            }

        }

        putRemaining()

        return list

    }

    private fun MutableList<Any>.joinSigns(): Boolean {
        for (i in 1 until size) {
            val number = this[i]
            if (!number.isValue()) continue
            if (i == 1 || when (this[i - 2]) {
                    is Double, is Vector -> false
                    '*', '/', '^', ',' -> true
                    else -> false
                }
            ) {
                when (this[i - 1]) {
                    '+' -> {
                        removeAt(i - 1)
                        return true
                    }
                    '-' -> {
                        this[i - 1] = mulAny(-1.0, number)
                        removeAt(i)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun MutableList<Any>.applyFunctions(): Boolean {
        return applyFunc0() or applyFunc1() or applyFunc2() or applyFunc3() or applyFunc4() or applyFunc5()
    }

    private fun MutableList<Any>.applyBrackets(): Boolean {
        for (i in 2 until size) {
            if (this[i - 2] != '(') continue
            if (this[i] != ')') continue
            removeAt(i)
            removeAt(i - 2)
            return true
        }
        return false
    }

    private fun MutableList<Any>.applyPower(): Boolean {
        loop@ for (i in 2 until size) {
            when (this[i - 1]) {
                '^' -> {
                }
                else -> continue@loop
            }
            val a = this[i - 2] as? Double ?: continue
            val b = this[i] as? Double ?: continue
            removeAt(i)
            removeAt(i - 1)
            this[i - 2] = pow(a, b)
            return true
        }
        return false
    }

    private fun MutableList<Any>.applyPercentages(): Boolean {
        loop@ for (i in 2 until size) {
            // +/- 30%
            if (this[i] != '%') continue
            val isAddition = when (this[i - 2]) {
                '+' -> true
                '-' -> false
                else -> continue@loop
            }
            val a = this[i - 1]
            if (!a.isValue()) continue
            removeAt(i)
            // 1.0 +/- 0.01 * a
            val temp = mulAny(a, 0.01)
            this[i - 1] = if (isAddition) addAny(1.0, temp) else subAny(1.0, temp)
            this[i - 2] = '*'
            return true
        }
        return false
    }

    private fun MutableList<Any>.applyMultiplication(): Boolean {
        loop@ for (i in 2 until size) {
            val isMultiplication = when (this[i - 1]) {
                '*', "dot" -> 0
                "x", "cross" -> 1 // mmmh...
                '/', "div" -> 2
                '%', "%" -> 3
                else -> continue@loop
            }
            val a = this[i - 2]
            val b = this[i]
            if (!a.isValue() || !b.isValue()) continue
            removeAt(i)
            removeAt(i - 1)
            this[i - 2] = when (isMultiplication) {
                0 -> mulAny(a, b)
                1 -> crossAny(a, b)
                2 -> divAny(a, b)
                else -> modAny(a, b)
            }
            return true
        }
        return false
    }

    private fun MutableList<Any>.applyAdditions(): Boolean {
        loop@ for (i in 2 until size) {
            val isAddition = when (this[i - 1]) {
                '+' -> true
                '-' -> false
                else -> continue@loop
            }
            val a = this[i - 2] as? Double ?: continue
            val b = this[i] as? Double ?: continue
            removeAt(i)
            removeAt(i - 1)
            this[i - 2] = if (isAddition) a + b else a - b
            return true
        }
        return false
    }

    private fun MutableList<Any>.applyComparisons(): Boolean {
        loop@ for (i in 2 until size) {
            val symbol = this[i - 1]
            when (symbol) {
                '>', '<', ">=", "<=" -> {
                }
                else -> continue@loop
            }
            // check for operators left and right
            if (i >= 3 && this[i - 3].isOperator()) continue
            if (i + 1 < size && this[i + 1].isOperator()) continue
            // check if they are values, doubles
            // todo compare two arrays, lol
            val a = this[i - 2] as? Double ?: continue
            val b = this[i] as? Double ?: continue
            removeAt(i)
            removeAt(i - 1)
            this[i - 2] = when (symbol) {
                '>' -> (a > b).toDouble()
                '<' -> (a < b).toDouble()
                ">=" -> (a >= b).toDouble()
                "<=" -> (a <= b).toDouble()
                else -> throw RuntimeException("Missing impl for $symbol")
            }
            return true
        }
        return false
    }

    fun Boolean.toDouble() = if (this) 1.0 else 0.0

    /**
     * NaN and numbers close to zero are false
     * non-zero and valid, and infinite numbers are true
     * */
    fun Double.toBool() = abs(this) > 1e-16

    private fun Any.getOperatorLevel() = when (this) {
        // like Java
        // todo check for left to right, because 6 / 1 * 2 shall be 12, not 3
        '(', ')', '[', ']' -> 15
        '!', '~' -> 13
        '*', '/', '%' -> 12
        '+', '-' -> 11
        "<<", ">>", ">>>" -> 10
        '<', "<=", '>', "=>" -> 9
        "==", "!=" -> 8
        '&' -> 7
        '^' -> 6
        '|' -> 5
        "&&" -> 4
        "||" -> 3
        '?', ':', "if", "else" -> 2
        else -> 0
    }

    private fun Any.isOperator() = when (this) {
        '(', ')', '[', ']', '?', ':' -> false
        is Char -> true
        else -> false
    }

    private fun MutableList<Any>.applyIfElse(): Boolean {
        val size = size
        loop@ for (i in 4 until size) {
            val a = this[i - 4]
            val b = this[i - 2]
            val c = this[i]
            // check for operators left and right
            if (i >= 5 && this[i - 5].isOperator()) continue
            if (i + 1 < size && this[i + 1].isOperator()) continue
            // check if they are values
            if (a.isValue() && b.isValue() && c.isValue()) {
                val c1 = this[i - 3]
                val c2 = this[i - 1]
                val result = when {
                    // python style inline comparison
                    c1 == "if" && c2 == "else" -> {
                        if (b !is Double) continue@loop
                        if (b.toBool()) a else c
                    }
                    // c style inline comparison
                    c1 == '?' && c2 == ':' -> {
                        if (a !is Double) continue@loop
                        if (a.toBool()) b else c
                    }
                    else -> continue@loop
                }
                for (j in 0 until 4) {
                    removeAt(i - j)
                }
                this[i - 4] = result
                return true
            }
        }
        return false
    }

    private fun MutableList<Any>.replaceConstants(constants: Map<String, Any>?) {
        if (constants == null) return
        for (i in indices) {
            val name = this[i] as? String ?: continue
            if (getOrNull(i + 1) != '(') {
                val replacement = constants[name] ?: constants[name.lowercase(Locale.getDefault())]
                if (replacement != null) {
                    this[i] = replacement
                }
            }
        }
    }

    fun Any?.isValue() = when (this) {
        is Vector -> isClosed
        is Double -> true
        else -> false
    }

    // todo pairs? true vectors? (x,y,z)
    private fun MutableList<Any>.findVectors(): Boolean {
        var wasChanged = false
        // searched: [ to open a vector
        loop@ for (i in 0 until size) {
            if (this[i] == '[') {
                this[i] = Vector()
                wasChanged = true
            }
        }
        // searched: ] to close a vector
        loop@ for (i in 1 until size) {
            val vector = this[i - 1] as? Vector ?: continue
            if (this[i] == ']' && !vector.isClosed) {
                removeAt(i)
                vector.close()
                return true
            }
        }
        // searched: v 5 ,/]
        loop@ for (i in 2 until size) {
            val vector = this[i - 2] as? Vector ?: continue
            when (val symbol = this[i]) {
                ',', ']' -> {
                    val value = this[i - 1]
                    if (value.isValue()) {
                        vector.data.add(value)
                        if (symbol == ',') removeAt(i)
                        removeAt(i - 1)
                        return true
                    }
                }
            }
        }
        // array access
        loop@ for (i in 1 until size) {
            val vector = this[i - 1] as? Vector ?: continue
            val indices = this[i] as? Vector ?: continue
            if (indices.isClosed && indices.data.size < 2) {
                val value = when (val index = indices.data.getOrNull(0) ?: 0.0) {
                    is Double -> vector[index]
                    else -> throw RuntimeException("Index type $index not (yet) supported!")
                } ?: 0.0
                removeAt(i)
                this[i - 1] = value
            }
        }
        return wasChanged
    }

    fun parseDouble(expr: String) = parseDouble(expr, null)
    fun parseDouble(expr: String, additionalConstants: Map<String, Double>?): Double? {
        return try {
            parseDouble(expr.splitInternally(), additionalConstants)
        } catch (e: Exception) {
            LOGGER.warn(e.message ?: "")
            // e.printStackTrace()
            null
        }
    }

    class Operation(val priority: Int, val condition: List<Any>, val action: (list: List<Any>, i0: Int) -> Any) {
        val chars = condition.filter { it.isCounted() }.toSet().toList()
    }

    val isName = { x: Any -> x is String && x.isNotEmpty() }
    val isFunctionName = Array(6) {
        val reference = when (it) {
            1 -> functions1
            2 -> functions2
            3 -> functions3
            4 -> functions4
            5 -> functions5
            else -> functions0
        }
        { name: Any -> name is String && reference.containsKey(name) }
    }
    val isValue = { x: Any -> x.isValue() }
    val isDouble = { x: Any -> x is Double }
    val isVector = { x: Any -> x is Vector }
    val operations = listOf(
        Operation(15, listOf('(', isValue, ')')) { list, i0 -> list[i0 + 1] },
        Operation(15, listOf('[')) { _, _ -> Vector() },
        Operation(15, listOf(isVector, isValue, ',')) { list, i0 ->
            val v = list[i0] as Vector
            v += list[i0 + 1]
            v
        },
        Operation(15, listOf(isVector, ',', ']')) { list, i0 -> list[i0] },
        Operation(15, listOf(isVector, ']')) { list, i0 -> list[i0] },

        Operation(11, listOf(isValue, '+', isValue)) { list, i0 -> addAny(list[i0], list[i0 + 2]) },
        Operation(11, listOf(isValue, '-', isValue)) { list, i0 -> subAny(list[i0], list[i0 + 2]) },

        Operation(
            0,
            listOf(isFunctionName[0], '(', ')')
        ) { list, i0 -> functions0[list[i0] as String]!!() },
        Operation(
            0,
            listOf(isFunctionName[1], '(', isDouble, ')')
        ) { list, i0 -> functions1[list[i0] as String]!!(list[i0 + 2] as Double) },
        Operation(
            0,
            listOf(isFunctionName[2], '(', isDouble, ',', isDouble, ')')
        ) { list, i0 -> functions2[list[i0] as String]!!(list[i0 + 2] as Double, list[i0 + 4] as Double) },
        Operation(
            0,
            listOf(isFunctionName[3], '(', isDouble, ',', isDouble, ',', isDouble, ')')
        ) { list, i0 ->
            functions3[list[i0] as String]!!(
                list[i0 + 2] as Double,
                list[i0 + 4] as Double,
                list[i0 + 6] as Double
            )
        },
        Operation(
            0,
            listOf(isFunctionName[4], '(', isDouble, ',', isDouble, ',', isDouble, ',', isDouble, ')')
        ) { list, i0 ->
            functions4[list[i0] as String]!!(
                list[i0 + 2] as Double,
                list[i0 + 4] as Double,
                list[i0 + 6] as Double,
                list[i0 + 8] as Double
            )
        },
        Operation(
            0,
            listOf(isFunctionName[5], '(', isDouble, ',', isDouble, ',', isDouble, ',', isDouble, ',', isDouble, ')')
        ) { list, i0 ->
            functions5[list[i0] as String]!!(
                list[i0 + 2] as Double,
                list[i0 + 4] as Double,
                list[i0 + 6] as Double,
                list[i0 + 8] as Double,
                list[i0 + 10] as Double
            )
        }

        )

    fun simplify2(parts: CountingList): CountingList {

        LOGGER.info(parts.toString())

        replacing@ while (true) {
            ops@ for (op in operations) {
                val condition = op.condition
                val condSize = condition.size
                if (condSize > parts.size) continue@ops
                for (char in op.chars) {
                    if (char !in parts) continue@ops
                }
                // check if/where applicable
                for (i in condSize - 1 until parts.size) {
                    val i0 = i - condSize + 1
                    for (j in 0 until condSize) {
                        val cond = condition[j]
                        val here = parts[i0 + j]
                        if (cond == here ||
                            (cond is Function1<*, *> && (cond as Function1<Any, Boolean>)(here))
                        ) {
                            // ok
                        } else continue@ops
                    }
                    // todo check if left/right is free
                    // apply
                    val replacement = op.action(parts, i0)
                    for (j in 0 until condSize - 1) {
                        parts.removeAt(i - j)
                    }
                    parts[i0] = replacement
                }
            }
            break
        }

        println(parts)

        return parts
    }

    fun simplify(parts: CountingList?, additionalConstants: Map<String, Any>?): CountingList? {

        if (parts == null) return null

        try {

            parts.replaceConstants(additionalConstants)
            parts.replaceConstants(constants)

            // return simplify2(parts)

            // simplify the expression until empty
            // performance of long strings is improved by CountingList and skipping of steps
            while (parts.size > 1) {
                if ('%' in parts && parts.applyPercentages()) continue
                if (parts.findVectors()) continue
                if ('(' in parts) {
                    if (parts.applyFunctions()) continue
                    if (parts.applyBrackets()) continue
                }
                if ('^' in parts && parts.applyPower()) continue
                if (('*' in parts || '/' in parts || '%' in parts) && parts.applyMultiplication()) continue
                if (('+' in parts || '-' in parts)) {
                    if (parts.applyAdditions()) continue
                    if (parts.joinSigns()) continue
                }
                if (('<' in parts || '>' in parts || ">=" in parts || "<=" in parts) &&
                    parts.applyComparisons()
                ) continue
                if (parts.applyIfElse()) continue
                break
            }

            return parts

        } catch (e: Exception) {
            val msg = e.message
            if (msg != null && msg !in knownMessages) {
                knownMessages.add(msg)
                LOGGER.warn(e.message ?: "")
            }
            return null
        }

    }

    var knownMessages = HashSet<String>()
    fun parseDouble(parts: CountingList?) = parseDouble(parts, null)
    fun parseDouble(parts: CountingList?, additionalConstants: Map<String, Any>?): Double? {

        val parts2 = simplify(parts, additionalConstants) ?: return null
        return when (parts2.size) {
            0 -> null
            1 -> parts2[0] as? Double
            else -> {
                LOGGER.warn("Couldn't understand $parts2")
                null
            }
        }

    }


}