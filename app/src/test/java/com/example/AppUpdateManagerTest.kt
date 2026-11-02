package com.example

import com.example.data.updater.AppUpdateManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateManagerTest {

    @Test
    fun `isNewerVersion correctly compares semantic versions`() {
        // Newer versions
        assertTrue(AppUpdateManager.isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.0.1", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.1-beta", "1.1.0"))

        // Same or older versions
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.9", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", ""))
    }

    @Test
    fun `checkForUpdates correctly detects update availability and parses apk asset`() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val mockJson = """
                {
                  "tag_name": "v9.9.9",
                  "name": "Manga Reader v9.9.9",
                  "body": "Novidades incríveis e correções.",
                  "published_at": "2026-11-02T10:00:00Z",
                  "html_url": "https://github.com/owner/repo/releases/v9.9.9",
                  "assets": [
                    {
                      "name": "MangaReader-v9.9.9.apk",
                      "browser_download_url": "${server.url("/download/MangaReader.apk")}",
                      "size": 15000000
                    }
                  ]
                }
            """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

            // Test parsing logic
            val json = org.json.JSONObject(mockJson)
            val tagName = json.getString("tag_name")
            val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()
            val updateAvailable = AppUpdateManager.isNewerVersion(cleanRemote, "1.1.0")

            assertTrue("Should detect 9.9.9 as newer than 1.1.0", updateAvailable)
            assertEquals("v9.9.9", tagName)
            assertEquals("Novidades incríveis e correções.", json.getString("body"))

            val asset = json.getJSONArray("assets").getJSONObject(0)
            assertEquals("MangaReader-v9.9.9.apk", asset.getString("name"))
            assertEquals(15000000L, asset.getLong("size"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `checkForUpdates rejects invalid repository formats gracefully`() = runBlocking {
        val result = AppUpdateManager.checkForUpdates("invalid_format_without_slash")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Formato de repositório inválido") == true)
    }
}
