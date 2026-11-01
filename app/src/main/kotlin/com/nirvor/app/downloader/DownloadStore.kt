package com.nirvor.app.downloader

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** What kind of thing a download is. Decides the folder, icon and colour. */
enum class Kind(val key: String, val bn: String) {
    Video("video", "ভিডিও"),
    Audio("audio", "অডিও"),
    Photo("photo", "ছবি"),
    Other("file", "ফাইল");

    companion object {
        fun of(key: String): Kind = entries.firstOrNull { it.key == key } ?: Other
    }
}

object Status {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

object EngineType {
    /** Pages like YouTube / Facebook / Instagram, handled by yt-dlp. */
    const val YTDLP = "ytdlp"

    /** A plain file link, downloaded over HTTP. */
    const val DIRECT = "direct"
}

/**
 * One download. [args] holds the yt-dlp arguments (JSON array) for [EngineType.YTDLP], or the
 * request headers (JSON object) for [EngineType.DIRECT].
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val kind: String,
    val engine: String,
    val label: String,
    val args: String,
    val fileName: String? = null,
    val mime: String? = null,
    val status: String = Status.QUEUED,
    val progress: Float = 0f,
    val totalBytes: Long = 0,
    val fileUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'queued' ORDER BY createdAt ASC")
    suspend fun queued(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(d: DownloadEntity): Long

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?)

    @Query("UPDATE downloads SET progress = :progress, totalBytes = :total WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Float, total: Long)

    @Query(
        "UPDATE downloads SET status = 'done', progress = 1, fileUri = :uri, fileName = :name, " +
            "mime = :mime, totalBytes = :size, error = NULL, finishedAt = :at WHERE id = :id"
    )
    suspend fun setDone(id: Long, uri: String, name: String, mime: String, size: Long, at: Long)

    @Query("UPDATE downloads SET status = 'queued', progress = 0, error = NULL WHERE id = :id")
    suspend fun requeue(id: Long)

    /** Resume keeps the progress already made (the partial file is continued). */
    @Query("UPDATE downloads SET status = 'queued', error = NULL WHERE id = :id")
    suspend fun resume(id: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('queued', 'running')")
    suspend fun activeCount(): Int

    /** After the app was killed mid-download, put those back in the queue. */
    @Query("UPDATE downloads SET status = 'queued' WHERE status = 'running'")
    suspend fun requeueInterrupted()

    @Delete
    suspend fun delete(d: DownloadEntity)

    @Query("DELETE FROM downloads WHERE status = 'done'")
    suspend fun clearFinished()
}

/** Kept apart from the main database so tasks/expenses never need a migration for this. */
@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class DownloadsDatabase : RoomDatabase() {
    abstract fun dao(): DownloadDao

    companion object {
        @Volatile
        private var instance: DownloadsDatabase? = null

        fun get(context: Context): DownloadsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadsDatabase::class.java,
                    "downloads.db",
                ).build().also { instance = it }
            }
    }
}
