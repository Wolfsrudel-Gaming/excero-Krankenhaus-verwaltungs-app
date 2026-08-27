package de.excero.tvwartung.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvRoomDao {
    @Query("SELECT * FROM tv_rooms ORDER BY station, zimmer")
    fun observeAll(): Flow<List<TvRoom>>

    @Query("SELECT * FROM tv_rooms WHERE id = :id")
    fun observeById(id: String): Flow<TvRoom?>

    @Query("SELECT * FROM tv_rooms WHERE id = :id")
    suspend fun getById(id: String): TvRoom?

    @Query("SELECT * FROM tv_rooms ORDER BY station, zimmer")
    suspend fun getAll(): List<TvRoom>

    @Query("SELECT COUNT(*) FROM tv_rooms")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rooms: List<TvRoom>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(room: TvRoom)

    @Update
    suspend fun update(room: TvRoom)
}

@Dao
interface InspectionDao {
    @Insert
    suspend fun insert(inspection: Inspection): Long

    @Query("SELECT * FROM inspections WHERE roomId = :roomId AND geloescht = 0 ORDER BY datum DESC, id DESC")
    fun observeForRoom(roomId: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE datum = :datum AND geloescht = 0 ORDER BY roomId")
    fun observeForDate(datum: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE datum >= :startDatum AND geloescht = 0 ORDER BY roomId")
    fun observeSince(startDatum: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observeById(id: Long): Flow<Inspection?>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: Long): Inspection?

    @Query("SELECT * FROM inspections WHERE datum = :datum ORDER BY roomId")
    suspend fun getForDate(datum: String): List<Inspection>

    @Query("SELECT * FROM inspections ORDER BY datum DESC, roomId")
    suspend fun getAll(): List<Inspection>

    @Query("SELECT * FROM inspections WHERE geloescht = 0 ORDER BY datum DESC, roomId")
    fun observeAlleSichtbaren(): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE geloescht = 1 ORDER BY datum DESC")
    fun observeGeloeschte(): Flow<List<Inspection>>

    @Query("UPDATE inspections SET geloescht = :geloescht WHERE id = :id")
    suspend fun setGeloescht(id: Long, geloescht: Boolean)

    @Query("SELECT * FROM inspections WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Inspection?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(inspection: Inspection): Long

    @Query("DELETE FROM inspections")
    suspend fun deleteAll()
}

@Dao
interface StundenzettelEintragDao {
    @Query("DELETE FROM stundenzettel_eintraege")
    suspend fun deleteAll()

    @Query("SELECT * FROM stundenzettel_eintraege WHERE station = :station AND zeitraumStart = :zeitraumStart ORDER BY mitarbeiter")
    fun observeFor(station: String, zeitraumStart: String): Flow<List<StundenzettelEintrag>>

    @Query("SELECT * FROM stundenzettel_eintraege WHERE station = :station AND zeitraumStart = :zeitraumStart ORDER BY mitarbeiter")
    suspend fun getFor(station: String, zeitraumStart: String): List<StundenzettelEintrag>

    @Query("SELECT * FROM stundenzettel_eintraege")
    suspend fun getAll(): List<StundenzettelEintrag>

    @Query("SELECT * FROM stundenzettel_eintraege")
    fun observeAll(): Flow<List<StundenzettelEintrag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eintrag: StundenzettelEintrag)

    @Query("DELETE FROM stundenzettel_eintraege WHERE station = :station AND zeitraumStart = :zeitraumStart AND mitarbeiter = :mitarbeiter")
    suspend fun delete(station: String, zeitraumStart: String, mitarbeiter: String)
}

@Dao
interface EinsatzDao {
    @Query("DELETE FROM einsaetze")
    suspend fun deleteAll()

    @Query("SELECT * FROM einsaetze WHERE mitarbeiter = :mitarbeiter AND ende = '' LIMIT 1")
    suspend fun laufender(mitarbeiter: String): Einsatz?

    @Query("SELECT * FROM einsaetze WHERE mitarbeiter = :mitarbeiter AND ende = '' LIMIT 1")
    fun observeLaufender(mitarbeiter: String): Flow<Einsatz?>

    @Query("SELECT * FROM einsaetze ORDER BY start DESC")
    suspend fun getAll(): List<Einsatz>

    @Query("SELECT * FROM einsaetze ORDER BY start DESC")
    fun observeAll(): Flow<List<Einsatz>>

    @Insert
    suspend fun insert(einsatz: Einsatz): Long

    @Update
    suspend fun update(einsatz: Einsatz)
}

@Dao
interface RoomSperreDao {
    @Query("DELETE FROM room_sperren")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sperre: RoomSperre)

    @Query("DELETE FROM room_sperren WHERE roomId = :roomId")
    suspend fun delete(roomId: String)

    @Query("SELECT * FROM room_sperren")
    fun observeAll(): Flow<List<RoomSperre>>

    @Query("SELECT * FROM room_sperren")
    suspend fun getAll(): List<RoomSperre>
}

@Dao
interface ActivityLogDao {
    @Query("DELETE FROM activity_log")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(entry: ActivityLog)

