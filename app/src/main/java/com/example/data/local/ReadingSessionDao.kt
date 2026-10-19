package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Query("SELECT * FROM reading_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity)

    @Query("SELECT COUNT(DISTINCT chapterId) FROM reading_sessions")
    fun getTotalChaptersCount(): Flow<Int>

    @Query("SELECT SUM(pagesRead) FROM reading_sessions")
    fun getTotalPagesRead(): Flow<Int?>

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions")
    fun getTotalDurationSeconds(): Flow<Long?>

    @Query("DELETE FROM reading_sessions")
    suspend fun clearAllStats()
}
