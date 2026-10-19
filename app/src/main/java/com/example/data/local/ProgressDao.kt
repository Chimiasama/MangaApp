package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM reading_progress ORDER BY timestamp DESC")
    fun getAllProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllProgressSync(): List<ProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE mangaUrl = :mangaUrl LIMIT 1")
    fun getProgressForManga(mangaUrl: String): Flow<ProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)

    @Query("DELETE FROM reading_progress WHERE mangaUrl = :mangaUrl")
    suspend fun deleteProgress(mangaUrl: String)
}
