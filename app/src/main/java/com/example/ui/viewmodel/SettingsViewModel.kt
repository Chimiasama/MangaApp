package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.util.ImageCacheConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Available(val info: com.example.data.updater.AppReleaseInfo) : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

data class SettingsUiState(
    val customRulesJson: String = "",
    val rulesUpdateSuccess: Boolean? = null,
    val themeMode: String = "DARK",
    val readerMode: String = "WEBTOON",
    val readerOrientation: String = "SENSOR",
    val defaultFullscreenReader: Boolean = true,
    val autoCloudSync: Boolean = true,
    val readerBackgroundColor: String = "OLED",
    val readerInvertColors: Boolean = false,
    val pagePreloadEnabled: Boolean = true,
    val cacheSizeFormatted: String = "0 B",
    val downloadsSizeFormatted: String = "0 B",
    val totalStorageFormatted: String = "0 B",
    val autoCleanCacheEnabled: Boolean = true,
    val autoCleanCacheDays: Int = 3,
    val autoCleanDownloadsEnabled: Boolean = true,
    val keepFavoritesDownloads: Boolean = true,
    val aniListToken: String? = null,
    val aniListUsername: String? = null,
    val aniListAvatar: String? = null,
    val aniListAutoSync: Boolean = true,
    val isCheckingAniList: Boolean = false,
    val aniListErrorMessage: String? = null,
    val batterySaverEnabled: Boolean = false,
    val batterySaverDimPercent: Int = 0,
    val syncOnWifiOnly: Boolean = false,
    val connectionDiagnosticsEnabled: Boolean = false,
    val homeSections: List<com.example.home.HomeSection> = com.example.home.HomeSections.defaults,
    val nsfwEnabled: Boolean = false,
    val githubRepo: String = com.example.data.updater.AppUpdateManager.DEFAULT_REPO,
    val updateUiState: UpdateUiState = UpdateUiState.Idle
)

private data class ReaderPrefs(
    val theme: String,
    val mode: String,
    val orientation: String,
    val fullscreen: Boolean,
    val bgColor: String,
    val invertColors: Boolean,
    val preloadEnabled: Boolean,
    val batterySaverEnabled: Boolean,
    val batterySaverDimPercent: Int
)

private data class AniListPrefs(
    val token: String?,
    val username: String?,
    val avatar: String?,
    val autoSync: Boolean
)

private data class SettingsUiFlags(
    val autoSync: Boolean,
    val rulesStatus: Boolean?,
    val cacheSize: String,
    val downloadsSize: String,
    val totalStorage: String,
    val isCheckingAniList: Boolean,
    val aniListError: String?,
    val syncOnWifiOnly: Boolean
)

