package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ProgressSyncWorkHelper {

    const val PERIODIC_WORK_NAME = "manga_progress_periodic_sync"
    const val IMMEDIATE_WORK_NAME = "manga_progress_immediate_sync"

    fun schedulePeriodicProgressSync(
        context: Context,
        requiresWifiOnly: Boolean = false,
        requiresBatteryNotLow: Boolean = true
    ) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (requiresWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(requiresBatteryNotLow)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<ProgressSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(PERIODIC_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        } catch (e: Exception) {
            android.util.Log.e("ProgressSyncWorkHelper", "Failed to schedule periodic progress sync", e)
        }
    }

    fun triggerImmediateProgressSync(
        context: Context,
        mangaUrl: String? = null,
        chapterId: String? = null
    ) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder().apply {
                if (!mangaUrl.isNullOrBlank()) putString(ProgressSyncWorker.KEY_MANGA_URL, mangaUrl)
                if (!chapterId.isNullOrBlank()) putString(ProgressSyncWorker.KEY_CHAPTER_ID, chapterId)
            }.build()

            val workRequest = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(IMMEDIATE_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("ProgressSyncWorkHelper", "Failed to trigger immediate progress sync", e)
        }
    }
}
