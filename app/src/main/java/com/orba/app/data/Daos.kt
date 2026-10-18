package com.orba.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Video>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun get(id: Long): Video?

    @Query("SELECT * FROM videos WHERE sourceUrl = :url AND kind = :kind LIMIT 1")
    suspend fun findByUrl(url: String, kind: Int): Video?

    @Insert
    suspend fun insert(video: Video): Long

    @Update
    suspend fun update(video: Video)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE videos SET localThumbPath = :path WHERE id = :id")
    suspend fun setThumb(id: Long, path: String)

    @Query("UPDATE videos SET downloadState = :state, downloadProgress = :progress, downloadError = :error WHERE id = :id")
    suspend fun setDownload(id: Long, state: Int, progress: Float, error: String?)

    @Query("UPDATE videos SET downloadProgress = :progress WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Float)

    @Query("UPDATE videos SET downloadState = 3, downloadProgress = 1, localFilePath = :path, fileSizeBytes = :size, downloadError = NULL WHERE id = :id")
    suspend fun setDownloaded(id: Long, path: String, size: Long)

    @Query("UPDATE videos SET downloadState = 0, downloadProgress = 0, localFilePath = NULL, fileSizeBytes = 0, downloadError = NULL WHERE id = :id")
    suspend fun clearDownload(id: Long)

    @Query("UPDATE videos SET lastPositionMs = :pos WHERE id = :id")
    suspend fun savePosition(id: Long, pos: Long)

    @Query("UPDATE videos SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)

    @Query("UPDATE videos SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE videos SET unavailableOnline = :value WHERE id = :id")
    suspend fun setUnavailable(id: Long, value: Boolean)

    @Query("UPDATE videos SET orientation = :orientation WHERE id = :id")
    suspend fun setOrientation(id: Long, orientation: Int)

    @Query("UPDATE videos SET width = :width, height = :height, isVertical = :vertical WHERE id = :id")
    suspend fun setDimensions(id: Long, width: Int, height: Int, vertical: Boolean)

    @Query("UPDATE videos SET lastPlayedAt = :time WHERE id = :id")
    suspend fun setLastPlayed(id: Long, time: Long)

    @Query("UPDATE videos SET favorite = :value WHERE id = :id")
    suspend fun setFavorite(id: Long, value: Boolean)

    @Query("UPDATE videos SET boostDb = :db WHERE id = :id")
    suspend fun setBoost(id: Long, db: Int)

    @Query("SELECT * FROM videos WHERE kind = :kind")
    suspend fun allOfKind(kind: Int): List<Video>

    @Query("UPDATE videos SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategoryMany(ids: List<Long>, categoryId: Long?)

    @Query("SELECT * FROM videos")
    suspend fun all(): List<Video>

    @Query("SELECT * FROM videos WHERE downloadState IN (1, 2)")
    suspend fun unfinishedDownloads(): List<Video>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Insert
    suspend fun insert(category: Category): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE videoId = :videoId ORDER BY positionMs IS NULL, positionMs, createdAt")
    fun observeFor(videoId: Long): Flow<List<Note>>

    @Query("SELECT videoId, COUNT(*) AS count FROM notes GROUP BY videoId")
    fun observeCounts(): Flow<List<NoteCount>>

    @Insert
    suspend fun insert(note: Note): Long

    @Query("UPDATE notes SET text = :text, updatedAt = :now WHERE id = :id")
    suspend fun updateText(id: Long, text: String, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<Note>

    @Query("UPDATE notes SET videoId = :to WHERE videoId = :from")
    suspend fun moveAll(from: Long, to: Long)
}
