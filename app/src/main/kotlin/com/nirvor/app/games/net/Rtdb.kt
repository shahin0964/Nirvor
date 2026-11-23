package com.nirvor.app.games.net

import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The server said no (database rules). */
class DeniedException : IOException("permission denied")

/** Firebase Realtime Database over its plain HTTP interface. All calls block: use on an IO thread. */
class Rtdb(private val base: String, private val extra: String = "", private val token: () -> String) {

    private fun url(path: String, query: String): URL {
        val auth = URLEncoder.encode(token(), "UTF-8")
        return URL("$base/$path.json?auth=$auth" + (if (extra.isEmpty()) "" else "&$extra") + (if (query.isEmpty()) "" else "&$query"))
    }

    private fun open(method: String, path: String, query: String): HttpURLConnection =
        (url(path, query).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 12_000
        }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code == 401 || code == 403) throw DeniedException()
        if (code !in 200..299) throw IOException("server $code")
    }

    /** The value at [path]: JSONObject, String, Number, Boolean or null. */
    fun get(path: String, query: String = ""): Any? {
        val c = open("GET", path, query)
        try {
            check(c)
            return parse(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    fun put(path: String, json: String) {
        val c = open("PUT", path, "")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(json.toByteArray()) }
            check(c)
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun delete(path: String) {
        val c = open("DELETE", path, "")
        try {
            check(c)
        } finally {
            c.disconnect()
        }
    }

    /**
     * Follows [path]: [onValue] gets the whole value again after every change. Returns when the
     * connection ends; [onOpen] hands over the connection so that it can be cut from outside.
     */
    fun listen(path: String, onOpen: (HttpURLConnection) -> Unit, onValue: (JSONObject?) -> Unit) {
        var c = open("GET", path, "")
        var hops = 0
        while (true) {
            c.instanceFollowRedirects = false
            c.readTimeout = 45_000 // the server sends keep-alive every 30 s
            c.setRequestProperty("Accept", "text/event-stream")
            onOpen(c)
            val code = c.responseCode
            if (code in 300..399 && hops++ < 4) {
                val next = c.getHeaderField("Location") ?: throw IOException("redirect")
                c.disconnect()
                c = (URL(next).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000 }
                continue
            }
            break
        }
        try {
            check(c)
            val root = JSONObject()
            var event = ""
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: return
                    when {
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> when (event) {
                            "put", "patch" -> {
                                val o = JSONObject(line.substring(5).trim())
                                apply(root, o.getString("path"), o.opt("data"), event == "patch")
                                onValue(root.optJSONObject("v"))
                            }
                            "cancel" -> throw DeniedException()
                            "auth_revoked" -> return
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        fun parse(text: String): Any? = JSONTokener(text).nextValue().takeIf { it != JSONObject.NULL }

        /** Writes one change into the copy kept under "v" of [root]. */
        fun apply(root: JSONObject, path: String, data: Any?, patch: Boolean) {
            val keys = listOf("v") + path.split('/').filter { it.isNotEmpty() }
            if (patch) {
                val o = data as? JSONObject ?: return
                o.keys().forEach { k -> set(root, keys + k.split('/').filter { it.isNotEmpty() }, o.opt(k)) }
            } else {
                set(root, keys, data)
            }
        }

        private fun set(root: JSONObject, keys: List<String>, data: Any?) {
            var at = root
            for (k in keys.dropLast(1)) at = at.optJSONObject(k) ?: JSONObject().also { at.put(k, it) }
            if (data == null || data == JSONObject.NULL) at.remove(keys.last()) else at.put(keys.last(), data)
        }
    }
}
