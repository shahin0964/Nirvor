package com.nirvor.app.office

/**
 * Tiny JSON reader for the Word editor's document export: objects become Map, arrays List,
 * numbers Double. No Android classes, so it can be unit-tested.
 */
object MiniJson {
    fun parse(s: String): Any? {
        val p = Parser(s)
        p.ws()
        val v = p.value()
        return v
    }

    /** A JSON string literal. */
    fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Bad JSON at $i")
            }
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (i < s.length) {
                ws()
                val k = str()
                ws()
                i++ // ':'
                m[k] = value()
                ws()
                if (i < s.length && s[i] == ',') {
                    i++
                    continue
                }
                i++ // '}'
                break
            }
            return m
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (i < s.length) {
                l.add(value())
                ws()
                if (i < s.length && s[i] == ',') {
                    i++
                    continue
                }
                i++ // ']'
                break
            }
            return l
        }

        fun str(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }
}

// Small typed readers for parsed JSON.
@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun Any?.arr(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: ""

internal fun Map<String, Any?>.num(k: String): Double? = this[k] as? Double

internal fun Map<String, Any?>.flag(k: String): Boolean = this[k] as? Boolean ?: false
