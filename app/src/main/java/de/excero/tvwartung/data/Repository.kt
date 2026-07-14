package de.excero.tvwartung.data

import android.content.Context
import androidx.room.withTransaction
import de.excero.tvwartung.util.Dates
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Zentrale Datenzugriffsschicht. Die Room-Datenbank ist die "Quelle der Wahrheit",
 * die Excel-Datei dient nur als Import-/Exportformat.
 */
class Repository(private val db: AppDatabase) {

    val rooms: Flow<List<TvRoom>> get() = db.tvRoomDao().observeAll()

    fun room(id: String): Flow<TvRoom?> = db.tvRoomDao().observeById(id)

    fun inspectionsFor(roomId: String): Flow<List<Inspection>> =
        db.inspectionDao().observeForRoom(roomId)

    fun inspectionsOn(isoDate: String): Flow<List<Inspection>> =
        db.inspectionDao().observeForDate(isoDate)

    fun inspectionsSince(isoStart: String): Flow<List<Inspection>> =
        db.inspectionDao().observeSince(isoStart)

    fun activityFor(roomId: String): Flow<List<ActivityLog>> =
        db.activityLogDao().observeForRoom(roomId)

    val sperren: Flow<List<RoomSperre>> get() = db.roomSperreDao().observeAll()

    /**
     * "Kein Zutritt" für ein Zimmer setzen oder aufheben. Beim Setzen wird ein
     * Vermerk mit aktuellem Datum in den Lebenslauf geschrieben ("... konnte
     * nicht betreten werden"); beim Aufheben wird der heutige Vermerk wieder
     * entfernt.
     */
    suspend fun setKeinZutritt(roomId: String, gesperrt: Boolean) {
        val vermerk = "${Dates.todayGerman()}: Zimmer konnte nicht betreten werden"
        db.withTransaction {
            val room = db.tvRoomDao().getById(roomId)
            if (gesperrt) {
                db.roomSperreDao().upsert(RoomSperre(roomId, Dates.todayIso()))
                if (room != null && !room.lebenslauf.lines().any { it.trim() == vermerk }) {
                    val neu = if (room.lebenslauf.isBlank()) vermerk
                    else room.lebenslauf.trimEnd() + "\n" + vermerk
                    db.tvRoomDao().update(room.copy(lebenslauf = neu))
                }
                logAction(roomId, "Kein Zutritt vermerkt")
            } else {
                db.roomSperreDao().delete(roomId)
                if (room != null && room.lebenslauf.lines().any { it.trim() == vermerk }) {
                    val neu = room.lebenslauf.lines()
                        .filterNot { it.trim() == vermerk }
                        .joinToString("\n")
                    db.tvRoomDao().update(room.copy(lebenslauf = neu))
                }
                logAction(roomId, "Zutritt wieder möglich")
            }
        }
    }

    fun recentActivity(limit: Int = 200): Flow<List<ActivityLog>> =
        db.activityLogDao().observeRecent(limit)

    fun inspection(id: Long): Flow<Inspection?> = db.inspectionDao().observeById(id)

    suspend fun getInspection(id: Long): Inspection? = db.inspectionDao().getById(id)

    suspend fun inspectionsForDate(isoDate: String): List<Inspection> =
        db.inspectionDao().getForDate(isoDate)

    suspend fun getRoom(id: String): TvRoom? = db.tvRoomDao().getById(id)

    suspend fun allRooms(): List<TvRoom> = db.tvRoomDao().getAll()

    suspend fun allInspections(): List<Inspection> = db.inspectionDao().getAll()

    /** Interne Protokollierung: wann wurde welches Zimmer bearbeitet (wird nie exportiert). */
    suspend fun logAction(roomId: String, aktion: String) {
        db.activityLogDao().insert(
            ActivityLog(roomId = roomId, zeitpunkt = Dates.nowIsoDateTime(), aktion = aktion)
        )
    }

    suspend fun updateRoom(room: TvRoom, logAktion: String = "Stammdaten geändert") {
        db.withTransaction {
            db.tvRoomDao().update(room)
            logAction(room.id, logAktion)
        }
    }

    suspend fun importRooms(rooms: List<TvRoom>) = db.tvRoomDao().upsertAll(rooms)

    /**
     * Speichert einen ausgefüllten Prüfbogen atomar und schreibt alle Ergebnisse in
     * die Stammdaten zurück: Lebenslauf-Eintrag, letzte Prüfung, korrigierte Werte
     * (Seriennummer, Freenet-ID, TV-Typ) und ein neues/korrigiertes Gültigkeitsdatum.
     */
    suspend fun saveInspection(
        inspection: Inspection,
        lebenslaufEintrag: String,
        neuesGueltigBis: String? = null,
        neueSeriennummer: String? = null,
        neueFreenetId: String? = null,
        neuerTvTyp: String? = null
    ) {
        db.withTransaction {
            db.inspectionDao().insert(inspection)
            val room = db.tvRoomDao().getById(inspection.roomId) ?: return@withTransaction
            val neueHistorie = when {
                lebenslaufEintrag.isBlank() -> room.lebenslauf
                room.lebenslauf.isBlank() -> lebenslaufEintrag
                else -> room.lebenslauf.trimEnd() + "\n" + lebenslaufEintrag
            }
            db.tvRoomDao().update(
                room.copy(
                    lebenslauf = neueHistorie,
                    letztePruefung = inspection.datum,
                    gueltigBis = neuesGueltigBis?.takeIf { it.isNotBlank() } ?: room.gueltigBis,
                    seriennummer = neueSeriennummer?.takeIf { it.isNotBlank() } ?: room.seriennummer,
                    freenetId = neueFreenetId?.takeIf { it.isNotBlank() } ?: room.freenetId,
                    tvTyp = neuerTvTyp?.takeIf { it.isNotBlank() } ?: room.tvTyp
                )
            )
            logAction(inspection.roomId, "Prüfbogen gespeichert")
        }
    }

    /** Lädt die mitgelieferten Stammdaten (Stand der KKH-Übersicht), falls die DB leer ist. */
    suspend fun seedIfEmpty(context: Context) {
        if (db.tvRoomDao().count() > 0) return
        val json = context.assets.open("seed.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val rooms = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TvRoom(
                        id = o.optString("id"),
                        station = o.optString("station"),
                        zimmer = o.optString("zimmer"),
                        lebenslauf = o.optString("lebenslauf"),
                        letztePruefung = Dates.normalizeIso(o.optString("letztePruefung")),
                        tvTyp = o.optString("tvTyp"),
                        seriennummer = o.optString("seriennummer"),
                        freenetId = o.optString("freenetId"),
                        gueltigBis = Dates.normalizeIso(o.optString("gueltigBis"))
                    )
                )
            }
        }
        db.tvRoomDao().upsertAll(rooms)
    }
}
