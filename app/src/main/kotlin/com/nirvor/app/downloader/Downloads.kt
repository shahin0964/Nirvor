package com.nirvor.app.downloader

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** What the screens call: add, pause, resume, retry, delete, open and share downloads. */
object Downloads {

    fun dao(context: Context) = DownloadsDatabase.get(context).dao()

    /** Adds the chosen format to the queue and starts the background downloader. */
    suspend fun enqueue(context: Context, info: MediaInfo, option: DlOption): Long {
        val args = if (option.engine == EngineType.YTDLP) {
            JSONArray(option.args).toString()
        } else {
            JSONObject(option.headers as Map<*, *>).toString()
        }
        val title = when (option.kind) {
            Kind.Photo, Kind.Other -> option.fileName?.substringBeforeLast('.') ?: info.title
            else -> info.title
        }
        val entity = DownloadEntity(
            url = if (option.engine == EngineType.DIRECT) option.directUrl ?: info.pageUrl else info.pageUrl,
            title = title,
            thumbnail = info.thumbnail ?: if (option.kind == Kind.Photo) option.directUrl else null,
            kind = option.kind.key,
            engine = option.engine,
            label = option.label,
            args = args,
            fileName = option.fileName,
            mime = option.mime,
        )
        val id = dao(context).insert(entity)
        DownloadService.start(context)
        return id
    }

    /** A file link found by the browser (or a page's download button). */
    suspend fun enqueueFile(
        context: Context,
        url: String,
        fileName: String,
        mime: String?,
        referer: String?,
        size: Long,
    ): Long {
        val kind = Analyzer.kindForMime(mime ?: Analyzer.mimeForName(fileName, ""))
        val option = DlOption(
            kind = kind,
            label = fileName.substringAfterLast('.', "file").uppercase(),
            detail = Analyzer.sizeText(size),
            sizeBytes = size,
            engine = EngineType.DIRECT,
            directUrl = url,
            headers = Analyzer.headersFor(url, referer),
            fileName = fileName,
            mime = mime,
        )
        val info = MediaInfo(referer ?: url, fileName, if (kind == Kind.Photo) url else null, Analyzer.hostOf(url), 0, listOf(option))
        return enqueue(context, info, option)
    }

    fun pause(context: Context, id: Long) = DownloadService.start(context, DownloadService.ACTION_PAUSE, id)

    suspend fun resume(context: Context, id: Long) {
        dao(context).resume(id)
        DownloadService.start(context)
    }

    suspend fun retry(context: Context, id: Long) {
        Storage.deleteJobDir(context, id)
        dao(context).requeue(id)
        DownloadService.start(context)
    }

    /** Stops (if running) and removes from the list. [deleteFile] also removes the saved file. */
    suspend fun remove(context: Context, d: DownloadEntity, deleteFile: Boolean) {
        if (d.status == Status.RUNNING || d.status == Status.QUEUED) {
            DownloadService.start(context, DownloadService.ACTION_CANCEL, d.id)
            return
        }
        if (deleteFile) d.fileUri?.let { Storage.deletePublished(context, Uri.parse(it)) }
        Storage.deleteJobDir(context, d.id)
        DownloadLive.clear(d.id)
        dao(context).delete(d)
    }

    suspend fun clearFinished(context: Context) = dao(context).clearFinished()

    fun open(context: Context, d: DownloadEntity): Boolean {
        val uri = d.fileUri ?: return false
        return try {
            context.startActivity(Storage.openIntent(Uri.parse(uri), d.mime))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun share(context: Context, d: DownloadEntity): Boolean {
        val uri = d.fileUri ?: return false
        return try {
            context.startActivity(Storage.shareIntent(Uri.parse(uri), d.mime))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Called when the app opens: downloads cut off by the app being killed go back in the queue. */
    suspend fun recover(context: Context) {
        if (DownloadService.alive) return
        val dao = dao(context)
        dao.requeueInterrupted()
        if (dao.activeCount() > 0) DownloadService.start(context)
    }
}
