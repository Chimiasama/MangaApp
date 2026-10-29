package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.network.MangaDexApiService
import com.example.data.network.MangaDexCallFactory
import com.example.data.network.MangaDexCatalogFilterApi
import com.example.data.network.RetrofitClient
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.net.ssl.HttpsURLConnection

/** Opt-in network verification; no app transport, source or filter settings are changed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveSpyFamilyCheck {
    @Test fun searchPortugueseArchivesFiltersAndFirstChapter() = runBlocking {
        val proxy = System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let {
            val uri = URI(it)
            Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
        }
        val apiClient = RetrofitClient.okHttpClient.newBuilder().apply { if (proxy != null) this.proxy(proxy) }.build()
        val htmlClient = SourceHttp.client.newBuilder().apply { if (proxy != null) this.proxy(proxy) }.build()
        val calls = MangaDexCallFactory(apiClient, nativeConnection = { url ->
            (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpsURLConnection
        })
        val retrofit = Retrofit.Builder().baseUrl(RetrofitClient.MANGADEX_BASE_URL).callFactory(calls)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build()
        val api = retrofit.create(MangaDexApiService::class.java)
        val dex = MangaDexSource(apiService = api, catalogApi = retrofit.create(MangaDexCatalogFilterApi::class.java))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val cache = java.nio.file.Files.createTempDirectory("spy-family-live").toFile()
        val html = mutableMapOf<String, String>()
        val dash = MangaDashSource(cacheDir = cache,
            fetch = { url ->
                html[url] ?: run {
                    delay(1500)
                    withContext(Dispatchers.IO) {
                        htmlClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            check(response.isSuccessful) { "HTML HTTP ${response.code}: $url" }
                            response.body!!.string()
                        }
                    }.also { html[url] = it }
                }
            },
            downloadPdf = { url, referer, file ->
                withContext(Dispatchers.IO) {
                    htmlClient.newCall(Request.Builder().url(url).header("Referer", referer)
                        .header("Accept", "application/pdf").build()).execute().use { response ->
                        check(response.isSuccessful) { "PDF HTTP ${response.code}" }
                        response.body!!.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    file.inputStream().use { input ->
                        val header = ByteArray(5)
                        java.io.DataInputStream(input).readFully(header)
                        check(String(header, Charsets.US_ASCII) == "%PDF-")
                    }
                }
            },
            renderPdf = { file, dir ->
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    // Independent real PDF decoding; the APK still uses Android PdfRenderer.
                    val process = ProcessBuilder("pdftoppm", "-png", "-scale-to", "900", file.path,
                        File(dir, "page").path).redirectOutput(File(dir, "stdout.log"))
                        .redirectError(File(dir, "stderr.log")).start()
                    if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); error("PDF render timeout") }
                    check(process.exitValue() == 0) { File(dir, "stderr.log").readText() }
                    dir.listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }
                        .map { it.toURI().toString() }
                }
            })
        try {
            val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
                ChapterDownloadManager(context, db.downloadDao()), UserPreferencesRepository(context),
                db.chapterDao(), db.bookmarkDao(), appContext = context)
            repository.registerSource(dex)
            repository.registerSource(dash)
            val results = repository.loadCatalog("spy", "all", "pt-br", "all").items
            val dashBook = results.first { it.sourceId == "mangadash" && it.url.endsWith("/118-spy-x-family") }
            val dexBook = results.first { it.sourceId == "mangadex" && it.id == "6b958848-c885-4735-9201-12ee77abcb3c" }
            val dashChapters = repository.getAggregatedChapters(dashBook.title, dashBook.url, dashBook.sourceId, "pt-br")
            val dexChapters = repository.getAggregatedChapters(dexBook.title, dexBook.url, dexBook.sourceId, "pt-br")
            assertTrue(dashChapters.size > 100)
            assertTrue(dashChapters.all { ChapterLanguage.matches(it.language, "pt-br") })
            assertTrue(dashChapters.any { it.chapterNumber == 1f })
            assertTrue(dashChapters.any { it.chapterNumber == 2f })
            assertTrue(dashChapters.any { it.chapterNumber == 3f })
            val raw = api.getChapterFeed(dexBook.id, translatedLanguages = listOf("pt-br"))
            val external = raw.data.orEmpty().filter { !it.attributes?.externalUrl.isNullOrBlank() }
            assertTrue(dexChapters.none { chapter -> external.any { it.id == chapter.id } })
            val colored = dex.getChapters("https://api.mangadex.org/manga/76eb5273-5869-4f5a-8b43-853f55efd6cd", "pt-br")
            val romance = CatalogFilters(included = setOf("genre:romance"))
            val genreResults = repository.loadCatalog("spy", "all", "pt-br", "all", romance)
                .items.filter(romance::matches) // Same final predicate as MangaViewModel.
            assertTrue(genreResults.any { it.url == dexBook.url })
            assertFalse(genreResults.any { it.url == dashBook.url })
            val first = dashChapters.first { it.chapterNumber == 1f }
            val pages = dash.getPages(first.chapterUrl)
            assertTrue(pages.isNotEmpty())
            for (page in listOf(pages.first(), pages.last())) {
                val image = ImageIO.read(File(URI(page)))
                assertNotNull(image)
                assertTrue(image!!.width > 0 && image.height > 0)
                image.flush()
            }
            println("LIVE SPY: search=${results.size}; sources=${results.map { it.sourceId }.distinct()}")
            println("MangaDash: PTBR=${dashChapters.size}; first=${dashChapters.minOf { it.chapterNumber }}; last=${dashChapters.maxOf { it.chapterNumber }}; chapter1 PDF pages=${pages.size}; first+last decoded")
            println("MangaDex: native PTBR=${dexChapters.size}; first=${dexChapters.minOf { it.chapterNumber }}; external=${external.size}; external numbers=${external.map { it.attributes?.chapter }}")
            println("MangaDex colored: native PTBR=${colored.size}; numbers=${colored.map { it.chapterNumber }}")
            println("Romance: MangaDex main included; MangaDash excluded by its genre metadata")
        } finally {
            db.close()
            cache.deleteRecursively()
            apiClient.dispatcher.executorService.shutdown()
            apiClient.connectionPool.evictAll()
            htmlClient.dispatcher.executorService.shutdown()
            htmlClient.connectionPool.evictAll()
        }
    }
}
