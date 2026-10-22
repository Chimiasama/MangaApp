package com.example.data.sync

import android.util.Log
import com.example.data.local.MangaDao
import com.example.data.local.MangaEntity
import com.example.data.local.ProgressDao
import com.example.data.local.ProgressEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class FirestoreSyncManager(
    private val mangaDao: MangaDao,
    private val progressDao: ProgressDao
) {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _syncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle())
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private var customSyncKey: String? = null

    suspend fun getSyncUserId(): String = withContext(Dispatchers.IO) {
        if (!customSyncKey.isNullOrBlank()) {
            return@withContext customSyncKey!!
        }
        val currentUser = auth.currentUser
        if (currentUser != null) {
            return@withContext currentUser.uid
        }
        try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user
            if (user != null) {
                return@withContext user.uid
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error signing in anonymously to Firebase", e)
        }
        return@withContext "guest_device_${UUID.randomUUID().toString().take(8)}"
    }

    fun setCustomSyncKey(key: String) {
        customSyncKey = if (key.isBlank()) null else key.trim()
    }

    suspend fun performFullSync(
        localFavorites: List<MangaEntity>,
        localProgress: List<ProgressEntity>
    ): CloudSyncState = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        try {
            val userId = getSyncUserId()
            val userFavRef = firestore.collection("manga_sync").document(userId).collection("favorites")
            val userProgRef = firestore.collection("manga_sync").document(userId).collection("progress")

            // 1. Push Local Favorites to Firestore
            for (fav in localFavorites) {
                val docId = fav.url.hashCode().toString()
                val favMap = hashMapOf(
                    "url" to fav.url,
                    "id" to fav.id,
                    "title" to fav.title,
                    "coverUrl" to fav.coverUrl,
                    "description" to fav.description,
                    "sourceId" to fav.sourceId,
                    "genres" to fav.genres,
                    "isFavorite" to fav.isFavorite,
                    "isNsfw" to fav.isNsfw,
                    "addedTimestamp" to fav.addedTimestamp
                )
                userFavRef.document(docId).set(favMap, SetOptions.merge()).await()
            }

            // 2. Push Local Reading Progress to Firestore
            for (prog in localProgress) {
                val docId = prog.mangaUrl.hashCode().toString()
                val progMap = hashMapOf(
                    "mangaUrl" to prog.mangaUrl,
                    "chapterId" to prog.chapterId,
                    "chapterTitle" to prog.chapterTitle,
                    "chapterUrl" to prog.chapterUrl,
                    "lastPageRead" to prog.lastPageRead,
                    "totalPages" to prog.totalPages,
                    "timestamp" to prog.timestamp
                )
                userProgRef.document(docId).set(progMap, SetOptions.merge()).await()
            }

            // 3. Pull Remote Favorites from Firestore and Restore/Merge locally
            val remoteFavSnapshot = userFavRef.get().await()
            for (doc in remoteFavSnapshot.documents) {
                val url = doc.getString("url") ?: continue
                val id = doc.getString("id") ?: url.hashCode().toString()
                val title = doc.getString("title") ?: ""
                val coverUrl = doc.getString("coverUrl") ?: ""
                val description = doc.getString("description") ?: ""
                val sourceId = doc.getString("sourceId") ?: "mangadex"
                val genres = doc.getString("genres") ?: ""
                val isFavorite = doc.getBoolean("isFavorite") ?: true
                val isNsfw = doc.getBoolean("isNsfw") ?: false
                val addedTimestamp = doc.getLong("addedTimestamp") ?: System.currentTimeMillis()

                val remoteEntity = MangaEntity(
                    url = url,
                    id = id,
                    title = title,
                    coverUrl = coverUrl,
                    description = description,
                    sourceId = sourceId,
                    genres = genres,
                    isFavorite = isFavorite,
                    isNsfw = isNsfw,
                    addedTimestamp = addedTimestamp
                )
                mangaDao.insertFavorite(remoteEntity)
            }

            // 4. Pull Remote Reading Progress from Firestore and Restore/Merge locally
            val remoteProgSnapshot = userProgRef.get().await()
            for (doc in remoteProgSnapshot.documents) {
                val mangaUrl = doc.getString("mangaUrl") ?: continue
                val chapterId = doc.getString("chapterId") ?: ""
                val chapterTitle = doc.getString("chapterTitle") ?: ""
                val chapterUrl = doc.getString("chapterUrl") ?: ""
                val lastPageRead = (doc.getLong("lastPageRead") ?: 0L).toInt()
                val totalPages = (doc.getLong("totalPages") ?: 0L).toInt()
                val scrollOffset = (doc.getLong("scrollOffset") ?: 0L).toInt()
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                val remoteProgEntity = ProgressEntity(
                    mangaUrl = mangaUrl,
                    chapterId = chapterId,
                    chapterTitle = chapterTitle,
                    chapterUrl = chapterUrl,
                    lastPageRead = lastPageRead,
                    totalPages = totalPages,
                    scrollOffset = scrollOffset,
                    timestamp = timestamp
                )
                progressDao.saveProgress(remoteProgEntity)
            }

            val timestamp = System.currentTimeMillis()
            val successState = CloudSyncState.Success(
                message = "Sincronização com Firebase Firestore concluída!",
                lastSyncTimestamp = timestamp,
                cloudUserId = userId
            )
            _syncState.value = successState
            return@withContext successState
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error syncing with Firestore", e)
            val errorMsg = e.localizedMessage ?: "Falha ao conectar com o Firebase Firestore."
            val errorState = CloudSyncState.Error(
                errorMessage = errorMsg,
                cloudUserId = getSyncUserId()
            )
            _syncState.value = errorState
            return@withContext errorState
        }
    }

    suspend fun backupOnly(
        localFavorites: List<MangaEntity>,
        localProgress: List<ProgressEntity>
    ): CloudSyncState = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        try {
            val userId = getSyncUserId()
            val userFavRef = firestore.collection("manga_sync").document(userId).collection("favorites")
            val userProgRef = firestore.collection("manga_sync").document(userId).collection("progress")

            for (fav in localFavorites) {
                val docId = fav.url.hashCode().toString()
                val favMap = hashMapOf(
                    "url" to fav.url,
                    "id" to fav.id,
                    "title" to fav.title,
                    "coverUrl" to fav.coverUrl,
                    "description" to fav.description,
                    "sourceId" to fav.sourceId,
                    "genres" to fav.genres,
                    "isFavorite" to fav.isFavorite,
                    "isNsfw" to fav.isNsfw,
                    "addedTimestamp" to fav.addedTimestamp
                )
                userFavRef.document(docId).set(favMap, SetOptions.merge()).await()
            }

            for (prog in localProgress) {
                val docId = prog.mangaUrl.hashCode().toString()
                val progMap = hashMapOf(
                    "mangaUrl" to prog.mangaUrl,
                    "chapterId" to prog.chapterId,
                    "chapterTitle" to prog.chapterTitle,
                    "chapterUrl" to prog.chapterUrl,
                    "lastPageRead" to prog.lastPageRead,
                    "totalPages" to prog.totalPages,
                    "scrollOffset" to prog.scrollOffset,
                    "timestamp" to prog.timestamp
                )
                userProgRef.document(docId).set(progMap, SetOptions.merge()).await()
            }

            val timestamp = System.currentTimeMillis()
            val state = CloudSyncState.Success(
                message = "Backup enviado para o Firestore com sucesso!",
                lastSyncTimestamp = timestamp,
                cloudUserId = userId
            )
            _syncState.value = state
            return@withContext state
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Erro ao realizar backup no Firestore."
            val state = CloudSyncState.Error(errorMessage = errorMsg, cloudUserId = getSyncUserId())
            _syncState.value = state
            return@withContext state
        }
    }

    suspend fun restoreOnly(): CloudSyncState = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        try {
            val userId = getSyncUserId()
            val userFavRef = firestore.collection("manga_sync").document(userId).collection("favorites")
            val userProgRef = firestore.collection("manga_sync").document(userId).collection("progress")

            val remoteFavSnapshot = userFavRef.get().await()
            var restoredFavs = 0
            for (doc in remoteFavSnapshot.documents) {
                val url = doc.getString("url") ?: continue
                val id = doc.getString("id") ?: url.hashCode().toString()
                val title = doc.getString("title") ?: ""
                val coverUrl = doc.getString("coverUrl") ?: ""
                val description = doc.getString("description") ?: ""
                val sourceId = doc.getString("sourceId") ?: "mangadex"
                val genres = doc.getString("genres") ?: ""
                val isFavorite = doc.getBoolean("isFavorite") ?: true
                val isNsfw = doc.getBoolean("isNsfw") ?: false
                val addedTimestamp = doc.getLong("addedTimestamp") ?: System.currentTimeMillis()

                mangaDao.insertFavorite(
                    MangaEntity(
                        url = url,
                        id = id,
                        title = title,
                        coverUrl = coverUrl,
                        description = description,
                        sourceId = sourceId,
                        genres = genres,
                        isFavorite = isFavorite,
                        isNsfw = isNsfw,
                        addedTimestamp = addedTimestamp
                    )
                )
                restoredFavs++
            }

            val remoteProgSnapshot = userProgRef.get().await()
            var restoredProgs = 0
            for (doc in remoteProgSnapshot.documents) {
                val mangaUrl = doc.getString("mangaUrl") ?: continue
                val chapterId = doc.getString("chapterId") ?: ""
                val chapterTitle = doc.getString("chapterTitle") ?: ""
                val chapterUrl = doc.getString("chapterUrl") ?: ""
                val lastPageRead = (doc.getLong("lastPageRead") ?: 0L).toInt()
                val totalPages = (doc.getLong("totalPages") ?: 0L).toInt()
                val scrollOffset = (doc.getLong("scrollOffset") ?: 0L).toInt()
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                progressDao.saveProgress(
                    ProgressEntity(
                        mangaUrl = mangaUrl,
                        chapterId = chapterId,
                        chapterTitle = chapterTitle,
                        chapterUrl = chapterUrl,
                        lastPageRead = lastPageRead,
                        totalPages = totalPages,
                        scrollOffset = scrollOffset,
                        timestamp = timestamp
                    )
                )
                restoredProgs++
            }

            val timestamp = System.currentTimeMillis()
            val state = CloudSyncState.Success(
                message = "Restauração concluída: $restoredFavs favoritos e $restoredProgs históricos restaurados do Firestore.",
                lastSyncTimestamp = timestamp,
                cloudUserId = userId
            )
            _syncState.value = state
            return@withContext state
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Erro ao restaurar dados do Firestore."
            val state = CloudSyncState.Error(errorMessage = errorMsg, cloudUserId = getSyncUserId())
            _syncState.value = state
            return@withContext state
        }
    }
}
