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
     * Vermerk mit aktuellem Datum (und optionalem Grund) in den Lebenslauf
     * geschrieben; beim Aufheben wird der heutige Vermerk wieder entfernt.
     */
    suspend fun setKeinZutritt(roomId: String, gesperrt: Boolean, grund: String = "") {
        val basis = "${Dates.todayGerman()}: Zimmer konnte nicht betreten werden"
        val vermerk = if (grund.isBlank()) basis else "$basis ($grund)"
        fun istHeutigerVermerk(zeile: String) = zeile.trim().startsWith(basis)
        db.withTransaction {
            val room = db.tvRoomDao().getById(roomId)
            if (gesperrt) {
                db.roomSperreDao().upsert(RoomSperre(roomId, Dates.todayIso(), grund.trim()))
                if (room != null) {
                    // Bereits vorhandenen heutigen Vermerk ersetzen (z. B. Grund nachgetragen)
                    val zeilen = room.lebenslauf.lines().filterNot { istHeutigerVermerk(it) }
                    val neu = (zeilen.filter { it.isNotBlank() } + vermerk).joinToString("\n")
                    db.tvRoomDao().update(room.copy(lebenslauf = neu))
                }
                logAction(roomId, "Kein Zutritt vermerkt" + if (grund.isBlank()) "" else " ($grund)")
            } else {
                db.roomSperreDao().delete(roomId)
                if (room != null && room.lebenslauf.lines().any { istHeutigerVermerk(it) }) {
                    val neu = room.lebenslauf.lines()
                        .filterNot { istHeutigerVermerk(it) }
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

    /** Neues Zimmer (und damit ggf. eine neue Station) anlegen. ID = Station_Zimmer. */
    suspend fun createRoom(room: TvRoom) {
        require(db.tvRoomDao().getById(room.id) == null) {
            "Zimmer ${room.id} existiert bereits"
        }
        db.withTransaction {
            db.tvRoomDao().insert(
                room.copy(
                    lebenslauf = "${Dates.todayGerman()}: Zimmer in der App angelegt"
                )
            )
            logAction(room.id, "Zimmer angelegt")
        }
    }

    /** Zimmer inaktiv setzen (archivieren) oder reaktivieren – Historie bleibt erhalten. */
    suspend fun setInaktiv(roomId: String, inaktiv: Boolean) {
        db.withTransaction {
            val room = db.tvRoomDao().getById(roomId) ?: return@withTransaction
            val vermerk = if (inaktiv) "${Dates.todayGerman()}: Zimmer inaktiv gesetzt (TV abgebaut/aufgelöst)"
            else "${Dates.todayGerman()}: Zimmer reaktiviert"
            val neu = if (room.lebenslauf.isBlank()) vermerk
            else room.lebenslauf.trimEnd() + "\n" + vermerk
            db.tvRoomDao().update(room.copy(inaktiv = inaktiv, lebenslauf = neu))
            logAction(roomId, if (inaktiv) "Zimmer inaktiv gesetzt" else "Zimmer reaktiviert")
        }
    }

    // ----- Materialkatalog & Bestand -----

    val materialien: Flow<List<Material>> get() = db.materialDao().observeAll()
    val aktiveMaterialien: Flow<List<Material>> get() = db.materialDao().observeAktive()

    suspend fun addMaterial(name: String, bestandAktiv: Boolean) {
        val maxSort = db.materialDao().getAll().maxOfOrNull { it.sortIndex } ?: 0
        db.materialDao().insert(
            Material(name = name.trim(), bestandAktiv = bestandAktiv, sortIndex = maxSort + 1)
        )
    }

    suspend fun updateMaterial(material: Material) = db.materialDao().update(material)

    // ----- Eigene Prüfpunkte -----

    val customPruefpunkte: Flow<List<CustomPruefpunkt>> get() = db.customPruefpunktDao().observeAll()
    val aktiveCustomPruefpunkte: Flow<List<CustomPruefpunkt>> get() = db.customPruefpunktDao().observeAktive()

    suspend fun addPruefpunkt(titel: String) {
        db.customPruefpunktDao().insert(CustomPruefpunkt(titel = titel.trim()))
    }

    suspend fun updatePruefpunkt(punkt: CustomPruefpunkt) = db.customPruefpunktDao().update(punkt)

    // ----- Stundenzettel -----

    val stundenzettel: Flow<List<StundenzettelEntity>> get() = db.stundenzettelDao().observeAll()

    suspend fun getStundenzettel(station: String, zeitraumStart: String): StundenzettelEntity? =
        db.stundenzettelDao().getFor(station, zeitraumStart)

    suspend fun getStundenzettelById(id: Long): StundenzettelEntity? =
        db.stundenzettelDao().getById(id)

    /**
     * Zeitfenster eines Stundenzettels: von seinem Zeitraumbeginn bis zum
     * Beginn des nächsten Zettels derselben Station (exklusiv), sonst offen.
     */
    suspend fun zettelFenster(zettel: StundenzettelEntity): Pair<String, String?> {
        val ende = db.stundenzettelDao().getAll()
            .filter { it.station == zettel.station && it.zeitraumStart > zettel.zeitraumStart }
            .minOfOrNull { it.zeitraumStart }
        return zettel.zeitraumStart to ende
    }

    suspend fun saveStundenzettel(zettel: StundenzettelEntity): StundenzettelEntity {
        val id = db.stundenzettelDao().upsert(zettel)
        return if (zettel.id == 0L) zettel.copy(id = id) else zettel
    }

    /** Fortlaufende Auftragsnummer, z. B. "A-2026-0007". */
    suspend fun naechsteAuftragsnummer(): String {
        val nr = db.stundenzettelDao().count() + 1
        return "A-%s-%04d".format(java.time.LocalDate.now().year, nr)
    }

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
            // Lagerbestand: verbrauchtes Material automatisch abziehen
            inspection.arbeitenListe().forEach { db.materialDao().verbrauche(it) }
            logAction(inspection.roomId, "Prüfbogen gespeichert")
        }
    }

    /** Materialkatalog beim ersten Start (bzw. nach dem Update) vorbelegen. */
    suspend fun seedMaterialienIfEmpty() {
        if (db.materialDao().count() > 0) return
        Arbeiten.SEED.forEachIndexed { index, (name, mitBestand) ->
            db.materialDao().insert(
                Material(name = name, bestandAktiv = mitBestand, sortIndex = index)
            )
        }
    }

    /** Lädt die mitgelieferten Stammdaten (Stand der KKH-Übersicht), falls die DB leer ist. */
    suspend fun seedIfEmpty(context: Context) {
        seedMaterialienIfEmpty()
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
