package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.network.MangaDexApiService
import com.example.source.MangaDexSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogYearTest {
    @Test fun `MangaDex year only catalog sends exact publication years without a title or language restriction for all`() = runBlocking {
        val requests = mutableListOf<HttpUrl>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            requests += url
            val year = url.queryParameter("year")!!.toInt()
            val offset = url.queryParameter("offset")!!.toInt()
            val count = if (year == 1985 && offset == 0) 100 else 1
            val data = (offset until offset + count).joinToString(",") { index ->
                """{"id":"m$year-$index","attributes":{"title":{"en":"Manga $year $index"},"year":$year}}"""
            }
            val total = if (year == 1985) 101 else 1
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":[$data],"total":$total}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build().create(MangaDexApiService::class.java)
        val result = MangaDexSource(api).getCatalogManga("", "all", "1985-1986")
        assertEquals(101, result.count { it.year == 1985 })
        assertEquals(1, result.count { it.year == 1986 })
        assertEquals(listOf("1985", "1985", "1986"), requests.map { it.queryParameter("year") })
        assertEquals(listOf("0", "100", "0"), requests.map { it.queryParameter("offset") })
        assertTrue(requests.all { it.queryParameter("title") == null && it.queryParameter("availableTranslatedLanguage[]") == null })
    }

    @Test fun `version nine upgrade preserves reading history and publication years survive database reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "year-${java.util.UUID.randomUUID()}"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        db.mangaDao().insertManga(MangaEntity("work", "id", "Title", "", "", "source"))
        db.progressDao().markChapterRead(ReadChapterEntity("work", "source", "445"))
        db.close()
        val sqlite = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN year")
        sqlite.version = 9
        sqlite.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_9_10).build()
        try {
            val manga = db.mangaDao().getMangaByUrlSync("work")!!
            assertNull(manga.year)
            db.mangaDao().insertManga(manga.copy(year = 1985))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(1985, db.mangaDao().getMangaByUrlSync("work")!!.year)
            assertEquals(listOf("445"), db.progressDao().getReadChapterIds("work", "source").first())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
