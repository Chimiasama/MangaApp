package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.ProgressEntity
import com.example.data.preferences.UserPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class ProgressSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background reading progress sync via WorkManager...")
        val prefs = UserPreferencesRepository(applicationContext)
        val autoSync = prefs.autoCloudSync.first()
        if (!autoSync) {
            Log.d(TAG, "Auto cloud sync is disabled in settings. Skipping.")
            return Result.success()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val progressDao = db.progressDao()
            val mangaDao = db.mangaDao()
            val syncManager = FirestoreSyncManager(mangaDao, progressDao)

            val firestore = try {
                if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                    try {
                        com.google.firebase.FirebaseApp.initializeApp(applicationContext)
                    } catch (e: Throwable) {
                        Log.w(TAG, "FirebaseApp.initializeApp failed: ${e.message}")
                    }
                }
                FirebaseFirestore.getInstance()
            } catch (e: Throwable) {
                Log.d(TAG, "Firebase is not configured or initialized in this build. Skipping cloud sync.")
                return Result.success()
            }

            val targetMangaUrl = inputData.getString(KEY_MANGA_URL)
            val userId = syncManager.getSyncUserId()
            val userProgRef = firestore.collection("manga_sync").document(userId).collection("progress")

            // 1. Prioritize immediate sync of the specific active reading progress if requested
            if (!targetMangaUrl.isNullOrBlank()) {
                val specificProg = progressDao.getProgressForMangaSync(targetMangaUrl)
                if (specificProg != null) {
                    val docId = specificProg.mangaUrl.hashCode().toString()
                    val map = hashMapOf(
                        "mangaUrl" to specificProg.mangaUrl,
                        "chapterId" to specificProg.chapterId,
                        "chapterTitle" to specificProg.chapterTitle,
                        "chapterUrl" to specificProg.chapterUrl,
                        "lastPageRead" to specificProg.lastPageRead,
                        "totalPages" to specificProg.totalPages,
                        "scrollOffset" to specificProg.scrollOffset,
                        "timestamp" to specificProg.timestamp
                    )
                    userProgRef.document(docId).set(map, SetOptions.merge()).await()
                }
            }

            // 2. Push all local reading progress to Firestore
            val localList = progressDao.getAllProgressSync()
            for (prog in localList) {
                val docId = prog.mangaUrl.hashCode().toString()
                val map = hashMapOf(
                    "mangaUrl" to prog.mangaUrl,
                    "chapterId" to prog.chapterId,
                    "chapterTitle" to prog.chapterTitle,
                    "chapterUrl" to prog.chapterUrl,
                    "lastPageRead" to prog.lastPageRead,
                    "totalPages" to prog.totalPages,
                    "scrollOffset" to prog.scrollOffset,
                    "timestamp" to prog.timestamp
                )
                userProgRef.document(docId).set(map, SetOptions.merge()).await()
            }

            // 3. Pull remote reading progress from Firestore and merge if remote has newer progress
            val remoteDocs = userProgRef.get().await().documents
            for (doc in remoteDocs) {
                val mUrl = doc.getString("mangaUrl") ?: continue
                val chId = doc.getString("chapterId") ?: ""
                val chTitle = doc.getString("chapterTitle") ?: ""
                val chUrl = doc.getString("chapterUrl") ?: ""
                val lastPage = (doc.getLong("lastPageRead") ?: 0L).toInt()
                val totalPages = (doc.getLong("totalPages") ?: 0L).toInt()
                val scrollOffset = (doc.getLong("scrollOffset") ?: 0L).toInt()
                val timestamp = doc.getLong("timestamp") ?: 0L

                val localExisting = progressDao.getProgressForMangaSync(mUrl)
                if (localExisting == null || timestamp > localExisting.timestamp) {
                    progressDao.saveProgress(
                        ProgressEntity(
                            mangaUrl = mUrl,
                            chapterId = chId,
                            chapterTitle = chTitle,
                            chapterUrl = chUrl,
                            lastPageRead = lastPage,
                            totalPages = totalPages,
                            scrollOffset = scrollOffset,
                            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
                        )
                    )
                }
            }

            Log.d(TAG, "Background reading progress sync succeeded.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ProgressSyncWorker", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "ProgressSyncWorker"
        const val KEY_MANGA_URL = "extra_manga_url"
        const val KEY_CHAPTER_ID = "extra_chapter_id"
    }
}
