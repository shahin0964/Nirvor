package com.nirvor.app.office

import android.content.Context
import java.io.File
import java.util.UUID

/** One document of the Word editor. */
class WordDoc(val id: String, val title: String, val updated: Long, val size: Long)

/**
 * The Word editor's documents, kept inside the app: `word/<id>.html` (the page) and
 * `word/<id>.json` (name and times). Exported Word / PDF files go to the Nirvor folder.
 */
object WordStore {
    /** A document another tool made (e.g. PDF → Word) that the Word editor should open next. */
    @Volatile
    var pendingOpen: String? = null

    private fun dir(context: Context) = File(context.filesDir, "word").apply { mkdirs() }

    private fun meta(context: Context, id: String) = File(dir(context), "$id.json")

    private fun page(context: Context, id: String) = File(dir(context), "$id.html")

    private class Meta(val title: String, val auto: Boolean, val created: Long, val updated: Long)

    private fun readMeta(context: Context, id: String): Meta? = try {
        val m = MiniJson.parse(meta(context, id).readText()).obj()
        Meta(m.str("title"), m.flag("auto"), (m.num("created") ?: 0.0).toLong(), (m.num("updated") ?: 0.0).toLong())
    } catch (e: Exception) {
        null
    }

    private fun writeMeta(context: Context, id: String, m: Meta) {
        meta(context, id).writeText(
            "{\"title\":${MiniJson.quote(m.title)},\"auto\":${m.auto},\"created\":${m.created},\"updated\":${m.updated}}"
        )
    }

    fun list(context: Context): List<WordDoc> = dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { f ->
            val id = f.name.removeSuffix(".json")
            val m = readMeta(context, id) ?: return@mapNotNull null
            WordDoc(id, m.title.ifBlank { "নাম ছাড়া ডকুমেন্ট" }, m.updated, page(context, id).length())
        }
        .sortedByDescending { it.updated }

    /** A new document; [title] blank = named after its first line as you type. */
    fun create(context: Context, title: String, html: String): String {
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val now = System.currentTimeMillis()
        page(context, id).writeText(html)
        writeMeta(context, id, Meta(title, title.isBlank(), now, now))
        return id
    }

    fun load(context: Context, id: String): String? = page(context, id).takeIf { it.exists() }?.readText()

    fun title(context: Context, id: String): String = readMeta(context, id)?.title.orEmpty()

    /** Saves the page; while the name is automatic it follows the first line ([firstLine]). */
    fun save(context: Context, id: String, html: String, firstLine: String) {
        val m = readMeta(context, id) ?: Meta("", true, System.currentTimeMillis(), 0L)
        page(context, id).writeText(html)
        val title = if (m.auto) firstLine.trim().take(60) else m.title
        writeMeta(context, id, Meta(title, m.auto, m.created, System.currentTimeMillis()))
    }

    fun rename(context: Context, id: String, title: String) {
        val m = readMeta(context, id) ?: return
        writeMeta(context, id, Meta(title.trim(), title.isBlank(), m.created, m.updated))
    }

    fun delete(context: Context, id: String) {
        page(context, id).delete()
        meta(context, id).delete()
    }

    /** Safe file name from a title ("আমার দরখাস্ত" → "আমার দরখাস্ত"). */
    fun fileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(60).ifBlank { "Document" }
}
