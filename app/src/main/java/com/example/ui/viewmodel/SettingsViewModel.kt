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
    val cacheSizeFormatted: String = "0 B"
)

private data class ReaderPrefs(
    val theme: String,
    val mode: String,
    val orientation: String,
    val fullscreen: Boolean,
    val bgColor: String,
    val invertColors: Boolean,
    val preloadEnabled: Boolean
)

private data class SettingsUiFlags(
    val autoSync: Boolean,
    val rulesStatus: Boolean?,
    val cacheSize: String
)

class SettingsViewModel(
    private val repository: MangaRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _rulesStatus = MutableStateFlow<Boolean?>(null)
    private val _cacheSize = MutableStateFlow("0 B")

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
                preferencesRepository.pagePreloadEnabled
            ) { args: Array<Any> ->
                ReaderPrefs(
                    theme = args[0] as String,
                    mode = args[1] as String,
                    orientation = args[2] as String,
                    fullscreen = args[3] as Boolean,
                    bgColor = args[4] as String,
                    invertColors = args[5] as Boolean,
                    preloadEnabled = args[6] as Boolean
                )
            }
        ) { customJson, reader ->
            Pair(customJson ?: "", reader)
        },
        combine(
            preferencesRepository.autoCloudSync,
            _rulesStatus,
            _cacheSize
        ) { autoSync, rulesStatus, cacheSize ->
            SettingsUiFlags(autoSync, rulesStatus, cacheSize)
        }
    ) { (customJson, reader), flags ->
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
            rulesUpdateSuccess = flags.rulesStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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
}
