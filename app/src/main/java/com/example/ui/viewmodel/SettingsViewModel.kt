package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.content.Context
import com.example.util.ImageCacheConfig
import kotlinx.coroutines.Dispatchers

data class SettingsUiState(
    val adultContentEnabled: Boolean = false,
    val filterAdultContentInSearch: Boolean = true,
    val isAgeVerified: Boolean = false,
    val showAgeModal: Boolean = false,
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
    val filterAdultSearch: Boolean,
    val showModal: Boolean,
    val rulesStatus: Boolean?,
    val cacheSize: String
)

class SettingsViewModel(
    private val repository: MangaRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _showAgeModal = MutableStateFlow(false)
    private val _rulesStatus = MutableStateFlow<Boolean?>(null)
    private val _cacheSize = MutableStateFlow("0 B")

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            combine(
                preferencesRepository.adultContentEnabled,
                preferencesRepository.isAgeVerified,
                preferencesRepository.customRulesJson
            ) { adultEnabled, ageVerified, customJson ->
                Triple(adultEnabled, ageVerified, customJson)
            },
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
        ) { (adultEnabled, ageVerified, customJson), reader ->
            Triple(Triple(adultEnabled, ageVerified, customJson ?: ""), reader, null)
        },
        combine(
            preferencesRepository.autoCloudSync,
            preferencesRepository.filterAdultContentInSearch,
            _showAgeModal,
            _rulesStatus,
            _cacheSize
        ) { autoSync, filterAdultSearch, showModal, rulesStatus, cacheSize ->
            SettingsUiFlags(autoSync, filterAdultSearch, showModal, rulesStatus, cacheSize)
        }
    ) { (account, reader, _), flags ->
        SettingsUiState(
            adultContentEnabled = account.first,
            filterAdultContentInSearch = flags.filterAdultSearch,
            isAgeVerified = account.second,
            customRulesJson = account.third,
            themeMode = reader.theme,
            readerMode = reader.mode,
            readerOrientation = reader.orientation,
            defaultFullscreenReader = reader.fullscreen,
            readerBackgroundColor = reader.bgColor,
            readerInvertColors = reader.invertColors,
            pagePreloadEnabled = reader.preloadEnabled,
            cacheSizeFormatted = flags.cacheSize,
            autoCloudSync = flags.autoSync,
            showAgeModal = flags.showModal,
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

    fun setFilterAdultContentInSearch(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFilterAdultContentInSearch(enabled)
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

    fun onAdultToggleClicked(targetEnabled: Boolean) {
        viewModelScope.launch {
            if (targetEnabled) {
                // Check if user is already age verified
                val isVerified = uiState.value.isAgeVerified
                if (isVerified) {
                    preferencesRepository.setAdultContentEnabled(true)
                } else {
                    // Trigger age verification modal dialog
                    _showAgeModal.value = true
                }
            } else {
                preferencesRepository.setAdultContentEnabled(false)
            }
        }
    }

    fun confirmAgeVerification() {
        viewModelScope.launch {
            preferencesRepository.setAgeVerified(true)
            preferencesRepository.setAdultContentEnabled(true)
            _showAgeModal.value = false
        }
    }

    fun dismissAgeVerificationModal() {
        _showAgeModal.value = false
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
