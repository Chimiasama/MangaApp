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
    val autoCloudSync: Boolean = true
)

private data class ReaderPrefs(
    val theme: String,
    val mode: String,
    val orientation: String,
    val fullscreen: Boolean
)

private data class SettingsUiFlags(
    val autoSync: Boolean,
    val filterAdultSearch: Boolean,
    val showModal: Boolean,
    val rulesStatus: Boolean?
)

class SettingsViewModel(
    private val repository: MangaRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _showAgeModal = MutableStateFlow(false)
    private val _rulesStatus = MutableStateFlow<Boolean?>(null)

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
                preferencesRepository.defaultFullscreenReader
            ) { theme, readerMode, readerOrientation, defaultFullscreen ->
                ReaderPrefs(theme, readerMode, readerOrientation, defaultFullscreen)
            }
        ) { (adultEnabled, ageVerified, customJson), reader ->
            Triple(Triple(adultEnabled, ageVerified, customJson ?: ""), reader, null)
        },
        combine(
            preferencesRepository.autoCloudSync,
            preferencesRepository.filterAdultContentInSearch,
            _showAgeModal,
            _rulesStatus
        ) { autoSync, filterAdultSearch, showModal, rulesStatus ->
            SettingsUiFlags(autoSync, filterAdultSearch, showModal, rulesStatus)
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
}
