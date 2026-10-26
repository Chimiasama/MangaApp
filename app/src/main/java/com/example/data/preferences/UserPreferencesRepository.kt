package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.floatPreferencesKey
import com.example.ui.reader.ReaderOptions
import com.example.ui.reader.ReaderViewport
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val steppedZoom = booleanPreferencesKey("reader_stepped_zoom")
    private val controlsTimeout = intPreferencesKey("reader_controls_timeout")
    private val preferredScale = floatPreferencesKey("reader_preferred_scale")

    val readerOptions: Flow<ReaderOptions> = context.dataStore.data.map { prefs ->
        ReaderOptions(prefs[steppedZoom] ?: false, (prefs[controlsTimeout] ?: 2).coerceIn(0, 15),
            (prefs[preferredScale] ?: 1f).coerceIn(1f, 4.5f))
    }

    suspend fun setReaderOptions(options: ReaderOptions) {
        context.dataStore.edit {
            it[steppedZoom] = options.steppedDoubleTap
            it[controlsTimeout] = options.controlsTimeoutSeconds.coerceIn(0, 15)
            it[preferredScale] = options.preferredScale.coerceIn(1f, 4.5f)
        }
    }

    private fun viewportKey(mangaUrl: String) = stringPreferencesKey("reader_viewport_" +
        java.security.MessageDigest.getInstance("SHA-256").digest(mangaUrl.toByteArray()).joinToString("") { "%02x".format(it) })

    suspend fun getReaderViewport(mangaUrl: String): ReaderViewport? {
        val json = context.dataStore.data.first()[viewportKey(mangaUrl)] ?: return null
        return runCatching {
        val value = JSONObject(json)
        ReaderViewport(value.getString("chapter"), value.getInt("page"),
            value.optDouble("offset", 0.0).toFloat().coerceIn(0f, 1f),
            value.optDouble("scale", 1.0).toFloat().coerceIn(1f, 4.5f),
            value.optDouble("x", 0.0).toFloat().coerceIn(-1f, 1f),
            value.optDouble("y", 0.0).toFloat().coerceIn(-1f, 1f),
            value.optString("mode", "WEBTOON"))
        }.getOrNull()
    }

    suspend fun saveReaderViewport(mangaUrl: String, viewport: ReaderViewport) {
        val json = JSONObject().put("chapter", viewport.chapterId).put("page", viewport.pageIndex)
            .put("offset", viewport.offsetFraction).put("scale", viewport.scale)
            .put("x", viewport.panXFraction).put("y", viewport.panYFraction).put("mode", viewport.mode)
        context.dataStore.edit {
            it[viewportKey(mangaUrl)] = json.toString()
            it[preferredScale] = viewport.scale.coerceIn(1f, 4.5f)
        }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "pt-br"
        val CUSTOM_RULES_JSON = stringPreferencesKey("custom_rules_json")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_MODE = stringPreferencesKey("reader_mode")
        val READER_ORIENTATION = stringPreferencesKey("reader_orientation")
        val DEFAULT_FULLSCREEN_READER = booleanPreferencesKey("default_fullscreen_reader")
        val AUTO_CLOUD_SYNC = booleanPreferencesKey("auto_cloud_sync")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val READER_BACKGROUND_COLOR = stringPreferencesKey("reader_background_color")
        val READER_INVERT_COLORS = booleanPreferencesKey("reader_invert_colors")
        val PAGE_PRELOAD_ENABLED = booleanPreferencesKey("page_preload_enabled")
        val ANILIST_TOKEN = stringPreferencesKey("anilist_token")
        val ANILIST_USERNAME = stringPreferencesKey("anilist_username")
        val ANILIST_AVATAR = stringPreferencesKey("anilist_avatar")
        val ANILIST_AUTO_SYNC = booleanPreferencesKey("anilist_auto_sync")
        val BATTERY_SAVER_ENABLED = booleanPreferencesKey("battery_saver_enabled")
        val BATTERY_SAVER_DIM_PERCENT = intPreferencesKey("battery_saver_dim_percent")
        val SYNC_ON_WIFI_ONLY = booleanPreferencesKey("sync_on_wifi_only")
    }

    val selectedLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_LANGUAGE] ?: DEFAULT_LANGUAGE
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

    val readerBackgroundColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_BACKGROUND_COLOR] ?: "OLED"
    }

    val readerInvertColors: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_INVERT_COLORS] ?: false
    }

    val pagePreloadEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PAGE_PRELOAD_ENABLED] ?: true
    }

    val aniListToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ANILIST_TOKEN]
    }

    val aniListUsername: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ANILIST_USERNAME]
    }

    val aniListAvatar: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ANILIST_AVATAR]
    }

    val aniListAutoSync: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ANILIST_AUTO_SYNC] ?: true
    }

    val batterySaverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BATTERY_SAVER_ENABLED] ?: false
    }

    val batterySaverDimPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BATTERY_SAVER_DIM_PERCENT] ?: 0
    }

    val syncOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SYNC_ON_WIFI_ONLY] ?: false
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

    suspend fun setSelectedLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_LANGUAGE] = lang
        }
    }

    suspend fun setReaderBackgroundColor(colorKey: String) {
        context.dataStore.edit { prefs ->
            prefs[READER_BACKGROUND_COLOR] = colorKey
        }
    }

    suspend fun setReaderInvertColors(inverted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_INVERT_COLORS] = inverted
        }
    }

    suspend fun setPagePreloadEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PAGE_PRELOAD_ENABLED] = enabled
        }
    }

    suspend fun saveAniListAuth(token: String, username: String, avatarUrl: String?) {
        context.dataStore.edit { prefs ->
            prefs[ANILIST_TOKEN] = token
            prefs[ANILIST_USERNAME] = username
            if (avatarUrl != null) {
                prefs[ANILIST_AVATAR] = avatarUrl
            } else {
                prefs.remove(ANILIST_AVATAR)
            }
        }
    }

    suspend fun clearAniListAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(ANILIST_TOKEN)
            prefs.remove(ANILIST_USERNAME)
            prefs.remove(ANILIST_AVATAR)
        }
    }

    suspend fun setAniListAutoSync(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ANILIST_AUTO_SYNC] = enabled
        }
    }

    suspend fun setBatterySaverEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BATTERY_SAVER_ENABLED] = enabled
        }
    }

    suspend fun setBatterySaverDimPercent(percent: Int) {
        context.dataStore.edit { prefs ->
            prefs[BATTERY_SAVER_DIM_PERCENT] = percent.coerceIn(0, 60)
        }
    }

    suspend fun setSyncOnWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SYNC_ON_WIFI_ONLY] = wifiOnly
        }
    }
}
