package com.orba.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Video::class, Category::class, Note::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videos(): VideoDao
    abstract fun categories(): CategoryDao
    abstract fun notes(): NoteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Versión 1 → 2: orientación elegida a mano y última vez reproducido. Conserva todo lo guardado. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN orientation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE videos ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 2 → 3: audios (sección Escuchar) y categorías propias para audio. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 3 → 4: el mismo enlace puede guardarse como video y como audio. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_videos_sourceUrl`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_videos_sourceUrl_kind` ON `videos` (`sourceUrl`, `kind`)")
            }
        }

        /** Versión 4 → 5: favoritos (Me encanta). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 5 → 6: refuerzo de volumen por video o canción. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN boostDb INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orba.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
