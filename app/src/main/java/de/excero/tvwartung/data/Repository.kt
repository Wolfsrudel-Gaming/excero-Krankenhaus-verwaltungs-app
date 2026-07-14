package de.excero.tvwartung.data

import android.content.Context
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

    suspend fun allRooms(): List<TvRoom> = db.tvRoomDao().getAll()

    suspend fun allInspections(): List<Inspection> = db.inspectionDao().getAll()

    suspend fun updateRoom(room: TvRoom) = db.tvRoomDao().update(room)

    suspend fun importRooms(rooms: List<TvRoom>) = db.tvRoomDao().upsertAll(rooms)

    /**
     * Speichert einen ausgefüllten Prüfbogen und schreibt die Ergebnisse in die
     * Stammdaten zurück: Lebenslauf-Eintrag, letzte Prüfung und – falls Freenet
     * verlängert wurde – das neue Gültigkeitsdatum.
     */
    suspend fun saveInspection(
        inspection: Inspection,
        lebenslaufEintrag: String,
        neuesGueltigBis: String?
    ) {
        db.inspectionDao().insert(inspection)
        val room = db.tvRoomDao().getById(inspection.roomId) ?: return
        val neueHistorie = if (lebenslaufEintrag.isBlank()) {
            room.lebenslauf
        } else if (room.lebenslauf.isBlank()) {
            lebenslaufEintrag
        } else {
            room.lebenslauf.trimEnd() + "\n" + lebenslaufEintrag
        }
        db.tvRoomDao().update(
            room.copy(
                lebenslauf = neueHistorie,
                letztePruefung = inspection.datum,
                gueltigBis = neuesGueltigBis?.takeIf { it.isNotBlank() } ?: room.gueltigBis
            )
        )
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
