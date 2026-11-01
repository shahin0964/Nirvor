package com.nirvor.app.browser

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", indices = [Index("url"), Index("visitedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bookmarks", indices = [Index("url")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Per-site choices; -1 = follow the browser setting, 0 = off, 1 = on. */
@Entity(tableName = "site_settings")
data class SiteSettingEntity(
    @PrimaryKey val host: String,
    val javascript: Int = -1,
    val desktop: Int = -1,
    val adblock: Int = -1,
    val images: Int = -1,
    val darkMode: Int = -1,
)

/** Ad blocking: kind = host | filter | allow (a site where ads are not blocked). */
@Entity(tableName = "ad_rules", indices = [Index(value = ["rule", "kind"], unique = true)])
data class AdRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rule: String,
    val kind: String,
)

/** Downloadable block lists (hosts files, EasyList). */
@Entity(tableName = "ad_lists")
data class AdListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val count: Int = 0,
    val updatedAt: Long = 0,
)

@Dao
interface BrowserDao {
    // history
    @Query("SELECT * FROM history WHERE url LIKE :q OR title LIKE :q ORDER BY visitedAt DESC LIMIT 500")
    fun history(q: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url LIKE :q OR title LIKE :q ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun searchHistory(q: String, limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun lastVisit(): HistoryEntity?

    @Insert
    suspend fun addHistory(h: HistoryEntity): Long

    @Query("UPDATE history SET title = :title, visitedAt = :at WHERE id = :id")
    suspend fun touchHistory(id: Long, title: String, at: Long)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY folder, createdAt DESC")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE url LIKE :q OR title LIKE :q LIMIT :limit")
    suspend fun searchBookmarks(q: String, limit: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun bookmarkFor(url: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    fun isBookmarked(url: String): Flow<Int>

    @Insert
    suspend fun addBookmark(b: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET title = :title, url = :url, folder = :folder WHERE id = :id")
    suspend fun updateBookmark(id: Long, title: String, url: String, folder: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()

    // site settings
    @Query("SELECT * FROM site_settings ORDER BY host")
    fun siteSettings(): Flow<List<SiteSettingEntity>>

    @Query("SELECT * FROM site_settings")
    suspend fun allSiteSettings(): List<SiteSettingEntity>

    @Upsert
    suspend fun saveSite(s: SiteSettingEntity)

    @Query("DELETE FROM site_settings WHERE host = :host")
    suspend fun deleteSite(host: String)

    @Query("DELETE FROM site_settings")
    suspend fun clearSites()

    // ad rules
    @Query("SELECT * FROM ad_rules ORDER BY kind, rule")
    fun adRules(): Flow<List<AdRuleEntity>>

    @Query("SELECT * FROM ad_rules")
    suspend fun allAdRules(): List<AdRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAdRule(r: AdRuleEntity): Long

    @Query("DELETE FROM ad_rules WHERE id = :id")
    suspend fun deleteAdRule(id: Long)

    // ad lists
    @Query("SELECT * FROM ad_lists ORDER BY name")
    fun adLists(): Flow<List<AdListEntity>>

    @Query("SELECT * FROM ad_lists")
    suspend fun allAdLists(): List<AdListEntity>

    @Insert
    suspend fun addAdList(l: AdListEntity): Long

    @Query("UPDATE ad_lists SET enabled = :enabled WHERE id = :id")
    suspend fun setAdListEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE ad_lists SET count = :count, updatedAt = :at WHERE id = :id")
    suspend fun setAdListUpdated(id: Long, count: Int, at: Long)

    @Query("DELETE FROM ad_lists WHERE id = :id")
    suspend fun deleteAdList(id: Long)
}

@Database(
    entities = [HistoryEntity::class, BookmarkEntity::class, SiteSettingEntity::class, AdRuleEntity::class, AdListEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun dao(): BrowserDao

    companion object {
        @Volatile
        private var instance: BrowserDatabase? = null

        fun get(context: Context): BrowserDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, BrowserDatabase::class.java, "browser.db")
                    .build()
                    .also { instance = it }
            }
    }
}
