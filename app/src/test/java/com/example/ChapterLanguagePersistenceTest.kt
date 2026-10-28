package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ChapterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChapterLanguagePersistenceTest {
    @Test fun `version seven upgrade keeps chapter progress without guessing its language`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "language-migration-${java.util.UUID.randomUUID()}"
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        db.chapterDao().insertChapter(ChapterEntity("saved", "manga", "Capítulo 115", "chapter", isRead = true, lastPageRead = 7))
        db.close()
        // Recreate the version 7 chapter table while retaining the rest of the real Room schema.
        val sqlite = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.execSQL("ALTER TABLE chapters DROP COLUMN language")
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN year")
        sqlite.version = 7
        sqlite.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build()
        try {
            val old = migrated.chapterDao().getChapterByIdSync("saved")!!
            assertTrue(old.isRead)
            assertEquals(7, old.lastPageRead)
            assertEquals("", old.language)
            migrated.chapterDao().insertChapter(old.copy(id = "verified", language = "pt-br"))
            assertEquals("pt-br", migrated.chapterDao().getChapterByIdSync("verified")!!.language)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
