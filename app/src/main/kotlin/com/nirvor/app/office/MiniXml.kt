package com.nirvor.app.office

/** One element of an XML part (DOCX / XLSX): name, attributes and children (elements or text). */
class XNode(val name: String, val attrs: Map<String, String>) {
    val children = mutableListOf<Any>()

    /** Name without the namespace prefix ("w:p" → "p"). */
    val local: String get() = name.substringAfter(':')

    /** Attribute by full name ("w:val") or by local name ("val"). */
    fun attr(n: String): String? = attrs[n] ?: attrs.entries.firstOrNull { it.key.substringAfter(':') == n }?.value

    fun elements(): List<XNode> = children.filterIsInstance<XNode>()

    fun child(local: String): XNode? = children.firstOrNull { it is XNode && it.local == local } as XNode?

    fun all(local: String): List<XNode> = elements().filter { it.local == local }

    /** Every element below this one (depth first) with the local name. */
    fun find(local: String): List<XNode> {
        val out = mutableListOf<XNode>()
        fun walk(n: XNode) {
            for (c in n.children) if (c is XNode) {
                if (c.local == local) out.add(c)
                walk(c)
            }
        }
        walk(this)
        return out
    }

    /** All text inside, in order. */
    fun text(): String = buildString {
        fun walk(n: XNode) {
            for (c in n.children) if (c is String) append(c) else if (c is XNode) walk(c)
        }
        walk(this@XNode)
    }
}

/** A small, forgiving XML reader and writer helpers (no Android classes, so it is unit-testable). */
object MiniXml {
    fun parse(src: String): XNode {
        val root = XNode("#root", emptyMap())
        val stack = ArrayDeque<XNode>()
        stack.addLast(root)
        var i = 0
        val n = src.length
        val text = StringBuilder()

        fun flushText() {
            if (text.isNotEmpty()) {
                stack.last().children.add(decode(text.toString()))
                text.setLength(0)
            }
        }

        while (i < n) {
            val c = src[i]
            if (c != '<') {
                val next = src.indexOf('<', i).let { if (it < 0) n else it }
                text.append(src, i, next)
                i = next
                continue
            }
            when {
                src.startsWith("<!--", i) -> {
                    val e = src.indexOf("-->", i + 4)
                    i = if (e < 0) n else e + 3
                }
                src.startsWith("<![CDATA[", i) -> {
                    val e = src.indexOf("]]>", i + 9)
                    val end = if (e < 0) n else e
                    flushText()
                    stack.last().children.add(src.substring(i + 9, end))
                    i = if (e < 0) n else e + 3
                }
                src.startsWith("<?", i) -> {
                    val e = src.indexOf("?>", i + 2)
                    i = if (e < 0) n else e + 2
                }
                src.startsWith("<!", i) -> {
                    val e = src.indexOf('>', i + 2)
                    i = if (e < 0) n else e + 1
                }
                src.startsWith("</", i) -> {
                    flushText()
                    val e = src.indexOf('>', i + 2)
                    val name = src.substring(i + 2, if (e < 0) n else e).trim()
                    // close up to the matching element (tolerates small mistakes)
                    if (stack.any { it.name == name }) {
                        while (stack.size > 1) {
                            val top = stack.removeLast()
                            if (top.name == name) break
                        }
                    }
                    i = if (e < 0) n else e + 1
                }
                else -> {
                    flushText()
                    var j = i + 1
                    while (j < n && !src[j].isWhitespace() && src[j] != '>' && src[j] != '/') j++
                    val name = src.substring(i + 1, j)
                    val attrs = LinkedHashMap<String, String>()
                    var selfClose = false
                    while (j < n) {
                        while (j < n && src[j].isWhitespace()) j++
                        if (j >= n) break
                        if (src[j] == '>') {
                            j++
                            break
                        }
                        if (src[j] == '/') {
                            selfClose = true
                            j++
                            continue
                        }
                        val ks = j
                        while (j < n && src[j] != '=' && !src[j].isWhitespace() && src[j] != '>' && src[j] != '/') j++
                        val key = src.substring(ks, j)
                        while (j < n && src[j].isWhitespace()) j++
                        if (j < n && src[j] == '=') {
                            j++
                            while (j < n && src[j].isWhitespace()) j++
                            if (j < n && (src[j] == '"' || src[j] == '\'')) {
                                val q = src[j]
                                val e = src.indexOf(q, j + 1).let { if (it < 0) n else it }
                                attrs[key] = decode(src.substring(j + 1, e))
                                j = e + 1
                            } else {
                                val vs = j
                                while (j < n && !src[j].isWhitespace() && src[j] != '>') j++
                                attrs[key] = decode(src.substring(vs, j))
                            }
                        } else if (key.isNotEmpty()) {
                            attrs[key] = ""
                        }
                    }
                    val node = XNode(name, attrs)
                    stack.last().children.add(node)
                    if (!selfClose) stack.addLast(node)
                    i = j
                }
            }
        }
        flushText()
        return root
    }

    private val named = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    fun decode(s: String): String {
        if (s.indexOf('&') < 0) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val e = s.indexOf(';', i + 1)
                if (e > i && e - i <= 10) {
                    val ent = s.substring(i + 1, e)
                    val rep = when {
                        ent.startsWith("#x") || ent.startsWith("#X") -> ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> named[ent]
                    }
                    if (rep != null) {
                        out.append(rep)
                        i = e + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** Escapes text for element content and attribute values; drops characters XML cannot hold. */
    fun esc(s: String): String {
        val out = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '<' -> out.append("&lt;")
                ch == '>' -> out.append("&gt;")
                ch == '&' -> out.append("&amp;")
                ch == '"' -> out.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> out.append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
