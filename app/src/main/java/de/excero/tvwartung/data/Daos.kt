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

    @Query("SELECT * FROM inspections ORDER BY datum DESC, roomId")
    suspend fun getAll(): List<Inspection>
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
