package com.nirvor.app.downloader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs downloads in the background (keeps going when the app is closed), shows their progress
 * in a notification, and stops itself when the queue is empty.
 */
class DownloadService : Service() {

    companion object {
        const val ACTION_PUMP = "com.nirvor.app.download.PUMP"
        const val ACTION_PAUSE = "com.nirvor.app.download.PAUSE"
        const val ACTION_CANCEL = "com.nirvor.app.download.CANCEL"
        private const val STARTING = "শুরু হচ্ছে…"
        const val EXTRA_ID = "id"

        /** True while the service is alive in this process. */
        @Volatile
        var alive = false
            private set

        fun start(context: Context, action: String = ACTION_PUMP, id: Long = -1) {
            val i = Intent(context, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                // Not allowed from the background right now; it starts the next time the app is open.
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pumpLock = Mutex()
    private val running = ConcurrentHashMap<Long, Job>()
    private val titles = ConcurrentHashMap<Long, String>()

    /** Why a job was stopped by the user: "pause" or "cancel". */
    private val stopReason = ConcurrentHashMap<Long, String>()

    private lateinit var dao: DownloadDao
    private lateinit var prefs: DownloadPrefs
    private var waitingForWifi = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var notifierJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        dao = DownloadsDatabase.get(this).dao()
        prefs = DownloadPrefs(this)
        DownloadNotifications.ensureChannels(this)
        goForeground("ডাউনলোড প্রস্তুত হচ্ছে…", "", -1)
        watchNetwork()
        notifierJob = scope.launch {
            while (isActive) {
                updateNotification()
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground("ডাউনলোড চলছে", "", -1)
        if (notifierJob?.isActive != true) {
            notifierJob = scope.launch {
                while (isActive) {
                    updateNotification()
                    delay(1000)
                }
            }
        }
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_PAUSE -> stop(id, "pause")
            ACTION_CANCEL -> stop(id, "cancel")
        }
        pump()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let {
            try {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        scope.cancel()
        alive = false
        super.onDestroy()
    }

    private fun goForeground(title: String, text: String, percent: Int) {
        val n = DownloadNotifications.ongoing(this, title, text, percent)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, DownloadNotifications.ONGOING_ID, n, type)
    }

    // ---------------------------------------------------------------- queue

    private fun pump() {
        scope.launch {
            pumpLock.withLock {
                if (prefs.wifiOnly && !onUnmeteredNetwork()) {
                    waitingForWifi = true
                    if (running.isEmpty() && dao.queued().isEmpty()) finish()
                    return@withLock
                }
                waitingForWifi = false
                val free = prefs.parallel - running.size
                if (free > 0) {
                    dao.queued().take(free).forEach { d ->
                        if (!running.containsKey(d.id)) {
                            dao.setStatus(d.id, Status.RUNNING, null)
                            titles[d.id] = d.title
                            running[d.id] = scope.launch { runJob(d) }
                        }
                    }
                }
                if (running.isEmpty() && dao.queued().isEmpty()) finish()
            }
        }
    }

    private fun finish() {
        notifierJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(DownloadNotifications.ONGOING_ID)
        stopSelf()
    }

    private fun stop(id: Long, reason: String) {
        if (id < 0) return
        stopReason[id] = reason
        MediaEngine.cancel(processId(id))
        running[id]?.cancel()
        if (!running.containsKey(id)) {
            // Not running (queued or paused): apply directly.
            scope.launch { applyStop(id, reason) }
        }
    }

    private suspend fun applyStop(id: Long, reason: String) {
        DownloadLive.clear(id)
        if (reason == "cancel") {
            dao.get(id)?.let { dao.delete(it) }
            Storage.deleteJobDir(this, id)
        } else {
            dao.setStatus(id, Status.PAUSED, null)
        }
    }

    private fun processId(id: Long) = "nirvor-$id"

    // ---------------------------------------------------------------- one job

    private suspend fun runJob(d: DownloadEntity) {
        val dir = Storage.jobDir(this, d.id)
        try {
            if (d.engine == EngineType.YTDLP) runYtDlp(d, dir) else runDirect(d, dir)

            val files = dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".parts") && !it.name.endsWith(".ytdl") && !it.name.contains(".temp") }
                ?.sortedBy { it.lastModified() }
                .orEmpty()
            if (files.isEmpty()) throw IllegalStateException("ফাইল পাওয়া যায়নি")

            DownloadLive.set(d.id, LiveStat(stage = "সেভ হচ্ছে"))
            var last: Storage.Published? = null
            var total = 0L
            for (f in files) {
                val mime = d.mime?.takeIf { files.size == 1 && it.isNotBlank() }
                    ?: Analyzer.mimeForName(f.name, "application/octet-stream")
                total += f.length()
                last = Storage.publish(this, f, mime)
            }
            val p = last!!
            val name = if (files.size == 1) p.name else "${files.size}টা ফাইল"
            dao.setDone(d.id, p.uri.toString(), name, p.mime, total, System.currentTimeMillis())
            Storage.deleteJobDir(this, d.id)
            DownloadLive.clear(d.id)
            if (prefs.notifyDone) DownloadNotifications.done(this, d.id, d.title, p.uri, p.mime)
        } catch (e: Throwable) {
            // The coroutine may already be cancelled (pause / cancel), so save the outcome regardless.
            withContext(NonCancellable) {
                val reason = stopReason.remove(d.id)
                when {
                    reason != null -> applyStop(d.id, reason)
                    e is CancellationException || e is com.yausername.youtubedl_android.YoutubeDL.CanceledException ->
                        dao.setStatus(d.id, Status.PAUSED, null)
                    else -> {
                        val msg = friendly(e)
                        dao.setStatus(d.id, Status.FAILED, msg)
                        DownloadLive.clear(d.id)
                        DownloadNotifications.failed(this@DownloadService, d.id, d.title, msg)
                    }
                }
            }
        } finally {
            running.remove(d.id)
            titles.remove(d.id)
            pump()
        }
    }

    private suspend fun runYtDlp(d: DownloadEntity, dir: File) {
        val args = mutableListOf<String>()
        val arr = JSONArray(d.args)
        for (i in 0 until arr.length()) args += arr.getString(i)
        // The page description saved by the format list makes the download start at once.
        // If it was cleared meanwhile, yt-dlp simply reads the page again.
        val infoAt = args.indexOf("--load-info-json")
        if (infoAt >= 0 && (infoAt + 1 >= args.size || !File(args[infoAt + 1]).exists())) {
            args.subList(infoAt, minOf(infoAt + 2, args.size)).clear()
        }
        args += listOf("--concurrent-fragments", prefs.parts.toString(), "--newline")
        DownloadLive.set(d.id, LiveStat(stage = STARTING))
        var lastWrite = 0L
        MediaEngine.download(this, d.url, args, dir, processId(d.id)) { percent, eta, line ->
            val (speed, total) = DownloadLive.parseLine(line)
            val prev = DownloadLive.stats.value[d.id]
            val stage = DownloadLive.stageOf(line) ?: prev?.stage?.takeIf { it != STARTING || percent <= 0f }
            DownloadLive.set(
                d.id,
                LiveStat(
                    speedBps = if (speed > 0) speed else prev?.speedBps ?: 0L,
                    etaSec = eta,
                    totalBytes = if (total > 0) total else prev?.totalBytes ?: 0L,
                    doneBytes = if (total > 0) (total * percent / 100f).toLong() else 0L,
                    stage = stage,
                ),
            )
            val now = System.currentTimeMillis()
            if (now - lastWrite > 700) {
                lastWrite = now
                scope.launch { dao.setProgress(d.id, (percent / 100f).coerceIn(0f, 1f), total) }
            }
        }
    }

    private suspend fun runDirect(d: DownloadEntity, dir: File) {
        val headers = mutableMapOf<String, String>()
        try {
            val o = JSONObject(d.args)
            o.keys().forEach { k -> headers[k] = o.getString(k) }
        } catch (e: Exception) {
            // no headers
        }
        val name = Analyzer.safeName(d.fileName ?: d.title).let { n ->
            if (n.contains('.')) n else n + extFor(d.mime)
        }
        val out = File(dir, name)
        DownloadLive.set(d.id, LiveStat(stage = STARTING))
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()
        var speed = 0L
        var lastWrite = 0L
        DirectDownloader(d.url, headers, out, prefs.parts).start { done, total ->
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (dt >= 800) {
                val inst = (done - lastBytes) * 1000 / dt
                speed = if (speed == 0L) inst else (speed * 6 + inst * 4) / 10
                lastBytes = done
                lastTime = now
            }
            val eta = if (speed > 0 && total > 0) (total - done) / speed else -1
            DownloadLive.set(d.id, LiveStat(speedBps = speed, etaSec = eta, doneBytes = done, totalBytes = total))
            if (now - lastWrite > 700) {
                lastWrite = now
                val p = if (total > 0) done.toFloat() / total else 0f
                scope.launch { dao.setProgress(d.id, p.coerceIn(0f, 1f), total) }
            }
        }
    }

    private fun extFor(mime: String?): String {
        val ext = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext.isNullOrBlank()) "" else ".$ext"
    }

    private fun friendly(e: Throwable): String {
        val m = (e.message ?: e.javaClass.simpleName).lowercase()
        return when {
            "403" in m || "forbidden" in m -> "সার্ভার অনুমতি দেয়নি (403)"
            "404" in m -> "ফাইল আর নেই (404)"
            "login" in m || "cookies" in m || "private" in m -> "লগইন লাগবে: ব্রাউজারে লগইন করে আবার চেষ্টা করুন"
            "no space" in m || "enospc" in m -> "ফোনে জায়গা নেই"
            "unable to resolve host" in m || "timeout" in m || "timed out" in m || "network" in m -> "ইন্টারনেট সংযোগে সমস্যা"
            else -> (e.message ?: "অজানা সমস্যা").lines().lastOrNull { it.isNotBlank() }?.take(140) ?: "অজানা সমস্যা"
        }
    }

    // ---------------------------------------------------------------- notification + network

    private suspend fun updateNotification() {
        val ids = running.keys.toList()
        if (ids.isEmpty()) {
            if (waitingForWifi) goForeground("Wi-Fi-র অপেক্ষায়", "Wi-Fi পেলে ডাউনলোড শুরু হবে", 0)
            return
        }
        val stats = DownloadLive.stats.value
        var done = 0L
        var total = 0L
        var speed = 0L
        ids.forEach { id ->
            stats[id]?.let {
                done += it.doneBytes
                total += it.totalBytes
                speed += it.speedBps
            }
        }
        val percent = if (total > 0) (done * 100 / total).toInt() else -1
        val title = if (ids.size == 1) titles[ids.first()] ?: "ডাউনলোড চলছে" else "${ids.size}টা ডাউনলোড চলছে"
        val text = listOf(
            if (percent >= 0) "$percent%" else "",
            DownloadLive.speedText(speed),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        try {
            NotificationManagerCompat.from(this).notify(
                DownloadNotifications.ONGOING_ID,
                DownloadNotifications.ongoing(this, title, text, percent),
            )
        } catch (e: SecurityException) {
            // notifications are off; the download still runs
        }
    }

    private fun onUnmeteredNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (waitingForWifi && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) pump()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (e: Exception) {
            // cannot watch; the user can retry by hand
        }
    }
}
