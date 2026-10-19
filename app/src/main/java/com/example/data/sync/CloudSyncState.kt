package com.example.data.sync

sealed class CloudSyncState {
    data class Idle(
        val lastSyncTimestamp: Long = 0,
        val autoSyncEnabled: Boolean = true,
        val cloudUserId: String = ""
    ) : CloudSyncState()

    object Syncing : CloudSyncState()

    data class Success(
        val message: String,
        val lastSyncTimestamp: Long = System.currentTimeMillis(),
        val cloudUserId: String = ""
    ) : CloudSyncState()

    data class Error(
        val errorMessage: String,
        val cloudUserId: String = ""
    ) : CloudSyncState()
}
