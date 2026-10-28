package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReadChapterEntity::class, MangaEntity::class, ChapterEntity::class, ProgressEntity::class, DownloadEntity::class, ReadingSessionEntity::class, BookmarkEntity::class, SearchHistoryEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun progressDao(): ProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_favorites ADD COLUMN originalLanguage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_favorites ADD COLUMN status TEXT NOT NULL DEFAULT 'Unknown'")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_favorites ADD COLUMN year INTEGER")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS read_chapters (mangaUrl TEXT NOT NULL, sourceId TEXT NOT NULL, chapterId TEXT NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY(mangaUrl, sourceId, chapterId))")
                // Only recover chapters with evidence of completion; opening a chapter used to set isRead.
                db.execSQL("INSERT OR IGNORE INTO read_chapters SELECT c.mangaUrl, m.sourceId, c.id, c.fetchedTimestamp FROM chapters c INNER JOIN manga_favorites m ON m.url = c.mangaUrl WHERE c.totalPages > 0 AND c.lastPageRead >= c.totalPages - 1")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN language TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manga_reader_db"
                ).addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
