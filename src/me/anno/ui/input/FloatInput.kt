package me.anno.ui.input

import me.anno.animation.Type
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.parser.SimpleExpressionParser
import me.anno.parser.SimpleExpressionParser.toDouble
import me.anno.studio.StudioBase.Companion.shiftSlowdown
import me.anno.ui.input.components.NumberInputComponent
import me.anno.ui.style.Style
import me.anno.utils.types.Strings.isBlank2
import org.joml.*
import kotlin.math.*

// must be open for Rem's Studio
open class FloatInput(
    title: String,
    visibilityKey: String,
    type: Type = Type.FLOAT,
    style: Style,
    private val inputPanel0: NumberInputComponent? = null
) : NumberInput<Double>(style, title, visibilityKey, type, inputPanel0) {

    constructor(style: Style) : this("", "", Type.FLOAT, style)

    final override var lastValue: Double = getValue(type.defaultValue)
    var changeListener: (value: Double) -> Unit = { }

    var allowInfinity = false

    init {
        // to do only override text, if the users presses enter (??)
        setText(lastValue.toString(), false)
        inputPanel.addChangeListener {
            val newValue = parseValue(it)
            if (newValue != null) {
                lastValue = newValue
                changeListener(newValue)
            }
        }
    }

    constructor(title: String, visibilityKey: String, value0: Float, type: Type, style: Style) :
            this(title, visibilityKey, type, style) {
        setValue(value0, false)
    }

    constructor(title: String, value0: Float, type: Type, style: Style) :
            this(title, "", type, style) {
        setValue(value0, false)
    }

    constructor(title: String, visibilityKey: String, value0: Double, type: Type, style: Style) :
            this(title, visibilityKey, type, style) {
        setValue(value0, false)
    }

    fun parseValue(text: String): Double? {
        if (text.isBlank2()) return 0.0
        val trimmed = text.trim()
        val newValue = trimmed.toDoubleOrNull() ?: SimpleExpressionParser.parseDouble(trimmed)
        if (newValue == null || !((allowInfinity && !newValue.isNaN()) || newValue.isFinite())) return null
        return newValue
    }

    fun setValue(v: Int, notify: Boolean) = setValue(v.toDouble(), notify)
    fun setValue(v: Long, notify: Boolean) = setValue(v.toDouble(), notify)
    fun setValue(v: Float, notify: Boolean) = setValue(v.toDouble(), notify)

    // todo prefer the default notation over the scientific one
    // todo especially, if the user input is that way
    // todo match the user input?
    fun stringify(v: Double): String {
        // if it was not in focus, the value may have been from the system, and the user may not prefer it
        // we could also use a setting :)
        /*val userWasScientific = isInFocus && inputPanel.text.run {
            contains("e+", true) || contains("e-", true)
        }*/
        return if (type.defaultValue is Double) v.toString()
        else v.toFloat().toString()
    }

    override fun changeValue(dx: Float, dy: Float) {
        val scale = 20f * shiftSlowdown
        val ws = windowStack
        val size = scale / max(ws.width, ws.height)
        val dx0 = dx * size
        val dy0 = dy * size
        val delta = dx0 - dy0
        // chose between exponential and linear curve, depending on the use-case
        var value = lastValue
        if (type.hasLinear || value == 0.0) value += delta * 0.1 * type.unitScale
        if (type.hasExponential) value *= (if (lastValue < 0) 1.0 / 1.03 else 1.03).pow(delta * if (type.hasLinear) 1.0 else 3.0)
        setValueClamped(value, true)
    }

    fun setValueClamped(value: Double, notify: Boolean) {
        val clampFunc = type.clampFunc
        if (clampFunc == null) {
            setValue(value, notify)
        } else {
            val input: Any = when (type.defaultValue) {
                is Boolean -> value >= 0.5
                is Float -> value.toFloat()
                is Double -> value
                is Int -> value.roundToInt()
                is Long -> value.roundToLong()
                is Vector2f, is Vector3f,
                is Vector4f, is Quaternionf -> value.toFloat()
                else -> throw RuntimeException("Unknown type ${type.defaultValue}")
            }
            val asDouble = when (val clamped = clampFunc(input)) {
                is Boolean -> clamped.toDouble()
                is Float -> clamped.toDouble()
                is Double -> clamped
                is Int -> clamped.toDouble()
                is Long -> clamped.toDouble()
                else -> throw RuntimeException("Unknown type $clamped for ${this::class.simpleName}")
            }
            setValue(asDouble, notify)
        }
    }

    // must be open for Rem's Studio
    open fun getValue(value: Any): Double {
        return when (value) {
            is Boolean -> value.toDouble()
            is Float -> value.toDouble()
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Vector2i -> value.x.toDouble()
            is Vector3i -> value.x.toDouble()
            is Vector4i -> value.x.toDouble()
            is Vector2f -> value.x.toDouble()
            is Vector3f -> value.x.toDouble()
            is Vector4f -> value.x.toDouble()
            is Vector2d -> value.x
            is Vector3d -> value.x
            is Vector4d -> value.x
            else -> throw RuntimeException("Unknown type $value for ${value.javaClass.simpleName}")
        }
    }

    fun setChangeListener(listener: (value: Double) -> Unit): FloatInput {
        changeListener = listener
        return this
    }

    override fun onEmpty(x: Float, y: Float) {
        val newValue = getValue(type.defaultValue)
        if (newValue != lastValue) {
            setValue(newValue, true)
        }
    }

    override fun setValue(value: Double, notify: Boolean): FloatInput {
        if (value != lastValue || !hasValue) {
            hasValue = true
            lastValue = value
            setText(stringify(value), notify)
            if (notify) changeListener(value)
            invalidateLayout()
        }
        return this
    }

    fun updateValueMaybe() {
        if (inputPanel.isInFocus) {
            wasInFocus = true
        } else if (wasInFocus) {
            // apply the value, or reset if invalid
            val value = parseValue(inputPanel.text) ?: lastValue
            setValue(value, true)
            wasInFocus = false
        }
    }

    override fun onEnterKey(x: Float, y: Float) {
        // evaluate the value, and write it back into the text field, e.g. for calculations
        hasValue = false
        setValue(lastValue, true)
    }

    override fun clone(): FloatInput {
        val clone = FloatInput(title, visibilityKey, type, style, inputPanel0?.clone())
        copy(clone)
        return clone
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as FloatInput
        // only works without hard references
        clone.changeListener = changeListener
        clone.allowInfinity = allowInfinity
        clone.tooltip = tooltip
        clone.setValue(lastValue, false)
    }

    override val className get() = "FloatInput"

}