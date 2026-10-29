package com.example.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import java.net.HttpURLConnection
import java.net.URL

/** Uses the system Chromium network stack, without changing TLS trust, DNS or proxy settings. */
object MangaDexChromiumTransport {
    @Volatile private var appContext: Context? = null
    @Volatile private var unavailableReason: String? = null

    fun initialize(context: Context) { appContext = context.applicationContext }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 34 ||
        (Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7)

    fun description(): String = when {
        !isSupported() -> "Chromium HttpEngine indisponível nesta versão do Android"
        appContext == null -> "Chromium HttpEngine ainda não inicializado"
        unavailableReason != null -> "Chromium HttpEngine indisponível: $unavailableReason"
        else -> "Chromium HttpEngine do Android (negociação automática)"
    }

    @SuppressLint("NewApi") // Runtime guard includes the API's S extension backport.
    fun openConnection(url: URL): HttpURLConnection? {
        require(url.protocol == "https") { "A recuperação exige HTTPS" }
        if (!isSupported() || unavailableReason != null) return null
        val context = appContext ?: return null
        // Some vendor images expose the API but lack the implementation. Keep the original fallback.
        val engine = try { Api34.engine(context) }
        catch (error: Exception) { unavailableReason = error.javaClass.simpleName; return null }
        catch (error: LinkageError) { unavailableReason = error.javaClass.simpleName; return null }
        return engine.openConnection(url) as HttpURLConnection
    }

    @SuppressLint("NewApi") // Also supplied by S SDK extension 7, checked above.
    private object Api34 {
        private var instance: android.net.http.HttpEngine? = null
        @Synchronized fun engine(context: Context): android.net.http.HttpEngine {
            return instance ?: android.net.http.HttpEngine.Builder(context)
                .setUserAgent(MangaDexConnectionDiagnostic.USER_AGENT)
                .setEnableHttp2(true).setEnableQuic(true)
                .setEnableHttpCache(android.net.http.HttpEngine.Builder.HTTP_CACHE_DISABLED, 0)
                .build().also { instance = it }
        }
    }
}
