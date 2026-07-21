package de.excero.tvwartung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TvRoom::class, Inspection::class, ActivityLog::class, RoomSperre::class,
        Material::class, CustomPruefpunkt::class, StundenzettelEntity::class,
        StundenzettelEintrag::class, Einsatz::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvRoomDao(): TvRoomDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun roomSperreDao(): RoomSperreDao
    abstract fun materialDao(): MaterialDao
    abstract fun customPruefpunktDao(): CustomPruefpunktDao
    abstract fun stundenzettelDao(): StundenzettelDao
    abstract fun stundenzettelEintragDao(): StundenzettelEintragDao
    abstract fun einsatzDao(): EinsatzDao

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

        /**
         * v4 → v5: inaktive Zimmer, eigene Prüfpunkte, Sperrgrund,
         * Materialkatalog mit Bestand und gespeicherte Stundenzettel.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_rooms ADD COLUMN inaktiv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE inspections ADD COLUMN extraPunkte TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE room_sperren ADD COLUMN grund TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `materialien` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`bestand` INTEGER NOT NULL, " +
                        "`bestandAktiv` INTEGER NOT NULL, " +
                        "`aktiv` INTEGER NOT NULL, " +
                        "`sortIndex` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_pruefpunkte` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`titel` TEXT NOT NULL, " +
                        "`aktiv` INTEGER NOT NULL, " +
                        "`sortIndex` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stundenzettel` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`station` TEXT NOT NULL, " +
                        "`zeitraumStart` TEXT NOT NULL, " +
                        "`auftragsnummer` TEXT NOT NULL, " +
                        "`datum` TEXT NOT NULL, " +
                        "`von` TEXT NOT NULL, " +
                        "`bis` TEXT NOT NULL, " +
                        "`anfahrt` TEXT NOT NULL, " +
                        "`techniker` TEXT NOT NULL)"
                )
            }
        }

        /** v5 → v6: Stundenzettel mit direkter Stundenangabe statt von/bis. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stundenzettel_neu` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`station` TEXT NOT NULL, " +
                        "`zeitraumStart` TEXT NOT NULL, " +
                        "`auftragsnummer` TEXT NOT NULL, " +
                        "`datum` TEXT NOT NULL, " +
                        "`stunden` TEXT NOT NULL, " +
                        "`anfahrt` TEXT NOT NULL, " +
                        "`techniker` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO stundenzettel_neu " +
                        "(id, station, zeitraumStart, auftragsnummer, datum, stunden, anfahrt, techniker) " +
                        "SELECT id, station, zeitraumStart, auftragsnummer, datum, '', anfahrt, techniker " +
                        "FROM stundenzettel"
                )
                db.execSQL("DROP TABLE stundenzettel")
                db.execSQL("ALTER TABLE stundenzettel_neu RENAME TO stundenzettel")
            }
        }

        /** v6 → v7: Felder für die Server-Synchronisation (updatedAt, UUIDs). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_rooms ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inspections ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stundenzettel ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                // Bestehende Prüfbögen bekommen nachträglich eine eindeutige ID
                db.execSQL("UPDATE inspections SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
            }
        }

        /** v7 → v8: Mehrbenutzer (Mitarbeiter je Prüfung), Team-Stundenzettel, Einsätze, Papierkorb. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspections ADD COLUMN mitarbeiter TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inspections ADD COLUMN geloescht INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stundenzettel_eintraege` (" +
                        "`station` TEXT NOT NULL, `zeitraumStart` TEXT NOT NULL, " +
                        "`mitarbeiter` TEXT NOT NULL, `stunden` TEXT NOT NULL, " +
                        "`anfahrt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, " +
                        "PRIMARY KEY(`station`, `zeitraumStart`, `mitarbeiter`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `einsaetze` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`station` TEXT NOT NULL, `mitarbeiter` TEXT NOT NULL, " +
                        "`start` TEXT NOT NULL, `ende` TEXT NOT NULL)"
                )
            }
        }

        /** v8 → v9: Material bekommt updatedAt für den bidirektionalen Bestand-Sync. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materialien ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { instance = it }
            }

        /** Instanz schließen (z. B. bevor die Datenbankdatei per Backup ersetzt wird). */
        fun reset() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
