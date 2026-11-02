package com.example.data.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSize: Long?,
    val isUpdateAvailable: Boolean
)

object AppUpdateManager {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    const val DEFAULT_REPO = "chimiasama/MangaApp"

    /**
     * Checks the GitHub Releases API for the latest release in [repo].
     * Example repo format: "owner/repository".
     */
    suspend fun checkForUpdates(repo: String = DEFAULT_REPO): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix("/")
        if (!cleanRepo.contains("/") || cleanRepo.split("/").size != 2) {
            return@withContext Result.failure(IllegalArgumentException("Formato de repositório inválido. Use 'usuario/repositorio'."))
        }

        val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MangaReader-App/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.failure(Exception("Nenhum release encontrado no repositório GitHub '$cleanRepo'."))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro na API do GitHub: HTTP ${response.code}"))
                }

                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)

                val tagName = json.optString("tag_name", "").trim()
                val title = json.optString("name", tagName)
                val body = json.optString("body", "Nenhuma nota de versão fornecida.")
                val publishedAt = json.optString("published_at", "").take(10)
                val htmlUrl = json.optString("html_url", "https://github.com/$cleanRepo/releases")

                // Extract APK asset if available
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize: Long? = null

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", null)
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val currentVersion = BuildConfig.VERSION_NAME.trim()

                val updateAvailable = isNewerVersion(cleanRemoteVersion, currentVersion)

                Result.success(
                    AppReleaseInfo(
                        tagName = tagName,
                        versionName = cleanRemoteVersion,
                        title = title.ifBlank { "Versão $tagName" },
                        changelog = body,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkName = apkName,
                        apkSize = apkSize,
                        isUpdateAvailable = updateAvailable
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares remote semantic version against the current app version.
     * Returns true if remote is strictly greater than current.
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        if (remote.equals(current, ignoreCase = true)) return false

        val remoteParts = remote.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Starts downloading the APK file using Android's DownloadManager, or opens browser as fallback.
     */
    fun downloadApk(context: Context, downloadUrl: String, fileName: String = "MangaReader-update.apk") {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setTitle("Baixando atualização do MangaReader")
                    setDescription(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setMimeType("application/vnd.android.package-archive")
                }
                downloadManager.enqueue(request)
                android.widget.Toast.makeText(
                    context,
                    "Download iniciado! Você receberá uma notificação ao concluir.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        } catch (_: Exception) {
            // Fallback to opening browser directly
        }

        // Browser fallback
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }

    /**
     * Opens the GitHub Releases page in the browser.
     */
    fun openReleasesPage(context: Context, releaseUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
