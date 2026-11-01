package com.example.data.network

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/**
 * Intelligent endpoint health and failover manager for MangaDex API.
 * Coordinates dynamic fallback between primary (api.mangadex.org) and official staging (api.mangadex.dev)
 * when Cloudflare WAF, regional ISP blocks, or transport failures occur.
 */
object MangaDexEndpointManager {

    const val PRIMARY_HOST = "api.mangadex.org"
    const val FALLBACK_HOST = "api.mangadex.dev"
    const val PRIMARY_BASE_URL = "https://$PRIMARY_HOST/"
    const val FALLBACK_BASE_URL = "https://$FALLBACK_HOST/"

    private val fallbackActiveUntil = AtomicLong(0L)
    private val preferFallback = AtomicBoolean(false)

    /** Duration to route requests to fallback host before re-probing primary (5 minutes) */
    private val COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5)

    fun isFallbackActive(): Boolean {
        val now = System.currentTimeMillis()
        return preferFallback.get() && now < fallbackActiveUntil.get()
    }

    fun markPrimaryFailure(reason: String? = null) {
        val until = System.currentTimeMillis() + COOLDOWN_MS
        fallbackActiveUntil.set(until)
        preferFallback.set(true)
        try {
            android.util.Log.w("MangaDexEndpoint", "Primário ($PRIMARY_HOST) degradado. Ativando fallback para $FALLBACK_HOST até $until. Motivo: $reason")
        } catch (_: Throwable) {
            System.err.println("[MangaDexEndpoint] Primário ($PRIMARY_HOST) degradado: $reason")
        }
    }

    fun markPrimarySuccess() {
        preferFallback.set(false)
        fallbackActiveUntil.set(0L)
    }

    fun markFallbackSuccess() {
        // Keep fallback active for the cooldown duration to prevent flip-flopping
        if (preferFallback.get()) {
            val until = System.currentTimeMillis() + COOLDOWN_MS
            fallbackActiveUntil.set(until)
        }
    }

    fun reset() {
        preferFallback.set(false)
        fallbackActiveUntil.set(0L)
    }

    fun getPreferredHost(): String {
        return if (isFallbackActive()) FALLBACK_HOST else PRIMARY_HOST
    }
}
