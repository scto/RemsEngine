package me.anno.graph.types.flow.maths

enum class IntMathType(
    val id: Int,
    val int: (a: Int, b: Int) -> Int,
    val long: (a: Long, b: Long) -> Long
) {

    // unary
    ABS(0, { a, _ -> kotlin.math.abs(a) }, { a, _ -> kotlin.math.abs(a) }),
    NEG(1, { a, _ -> -a }, { a, _ -> -a }),
    NOT(2, { a, _ -> a.inv() }, { a, _ -> a.inv() }), // = -x-1

    // binary
    ADD(10, { a, b -> a + b }, { a, b -> a + b }),
    SUB(11, { a, b -> a - b }, { a, b -> a - b }),
    MUL(12, { a, b -> a * b }, { a, b -> a * b }),
    DIV(13, { a, b -> a / b }, { a, b -> a / b }),
    MOD(14, { a, b -> a % b }, { a, b -> a % b }),

    LSL(20, { a, b -> a shl b }, { a, b -> a shl b.toInt() }),
    LSR(21, { a, b -> a ushr b }, { a, b -> a ushr b.toInt() }),
    SHR(22, { a, b -> a shr b }, { a, b -> a shr b.toInt() }),

    AND(30, { a, b -> a and b }, { a, b -> a and b }),
    OR(31, { a, b -> a or b }, { a, b -> a or b }),
    XOR(32, { a, b -> a xor b }, { a, b -> a xor b }),
    NOR(33, { a, b -> (a or b).inv() }, { a, b -> (a or b).inv() }),
    XNOR(34, { a, b -> (a xor b).inv() }, { a, b -> (a xor b).inv() }),
    NAND(35, { a, b -> (a and b).inv() }, { a, b -> (a and b).inv() }),

    // POW({ a, b -> me.anno.maths.Maths.pow(a, b) }, { a, b -> pow(a, b) }),
    // ROOT({ a, b -> me.anno.maths.Maths.pow(a, 1 / b) }, { a, b -> pow(a, 1 / b) }),
    // LENGTH({ a, b -> kotlin.math.sqrt(a * a + b * b) }, { a, b -> sqrt(a * a + b * b) }),
    LENGTH_SQUARED(40, { a, b -> a * a + b * b }, { a, b -> a * a + b * b }),
    ABS_DELTA(41, { a, b -> kotlin.math.abs(a - b) }, { a, b -> kotlin.math.abs(a - b) }),
    NORM1(42, { a, b -> kotlin.math.abs(a) + kotlin.math.abs(b) }, { a, b -> kotlin.math.abs(a) + kotlin.math.abs(b) }),
    AVG(43, { a, b -> (a + b) shr 1 }, { a, b -> (a + b) shr 1 }),

    // GEO_MEAN({ a, b -> kotlin.math.sqrt(a * b) }, { a, b -> kotlin.math.sqrt(a * b) }),
    MIN(50, { a, b -> kotlin.math.min(a, b) }, { a, b -> kotlin.math.min(a, b) }),
    MAX(51, { a, b -> kotlin.math.max(a, b) }, { a, b -> kotlin.math.max(a, b) }),

    // Kronecker delta
    EQUALS(60, { a, b -> if (a == b) 1 else 0 }, { a, b -> if (a == b) 1 else 0 }),

}