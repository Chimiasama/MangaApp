package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogMetadataPersistenceTest {
    @Test fun `metadata upgrade preserves favorites and completed chapter history and new fields survive reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "classification-${java.util.UUID.randomUUID()}"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        db.mangaDao().insertManga(MangaEntity("work", "id", "Title", "", "", "mangadex", year = 1984))
        db.progressDao().markChapterRead(ReadChapterEntity("work", "mangadex", "445"))
        db.close()
        val sqlite = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN originalLanguage")
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN status")
        sqlite.version = 10
        sqlite.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_10_11).build()
        try {
            val old = db.mangaDao().getMangaByUrlSync("work")!!
            assertTrue(old.isFavorite)
            assertEquals(1984, old.year)
            assertEquals("", old.originalLanguage)
            assertEquals("Unknown", old.status)
            db.mangaDao().insertManga(old.copy(originalLanguage = "ja", status = "completed"))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("ja", db.mangaDao().getMangaByUrlSync("work")!!.originalLanguage)
            assertEquals("completed", db.mangaDao().getMangaByUrlSync("work")!!.status)
            assertEquals(listOf("445"), db.progressDao().getReadChapterIds("work", "mangadex").first())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
