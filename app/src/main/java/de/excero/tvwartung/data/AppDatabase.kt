package de.excero.tvwartung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TvRoom::class, Inspection::class, ActivityLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvRoomDao(): TvRoomDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        /** v1 → v2: internes Aktivitätsprotokoll; bestehende Daten bleiben unverändert. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`roomId` TEXT NOT NULL, " +
                        "`zeitpunkt` TEXT NOT NULL, " +
                        "`aktion` TEXT NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tvwartung.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
