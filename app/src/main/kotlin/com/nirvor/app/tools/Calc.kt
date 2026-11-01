package com.nirvor.app.tools

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class CalcError(message: String) : Exception(message)

/**
 * Calculator expressions: + − × ÷ ^ ( ) % ! ² √ π e Ans, sin cos tan asin acos atan ln log abs.
 * "200 + 10%" = 220 and "200 − 10%" = 180, like a phone calculator. Missing ")" at the end are
 * closed automatically; "2π" and "3(4)" multiply.
 */
object Calc {
    fun eval(expr: String, degrees: Boolean, ans: Double = 0.0): Double {
        val text = expr.replace('×', '*').replace('÷', '/').replace('−', '-').replace(" ", "")
        if (text.isEmpty()) throw CalcError("empty")
        val v = Parser(text, degrees, ans).parse()
        if (v.isNaN()) throw CalcError("nan")
        return v
    }

    private class Parser(val s: String, val deg: Boolean, val ans: Double) {
        var i = 0
        var lastPercent = false

        fun parse(): Double {
            val v = expr()
            if (i < s.length) throw CalcError("unexpected '${s[i]}'")
            return v
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun expr(): Double {
            var v = term().first
            while (true) {
                when (peek()) {
                    '+' -> {
                        i++
                        val (t, pct) = term()
                        v = if (pct) v + v * t else v + t
                    }
                    '-' -> {
                        i++
                        val (t, pct) = term()
                        v = if (pct) v - v * t else v - t
                    }
                    else -> return v
                }
            }
        }

        /** Value + "this term was a bare percentage" (so + / − can treat it as a share of the left side). */
        private fun term(): Pair<Double, Boolean> {
            var v = unary()
            var pct = lastPercent
            while (true) {
                val c = peek()
                when {
                    c == '*' -> {
                        i++
                        v *= unary()
                        pct = false
                    }
                    c == '/' -> {
                        i++
                        val d = unary()
                        if (d == 0.0) throw CalcError("÷0")
                        v /= d
                        pct = false
                    }
                    startsPrimary(c) -> {
                        // implicit multiplication: 2π, 3(4), 2sin(30)
                        v *= unary()
                        pct = false
                    }
                    else -> return v to pct
                }
            }
        }

        private fun startsPrimary(c: Char): Boolean =
            c.isDigit() || c == '.' || c == '(' || c == 'π' || c == '√' || c in 'a'..'z' || c in 'A'..'Z'

        private fun unary(): Double = when (peek()) {
            '-' -> {
                i++
                -unary()
            }
            '+' -> {
                i++
                unary()
            }
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                i++
                val e = unary()
                lastPercent = false
                return base.pow(e)
            }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            lastPercent = false
            while (true) {
                when (peek()) {
                    '!' -> {
                        i++
                        v = factorial(v)
                        lastPercent = false
                    }
                    '%' -> {
                        i++
                        v /= 100.0
                        lastPercent = true
                    }
                    '²' -> {
                        i++
                        v *= v
                        lastPercent = false
                    }
                    else -> return v
                }
            }
        }

        private fun primary(): Double {
            val c = peek()
            when {
                c.isDigit() || c == '.' -> return number()
                c == '(' -> {
                    i++
                    val v = expr()
                    if (peek() == ')') i++ // a missing ")" at the end is fine
                    return v
                }
                c == 'π' -> {
                    i++
                    return PI
                }
                c == '√' -> {
                    i++
                    val v = if (peek() == '(') primary() else power()
                    if (v < 0) throw CalcError("√-")
                    return sqrt(v)
                }
                c in 'a'..'z' || c in 'A'..'Z' -> {
                    val start = i
                    while (i < s.length && (s[i] in 'a'..'z' || s[i] in 'A'..'Z')) i++
                    val name = s.substring(start, i).lowercase()
                    return when (name) {
                        "e" -> E
                        "ans" -> ans
                        "pi" -> PI
                        else -> applyFunction(name, argument())
                    }
                }
                else -> throw CalcError("unexpected")
            }
        }

        private fun argument(): Double =
            if (peek() == '(') {
                i++
                val v = expr()
                if (peek() == ')') i++
                v
            } else {
                power()
            }

        private fun applyFunction(name: String, x: Double): Double {
            val rad = if (deg) Math.toRadians(x) else x
            fun back(r: Double) = if (deg) Math.toDegrees(r) else r
            return when (name) {
                "sin" -> clean(sin(rad))
                "cos" -> clean(cos(rad))
                "tan" -> {
                    if (deg && abs((x % 180.0 + 180.0) % 180.0 - 90.0) < 1e-9) throw CalcError("tan90")
                    clean(tan(rad))
                }
                "asin" -> back(asin(x))
                "acos" -> back(acos(x))
                "atan" -> back(atan(x))
                "ln" -> {
                    if (x <= 0) throw CalcError("ln")
                    ln(x)
                }
                "log" -> {
                    if (x <= 0) throw CalcError("log")
                    log10(x)
                }
                "sqrt" -> sqrt(x)
                "abs" -> abs(x)
                else -> throw CalcError("unknown $name")
            }
        }

        /** sin(180°) should be 0, not 1.2e-16. */
        private fun clean(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw CalcError("number")
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v)) throw CalcError("!")
            if (v > 170) return Double.POSITIVE_INFINITY
            var r = 1.0
            var k = 2
            while (k <= v.toInt()) {
                r *= k
                k++
            }
            return r
        }
    }
}
