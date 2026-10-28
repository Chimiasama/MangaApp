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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val aniListToken: String? = null,
    val aniListUsername: String? = null,
    val aniListAvatar: String? = null,
    val aniListAutoSync: Boolean = true,
    val isCheckingAniList: Boolean = false,
    val aniListErrorMessage: String? = null,
    val batterySaverEnabled: Boolean = false,
    val batterySaverDimPercent: Int = 0,
    val syncOnWifiOnly: Boolean = false,
    val connectionDiagnosticsEnabled: Boolean = false
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
    private val _isCheckingAniList = MutableStateFlow(false)
    private val _aniListErrorMessage = MutableStateFlow<String?>(null)

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
            _isCheckingAniList,
            _aniListErrorMessage,
            preferencesRepository.syncOnWifiOnly
        ) { args: Array<Any?> ->
            SettingsUiFlags(
                autoSync = args[0] as Boolean,
                rulesStatus = args[1] as? Boolean,
                cacheSize = args[2] as String,
                isCheckingAniList = args[3] as Boolean,
                aniListError = args[4] as? String,
                syncOnWifiOnly = args[5] as Boolean
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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
            val size = ImageCacheConfig.getDiskCacheSizeBytes(context)
            _cacheSize.value = ImageCacheConfig.formatSizeBytes(size)
        }
    }

    fun clearImageCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ImageCacheConfig.clearImageCache(context)
            val size = ImageCacheConfig.getDiskCacheSizeBytes(context)
            _cacheSize.value = ImageCacheConfig.formatSizeBytes(size)
        }
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
