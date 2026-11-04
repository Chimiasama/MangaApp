package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val readerBarSize = stringPreferencesKey("reader_bar_size")
    private val readerSliderPosition = stringPreferencesKey("reader_slider_position")
    private val readerExtraInfo = booleanPreferencesKey("reader_extra_info")
    private val preferredScale = floatPreferencesKey("reader_preferred_scale")

    val readerOptions: Flow<ReaderOptions> = context.dataStore.data.map { prefs ->
        ReaderOptions(prefs[steppedZoom] ?: false, (prefs[controlsTimeout] ?: 3).coerceIn(0, 15),
            (prefs[preferredScale] ?: 1f).coerceIn(1f, 4.5f),
            prefs[readerBarSize]?.takeIf { it in setOf("compact", "normal") } ?: "compact",
            prefs[readerSliderPosition]?.takeIf { it in setOf("bottom", "top") } ?: "bottom",
            prefs[readerExtraInfo] ?: false)
    }

    suspend fun setReaderOptions(options: ReaderOptions) {
        context.dataStore.edit {
            it[steppedZoom] = options.steppedDoubleTap
            it[controlsTimeout] = options.controlsTimeoutSeconds.coerceIn(0, 15)
            it[preferredScale] = options.preferredScale.coerceIn(1f, 4.5f)
            it[readerBarSize] = options.barSize
            it[readerSliderPosition] = options.sliderPosition
            it[readerExtraInfo] = options.showExtraInfo
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

    val homeSections: Flow<List<com.example.home.HomeSection>> = context.dataStore.data.map {
        com.example.home.HomeSections.decode(it[HOME_SECTIONS])
    }.distinctUntilChanged()
    suspend fun setHomeSections(sections: List<com.example.home.HomeSection>) {
        context.dataStore.edit { it[HOME_SECTIONS] = com.example.home.HomeSections.encode(sections) }
    }

    companion object {
        val HOME_SECTIONS = stringPreferencesKey("home_sections_v1")
        val CONNECTION_DIAGNOSTICS = booleanPreferencesKey("connection_diagnostics")
        const val DEFAULT_LANGUAGE = "pt-br"
        val CUSTOM_RULES_JSON = stringPreferencesKey("custom_rules_json")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_MODE = stringPreferencesKey("reader_mode")
        val READER_ORIENTATION = stringPreferencesKey("reader_orientation")
        val DEFAULT_FULLSCREEN_READER = booleanPreferencesKey("default_fullscreen_reader")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val READER_BACKGROUND_COLOR = stringPreferencesKey("reader_background_color")
        val READER_INVERT_COLORS = booleanPreferencesKey("reader_invert_colors")
        val PAGE_PRELOAD_ENABLED = booleanPreferencesKey("page_preload_enabled")
        val BATTERY_SAVER_ENABLED = booleanPreferencesKey("battery_saver_enabled")
        val BATTERY_SAVER_DIM_PERCENT = intPreferencesKey("battery_saver_dim_percent")
        val NSFW_ENABLED = booleanPreferencesKey("nsfw_enabled")
        val AUTO_CLEAN_CACHE_ENABLED = booleanPreferencesKey("auto_clean_cache_enabled")
        val AUTO_CLEAN_CACHE_DAYS = intPreferencesKey("auto_clean_cache_days")
        val AUTO_CLEAN_DOWNLOADS_ENABLED = booleanPreferencesKey("auto_clean_downloads_enabled")
        val KEEP_FAVORITES_DOWNLOADS = booleanPreferencesKey("keep_favorites_downloads")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
    }

    val cachedNsfw: Boolean get() = false

    val nsfwEnabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    suspend fun setNsfwEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NSFW_ENABLED] = false
        }
    }

    val connectionDiagnosticsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CONNECTION_DIAGNOSTICS] ?: false
    }

    suspend fun setConnectionDiagnosticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CONNECTION_DIAGNOSTICS] = enabled }
    }

    val selectedLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[SELECTED_LANGUAGE]
        if (saved.isNullOrBlank() || saved == "all") DEFAULT_LANGUAGE else saved
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

    val readerBackgroundColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_BACKGROUND_COLOR] ?: "OLED"
    }

    val readerInvertColors: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_INVERT_COLORS] ?: false
    }

    val pagePreloadEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PAGE_PRELOAD_ENABLED] ?: true
    }

    val batterySaverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BATTERY_SAVER_ENABLED] ?: false
    }

    val batterySaverDimPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BATTERY_SAVER_DIM_PERCENT] ?: 0
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

    val autoCleanCacheEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CLEAN_CACHE_ENABLED] ?: true
    }

    val autoCleanCacheDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CLEAN_CACHE_DAYS] ?: 3
    }

    val autoCleanDownloadsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CLEAN_DOWNLOADS_ENABLED] ?: true
    }

    val keepFavoritesDownloads: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEEP_FAVORITES_DOWNLOADS] ?: true
    }

    suspend fun setAutoCleanCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_CLEAN_CACHE_ENABLED] = enabled }
    }

    suspend fun setAutoCleanCacheDays(days: Int) {
        context.dataStore.edit { prefs -> prefs[AUTO_CLEAN_CACHE_DAYS] = days.coerceIn(1, 30) }
    }

    suspend fun setAutoCleanDownloadsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_CLEAN_DOWNLOADS_ENABLED] = enabled }
    }

    suspend fun setKeepFavoritesDownloads(keep: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEEP_FAVORITES_DOWNLOADS] = keep }
    }

    val githubRepo: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GITHUB_REPO] ?: com.example.data.updater.AppUpdateManager.DEFAULT_REPO
    }

    suspend fun setGithubRepo(repo: String) {
        context.dataStore.edit { prefs -> prefs[GITHUB_REPO] = repo.trim() }
    }
}