class SettingsViewModel(
    private val repository: MangaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val aniListSyncManager: com.example.data.sync.AniListSyncManager = com.example.data.sync.AniListSyncManager()
) : ViewModel() {

    private val _rulesStatus = MutableStateFlow<Boolean?>(null)
    private val _cacheSize = MutableStateFlow("0 B")
    private val _downloadsSize = MutableStateFlow("0 B")
    private val _totalStorage = MutableStateFlow("0 B")
    private val _isCheckingAniList = MutableStateFlow(false)
    private val _aniListErrorMessage = MutableStateFlow<String?>(null)
    private val _updateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesRepository.customRulesJson,
            combine(
                preferencesRepository.themeMode,
                preferencesRepository.readerMode,
                preferencesRepository.readerOrientation,
                preferencesRepository.defaultFullscreenReader,
                preferencesRepository.readerBackgroundColor,
                preferencesRepository.readerInvertColors,
                preferencesRepository.pagePreloadEnabled,
                preferencesRepository.batterySaverEnabled,
                preferencesRepository.batterySaverDimPercent
            ) { args: Array<Any> ->
                ReaderPrefs(
                    theme = args[0] as String,
                    mode = args[1] as String,
                    orientation = args[2] as String,
                    fullscreen = args[3] as Boolean,
                    bgColor = args[4] as String,
                    invertColors = args[5] as Boolean,
                    preloadEnabled = args[6] as Boolean,
                    batterySaverEnabled = args[7] as Boolean,
                    batterySaverDimPercent = args[8] as Int
                )
            },
            combine(
                preferencesRepository.aniListToken,
                preferencesRepository.aniListUsername,
                preferencesRepository.aniListAvatar,
                preferencesRepository.aniListAutoSync
            ) { token, username, avatar, autoSync ->
                AniListPrefs(token, username, avatar, autoSync)
            }
        ) { customJson, reader, anilist ->
            Triple(customJson ?: "", reader, anilist)
        },
        combine(
            preferencesRepository.autoCloudSync,
            _rulesStatus,
            _cacheSize,
            _downloadsSize,
            _totalStorage,
            _isCheckingAniList,
            _aniListErrorMessage,
            preferencesRepository.syncOnWifiOnly
        ) { args: Array<Any?> ->
            SettingsUiFlags(
                autoSync = args[0] as Boolean,
                rulesStatus = args[1] as? Boolean,
                cacheSize = args[2] as String,
                downloadsSize = args[3] as String,
                totalStorage = args[4] as String,
                isCheckingAniList = args[5] as Boolean,
                aniListError = args[6] as? String,
                syncOnWifiOnly = args[7] as Boolean
            )
        }
    ) { (customJson, reader, anilist), flags ->
        SettingsUiState(
            customRulesJson = customJson,
            themeMode = reader.theme,
            readerMode = reader.mode,
            readerOrientation = reader.orientation,
            defaultFullscreenReader = reader.fullscreen,
            readerBackgroundColor = reader.bgColor,
            readerInvertColors = reader.invertColors,
            pagePreloadEnabled = reader.preloadEnabled,
            cacheSizeFormatted = flags.cacheSize,
            downloadsSizeFormatted = flags.downloadsSize,
            totalStorageFormatted = flags.totalStorage,
            autoCloudSync = flags.autoSync,
            rulesUpdateSuccess = flags.rulesStatus,
            aniListToken = anilist.token,
            aniListUsername = anilist.username,
            aniListAvatar = anilist.avatar,
            aniListAutoSync = anilist.autoSync,
            isCheckingAniList = flags.isCheckingAniList,
            aniListErrorMessage = flags.aniListError,
            batterySaverEnabled = reader.batterySaverEnabled,
            batterySaverDimPercent = reader.batterySaverDimPercent,
            syncOnWifiOnly = flags.syncOnWifiOnly
        )
    }.combine(preferencesRepository.connectionDiagnosticsEnabled) { state, enabled ->
        state.copy(connectionDiagnosticsEnabled = enabled)
    }.combine(preferencesRepository.homeSections) { state, sections -> state.copy(homeSections = sections)
    }.combine(preferencesRepository.nsfwEnabled) { state, nsfw -> state.copy(nsfwEnabled = nsfw)
    }.combine(preferencesRepository.autoCleanCacheEnabled) { state, enabled -> state.copy(autoCleanCacheEnabled = enabled)
    }.combine(preferencesRepository.autoCleanCacheDays) { state, days -> state.copy(autoCleanCacheDays = days)
    }.combine(preferencesRepository.autoCleanDownloadsEnabled) { state, enabled -> state.copy(autoCleanDownloadsEnabled = enabled)
    }.combine(preferencesRepository.keepFavoritesDownloads) { state, keep -> state.copy(keepFavoritesDownloads = keep)
    }.combine(preferencesRepository.githubRepo) { state, repo -> state.copy(githubRepo = repo)
    }.combine(_updateUiState) { state, updateState -> state.copy(updateUiState = updateState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun checkForUpdates(repoOverride: String? = null) {
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Checking
            val targetRepo = repoOverride?.trim()?.ifBlank { null }
                ?: preferencesRepository.githubRepo.first()
            val result = com.example.data.updater.AppUpdateManager.checkForUpdates(targetRepo)
            result.onSuccess { release ->
                if (release.isUpdateAvailable) {
                    _updateUiState.value = UpdateUiState.Available(release)
                } else {
                    _updateUiState.value = UpdateUiState.UpToDate
                }
            }.onFailure { error ->
                _updateUiState.value = UpdateUiState.Error(error.message ?: "Falha ao verificar atualizações no GitHub.")
            }
        }
    }

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            preferencesRepository.setGithubRepo(repo)
        }
    }

    fun clearUpdateState() {
        _updateUiState.value = UpdateUiState.Idle
    }

    fun setNsfwEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNsfwEnabled(enabled) }
    }

    fun setHomeSections(sections: List<com.example.home.HomeSection>) {
        viewModelScope.launch { preferencesRepository.setHomeSections(sections) }
    }

    fun setConnectionDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setConnectionDiagnosticsEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setReaderMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderMode(mode)
        }
    }

    fun setReaderOrientation(orientation: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderOrientation(orientation)
        }
    }

    fun setDefaultFullscreenReader(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDefaultFullscreenReader(enabled)
        }
    }

    fun setAutoCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCloudSync(enabled)
        }
    }

    fun importScraperRulesJson(json: String) {
        viewModelScope.launch {
            val success = repository.loadScraperRulesFromJson(json)
            if (success) {
                preferencesRepository.saveCustomRulesJson(json)
                _rulesStatus.value = true
            } else {
                _rulesStatus.value = false
            }
        }
    }

    fun resetRulesStatus() {
        _rulesStatus.value = null
    }

    fun refreshCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = com.example.util.CacheCleanupManager.getStorageStats(context)
            _cacheSize.value = stats.pageCacheFormatted
            _downloadsSize.value = stats.downloadsFormatted
            _totalStorage.value = stats.totalFormatted
        }
    }

    fun clearImageCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.util.CacheCleanupManager.clearAllPageCache(context)
            refreshCacheSize(context)
        }
    }

    fun clearOldDownloadsAndCache(context: Context, days: Int = 3) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.util.CacheCleanupManager.purgeOldDiskCache(context, days)
            val protect = preferencesRepository.keepFavoritesDownloads.first()
            com.example.util.CacheCleanupManager.purgeOldDownloads(context, repository, days, protect)
            refreshCacheSize(context)
        }
    }

    fun clearAllDownloads(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.util.CacheCleanupManager.clearAllDownloads(context, repository)
            refreshCacheSize(context)
        }
    }

    fun setAutoCleanCacheEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCleanCacheEnabled(enabled) }
    }

    fun setAutoCleanCacheDays(days: Int) {
        viewModelScope.launch { preferencesRepository.setAutoCleanCacheDays(days) }
    }

    fun setAutoCleanDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCleanDownloadsEnabled(enabled) }
    }

    fun setKeepFavoritesDownloads(keep: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepFavoritesDownloads(keep) }
    }

    fun setReaderBackgroundColor(colorKey: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderBackgroundColor(colorKey)
        }
    }

    fun setReaderInvertColors(inverted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReaderInvertColors(inverted)
        }
    }

    fun setPagePreloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPagePreloadEnabled(enabled)
        }
    }

    fun connectAniList(token: String) {
        val cleanToken = token.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) {
            _aniListErrorMessage.value = "O token não pode ser vazio."
            return
        }
        viewModelScope.launch {
            _isCheckingAniList.value = true
            _aniListErrorMessage.value = null
            val result = aniListSyncManager.validateToken(cleanToken)
            result.onSuccess { viewer ->
                preferencesRepository.saveAniListAuth(
                    token = cleanToken,
                    username = viewer.name,
                    avatarUrl = viewer.avatar?.large ?: viewer.avatar?.medium
                )
                _isCheckingAniList.value = false
            }.onFailure { ex ->
                _aniListErrorMessage.value = ex.message ?: "Falha ao validar conta AniList"
                _isCheckingAniList.value = false
            }
        }
    }

    fun disconnectAniList() {
        viewModelScope.launch {
            preferencesRepository.clearAniListAuth()
            _aniListErrorMessage.value = null
        }
    }

    fun setAniListAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAniListAutoSync(enabled)
        }
    }

    fun clearAniListError() {
        _aniListErrorMessage.value = null
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBatterySaverEnabled(enabled)
        }
    }

    fun setBatterySaverDimPercent(percent: Int) {
        viewModelScope.launch {
            preferencesRepository.setBatterySaverDimPercent(percent)
        }
    }

    fun setSyncOnWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncOnWifiOnly(wifiOnly)
        }
    }
}
