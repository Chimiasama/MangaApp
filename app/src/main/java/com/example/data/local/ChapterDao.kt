package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE mangaUrl = :mangaUrl ORDER BY chapterNumber ASC")
    fun getChaptersForManga(mangaUrl: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaUrl = :mangaUrl ORDER BY chapterNumber ASC")
    suspend fun getChaptersForMangaSync(mangaUrl: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    fun getChapterById(chapterId: String): Flow<ChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET isRead = :isRead, lastPageRead = :lastPageRead, totalPages = :totalPages WHERE id = :chapterId")
    suspend fun updateChapterReadStatus(chapterId: String, isRead: Boolean, lastPageRead: Int, totalPages: Int)

    @Query("DELETE FROM chapters WHERE mangaUrl = :mangaUrl")
    suspend fun deleteChaptersForManga(mangaUrl: String)
}
