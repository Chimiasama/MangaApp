package com.example.data.network

object MangaDexApiConfig {
    const val APP_NAME = "MangaReader"
    const val APP_VERSION = "1.1.0"
    const val CONTACT_EMAIL = "chimiasama@gmail.com"

    /**
     * Formatted User-Agent header following MangaDex official API guidelines:
     * "AppName/Version (URL_or_contact_email)"
     */
    const val USER_AGENT = "$APP_NAME/$APP_VERSION ($CONTACT_EMAIL)"
}
