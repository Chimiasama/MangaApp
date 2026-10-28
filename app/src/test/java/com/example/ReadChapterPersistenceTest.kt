package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.local.ReadChapterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadChapterPersistenceTest {
    @Test fun `completed chapters persist independently for each work and source`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "read-${java.util.UUID.randomUUID()}"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            db.progressDao().markChapterRead(ReadChapterEntity("work", "source-a", "one"))
            db.progressDao().markChapterRead(ReadChapterEntity("work", "source-a", "two"))
            db.progressDao().markChapterRead(ReadChapterEntity("work", "source-a", "one"))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(setOf("one", "two"), db.progressDao().getReadChapterIds("work", "source-a").first().toSet())
            assertTrue(db.progressDao().getReadChapterIds("work", "source-b").first().isEmpty())
            assertTrue(db.progressDao().getReadChapterIds("other", "source-a").first().isEmpty())
            db.progressDao().markChapterUnread("work", "source-b", "one")
            assertEquals(2, db.progressDao().getReadChapterIds("work", "source-a").first().size)
            db.progressDao().markChapterUnread("work", "source-a", "one")
            assertEquals(listOf("two"), db.progressDao().getReadChapterIds("work", "source-a").first())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `upgrade recovers completed chapters without marking partially read chapters`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "read-migration-${java.util.UUID.randomUUID()}"
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        db.mangaDao().insertManga(MangaEntity("work", "id", "Title", "", "", "source"))
        db.chapterDao().insertChapter(ChapterEntity("done", "work", "Done", "done", isRead = true, lastPageRead = 9, totalPages = 10))
        db.chapterDao().insertChapter(ChapterEntity("partial", "work", "Partial", "partial", isRead = true, lastPageRead = 2, totalPages = 10))
        db.close()
        val sqlite = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.execSQL("DROP TABLE read_chapters")
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN year")
        sqlite.version = 8
        sqlite.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build()
        try {
            assertEquals(listOf("done"), upgraded.progressDao().getReadChapterIds("work", "source").first())
            assertNotNull(upgraded.chapterDao().getChapterByIdSync("partial"))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
