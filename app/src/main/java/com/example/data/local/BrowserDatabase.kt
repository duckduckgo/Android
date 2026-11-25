package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "fireproof_sites")
data class FireproofSiteEntity(
    @PrimaryKey val domain: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val status: String = "Completed",
    val systemDownloadId: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isFavorite = 1 ORDER BY createdAt ASC")
    fun observeFavorites(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' LIMIT 6")
    suspend fun search(query: String): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 6")
    suspend fun search(query: String): List<HistoryEntity>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface FireproofSiteDao {
    @Query("SELECT * FROM fireproof_sites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FireproofSiteEntity>>

    @Query("SELECT domain FROM fireproof_sites")
    suspend fun getAllDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: FireproofSiteEntity)

    @Query("DELETE FROM fireproof_sites WHERE domain = :domain")
    suspend fun delete(domain: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        FireproofSiteEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun fireproofSiteDao(): FireproofSiteDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: BrowserDatabase? = null

        fun getInstance(context: Context): BrowserDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "duckduckgo_browser.db",
                ).build().also { instance = it }
            }
        }
    }
}
