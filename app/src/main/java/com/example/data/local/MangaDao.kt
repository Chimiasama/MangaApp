package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga_favorites WHERE isFavorite = 1 ORDER BY addedTimestamp DESC")
    fun getFavoriteMangas(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga_favorites WHERE isFavorite = 1")
    suspend fun getAllFavoritesSync(): List<MangaEntity>

    @Query("SELECT * FROM manga_favorites WHERE url = :url LIMIT 1")
    fun getMangaByUrl(url: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga_favorites WHERE url = :url LIMIT 1")
    suspend fun getMangaByUrlSync(url: String): MangaEntity?

    @Query("SELECT * FROM manga_favorites ORDER BY addedTimestamp DESC")
    suspend fun getAllCachedMangasSync(): List<MangaEntity>

    @Query("SELECT * FROM manga_favorites WHERE sourceId = :sourceId ORDER BY addedTimestamp DESC")
    suspend fun getCachedMangasBySourceSync(sourceId: String): List<MangaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(manga: MangaEntity) {
        insertManga(manga)
    }

    @Query("DELETE FROM manga_favorites WHERE url = :url")
    suspend fun deleteFavoriteByUrl(url: String)

    @Query("UPDATE manga_favorites SET isFavorite = :isFavorite WHERE url = :url")
    suspend fun updateFavoriteStatus(url: String, isFavorite: Boolean)
}
