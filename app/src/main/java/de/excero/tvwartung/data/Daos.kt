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

    @Query("SELECT * FROM inspections WHERE roomId = :roomId ORDER BY datum DESC, id DESC")
    fun observeForRoom(roomId: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE datum = :datum ORDER BY roomId")
    fun observeForDate(datum: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE datum >= :startDatum ORDER BY roomId")
    fun observeSince(startDatum: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observeById(id: Long): Flow<Inspection?>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: Long): Inspection?

    @Query("SELECT * FROM inspections WHERE datum = :datum ORDER BY roomId")
    suspend fun getForDate(datum: String): List<Inspection>

    @Query("SELECT * FROM inspections ORDER BY datum DESC, roomId")
    suspend fun getAll(): List<Inspection>
}

@Dao
interface RoomSperreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sperre: RoomSperre)

    @Query("DELETE FROM room_sperren WHERE roomId = :roomId")
    suspend fun delete(roomId: String)

    @Query("SELECT * FROM room_sperren")
    fun observeAll(): Flow<List<RoomSperre>>
}

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLog)

    @Query("SELECT * FROM activity_log WHERE roomId = :roomId ORDER BY zeitpunkt DESC")
    fun observeForRoom(roomId: String): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_log ORDER BY zeitpunkt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLog>>
}

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materialien ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<Material>>

    @Query("SELECT * FROM materialien WHERE aktiv = 1 ORDER BY sortIndex, name")
    fun observeAktive(): Flow<List<Material>>

    @Query("SELECT * FROM materialien")
    suspend fun getAll(): List<Material>

    @Query("SELECT COUNT(*) FROM materialien")
    suspend fun count(): Int

    @Insert
    suspend fun insert(material: Material): Long

    @Update
    suspend fun update(material: Material)

    @Query("UPDATE materialien SET bestand = bestand - 1 WHERE name = :name AND bestandAktiv = 1")
    suspend fun verbrauche(name: String)
}

@Dao
interface CustomPruefpunktDao {
    @Query("SELECT * FROM custom_pruefpunkte ORDER BY sortIndex, titel")
    fun observeAll(): Flow<List<CustomPruefpunkt>>

    @Query("SELECT * FROM custom_pruefpunkte WHERE aktiv = 1 ORDER BY sortIndex, titel")
    fun observeAktive(): Flow<List<CustomPruefpunkt>>

    @Insert
    suspend fun insert(punkt: CustomPruefpunkt): Long

    @Update
    suspend fun update(punkt: CustomPruefpunkt)
}

@Dao
interface StundenzettelDao {
    @Query("SELECT * FROM stundenzettel WHERE station = :station AND zeitraumStart = :zeitraumStart LIMIT 1")
    suspend fun getFor(station: String, zeitraumStart: String): StundenzettelEntity?

    @Query("SELECT COUNT(*) FROM stundenzettel")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zettel: StundenzettelEntity): Long
}
