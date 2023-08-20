package me.anno.maths

import me.anno.maths.Maths.TAUf
import me.anno.maths.Maths.pow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

object EquationSolver {

    private const val TOO_LARGE_RATIO = 1e9 // idk...

    fun solveQuadratic(dst: FloatArray, a: Float, b: Float, c: Float): Int {
        // a = 0 -> linear equation
        if (a == 0f || abs(b) + abs(c) > TOO_LARGE_RATIO * abs(a)) {
            // a, b = 0 -> no solution
            if (b == 0f || abs(c) > TOO_LARGE_RATIO * abs(b)) {
                return if (c == 0f) -1 else 0 // 0 = 0
            }
            dst[0] = -c / b
            return 1
        }
        var dscr = b * b - 4 * a * c
        return when {
            dscr > 0f -> {
                dscr = sqrt(dscr)
                dst[0] = (-b + dscr) / (2 * a)
                dst[1] = (-b - dscr) / (2 * a)
                2
            }
            dscr == 0f -> {
                dst[0] = -b / (2 * a)
                1
            }
            else -> 0
        }
    }

    fun solveCubicNormed(dst: FloatArray, a0: Float, b: Float, c: Float): Int {
        var a = a0
        val a2 = a * a
        var q = (a2 - 3 * b) / 9
        val r = (a * (2 * a2 - 9 * b) + 27 * c) / 54
        val r2 = r * r
        val q3 = q * q * q
        return if (r2 < q3) {
            var t = r / sqrt(q3)
            if (t < -1) t = -1f
            if (t > 1) t = 1f
            t = acos(t)
            a /= 3f
            q = -2 * sqrt(q)
            dst[0] = q * cos(t / 3f) - a
            dst[1] = q * cos((t + TAUf) / 3f) - a
            dst[2] = q * cos((t - TAUf) / 3f) - a
            3
        } else {
            var a3 = -pow(abs(r) + sqrt(r2 - q3), 1f / 3f)
            if (r < 0) a3 = -a3
            val b3 = if (a3 == 0f) 0f else q / a3
            a /= 3f
            dst[0] = a3 + b3 - a
            dst[1] = -0.5f * (a3 + b3) - a
            dst[2] = +0.5f * sqrt(3f) * (a3 - b3)
            if (abs(dst[2]) < 1e-14) 2 else 1
        }
    }

    fun solveCubic(dst: FloatArray, a: Float, b: Float, c: Float, d: Float): Int {
        if (a != 0f) {
            val bn = b / a
            val cn = c / a
            val dn: Float = d / a
            // Check, that <a> isn't "almost zero"
            if (abs(bn) < TOO_LARGE_RATIO && abs(cn) < TOO_LARGE_RATIO && abs(dn) < TOO_LARGE_RATIO)
                return solveCubicNormed(dst, bn, cn, dn)
        }
        return solveQuadratic(dst, b, c, d)
    }

}