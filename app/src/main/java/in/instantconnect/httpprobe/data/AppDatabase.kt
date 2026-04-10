package `in`.instantconnect.httpprobe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProbeConfig::class, ProbeLog::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN smsFilterRegex TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN isNtfy INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyUrl TEXT")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyUsername TEXT")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyPassword TEXT")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN lastNtfyMessageId TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyConsumerMode INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfySince TEXT")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyScheduled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE probe_configs ADD COLUMN ntfyFilters TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "probe_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