    @Query("SELECT * FROM activity_log WHERE roomId = :roomId ORDER BY zeitpunkt DESC")
    fun observeForRoom(roomId: String): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_log ORDER BY zeitpunkt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_log ORDER BY id")
    suspend fun getAll(): List<ActivityLog>
}

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materialien ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<Material>>

    @Query("SELECT * FROM materialien WHERE aktiv = 1 ORDER BY sortIndex, name")
    fun observeAktive(): Flow<List<Material>>

    @Query("SELECT * FROM materialien")
    suspend fun getAll(): List<Material>

    @Query("SELECT * FROM materialien WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Material?

    @Query("SELECT COUNT(*) FROM materialien")
    suspend fun count(): Int

    @Insert
    suspend fun insert(material: Material): Long

    @Update
    suspend fun update(material: Material)

    @Query("UPDATE materialien SET bestand = bestand - 1, updatedAt = :zeitpunkt WHERE name = :name AND bestandAktiv = 1")
    suspend fun verbrauche(name: String, zeitpunkt: String)
}

@Dao
interface CustomPruefpunktDao {
    @Query("SELECT * FROM custom_pruefpunkte ORDER BY sortIndex, titel")
    fun observeAll(): Flow<List<CustomPruefpunkt>>

    @Query("SELECT * FROM custom_pruefpunkte ORDER BY sortIndex, titel")
    suspend fun getAll(): List<CustomPruefpunkt>

    @Query("SELECT * FROM custom_pruefpunkte WHERE aktiv = 1 ORDER BY sortIndex, titel")
    fun observeAktive(): Flow<List<CustomPruefpunkt>>

    @Insert
    suspend fun insert(punkt: CustomPruefpunkt): Long

    @Update
    suspend fun update(punkt: CustomPruefpunkt)
}

@Dao
interface StundenzettelDao {
    @Query("DELETE FROM stundenzettel")
    suspend fun deleteAll()

    @Query("SELECT * FROM stundenzettel WHERE station = :station AND zeitraumStart = :zeitraumStart LIMIT 1")
    suspend fun getFor(station: String, zeitraumStart: String): StundenzettelEntity?

    @Query("SELECT * FROM stundenzettel WHERE id = :id")
    suspend fun getById(id: Long): StundenzettelEntity?

    @Query("SELECT * FROM stundenzettel ORDER BY zeitraumStart DESC, station")
    fun observeAll(): Flow<List<StundenzettelEntity>>

    @Query("SELECT * FROM stundenzettel")
    suspend fun getAll(): List<StundenzettelEntity>

    @Query("SELECT COUNT(*) FROM stundenzettel")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zettel: StundenzettelEntity): Long
}
