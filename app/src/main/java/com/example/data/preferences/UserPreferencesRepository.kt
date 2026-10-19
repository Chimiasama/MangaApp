package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val ADULT_CONTENT_ENABLED = booleanPreferencesKey("adult_content_enabled")
        val AGE_VERIFIED = booleanPreferencesKey("age_verified")
        val CUSTOM_RULES_JSON = stringPreferencesKey("custom_rules_json")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_MODE = stringPreferencesKey("reader_mode")
        val READER_ORIENTATION = stringPreferencesKey("reader_orientation")
        val DEFAULT_FULLSCREEN_READER = booleanPreferencesKey("default_fullscreen_reader")
        val AUTO_CLOUD_SYNC = booleanPreferencesKey("auto_cloud_sync")
        val FILTER_ADULT_CONTENT_SEARCH = booleanPreferencesKey("filter_adult_content_search")
    }

    val adultContentEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ADULT_CONTENT_ENABLED] ?: false
    }

    val isAgeVerified: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AGE_VERIFIED] ?: false
    }

    val customRulesJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_RULES_JSON]
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "DARK"
    }

    val readerMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_MODE] ?: "WEBTOON"
    }

    val readerOrientation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_ORIENTATION] ?: "SENSOR"
    }

    val defaultFullscreenReader: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_FULLSCREEN_READER] ?: true
    }

    val autoCloudSync: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CLOUD_SYNC] ?: true
    }

    val filterAdultContentInSearch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FILTER_ADULT_CONTENT_SEARCH] ?: true
    }

    suspend fun setAdultContentEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ADULT_CONTENT_ENABLED] = enabled
        }
    }

    suspend fun setAgeVerified(verified: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AGE_VERIFIED] = verified
        }
    }

    suspend fun saveCustomRulesJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_RULES_JSON] = json
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    suspend fun setReaderMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[READER_MODE] = mode
        }
    }

    suspend fun setReaderOrientation(orientation: String) {
        context.dataStore.edit { prefs ->
            prefs[READER_ORIENTATION] = orientation
        }
    }

    suspend fun setDefaultFullscreenReader(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_FULLSCREEN_READER] = enabled
        }
    }

    suspend fun setAutoCloudSync(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_CLOUD_SYNC] = enabled
        }
    }

    suspend fun setFilterAdultContentInSearch(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FILTER_ADULT_CONTENT_SEARCH] = enabled
        }
    }
}
