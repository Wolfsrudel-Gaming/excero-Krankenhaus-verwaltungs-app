package de.excero.tvwartung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TvRoom::class, Inspection::class, ActivityLog::class, RoomSperre::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvRoomDao(): TvRoomDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun roomSperreDao(): RoomSperreDao

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

        /** v2 → v3: "Kein Zutritt"-Vermerke; bestehende Daten bleiben unverändert. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `room_sperren` (" +
                        "`roomId` TEXT NOT NULL, " +
                        "`gesperrtAm` TEXT NOT NULL, " +
                        "PRIMARY KEY(`roomId`))"
                )
            }
        }

        /** v3 → v4: durchgeführte Arbeiten / verbautes Material je Prüfbogen. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspections ADD COLUMN arbeiten TEXT NOT NULL DEFAULT ''")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
