package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT chapterId FROM read_chapters WHERE mangaUrl = :mangaUrl AND sourceId = :sourceId")
    fun getReadChapterIds(mangaUrl: String, sourceId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markChapterRead(chapter: ReadChapterEntity)


    @Query("SELECT * FROM reading_progress ORDER BY timestamp DESC")
    fun getAllProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllProgressSync(): List<ProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE mangaUrl = :mangaUrl LIMIT 1")
    fun getProgressForManga(mangaUrl: String): Flow<ProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE mangaUrl = :mangaUrl LIMIT 1")
    suspend fun getProgressForMangaSync(mangaUrl: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)

    @Query("DELETE FROM reading_progress WHERE mangaUrl = :mangaUrl")
    suspend fun deleteProgress(mangaUrl: String)
}
