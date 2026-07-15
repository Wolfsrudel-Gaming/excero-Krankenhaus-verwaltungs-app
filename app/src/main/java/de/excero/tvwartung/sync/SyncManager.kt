package de.excero.tvwartung.sync

import de.excero.tvwartung.data.Repository
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.files.PhotoStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Synchronisation mit dem KKH-Server:
 *  - Zimmer/Stationen: bidirektional (last-write-wins über updatedAt) –
 *    Änderungen aus der Weboberfläche landen in der App und umgekehrt
 *  - Prüfbögen & Stundenzettel: Push zum Server (dort einsehbar/auswertbar)
 *  - Fotos & Prüfbericht-PDFs: Upload fehlender Dateien
 */
class SyncManager(
    private val repository: Repository,
    private val photoStore: PhotoStore,
    private val serverUrl: String,
    private val apiKey: String
) {

    data class Ergebnis(
        val zimmerGesendet: Int,
        val zimmerEmpfangen: Int,
        val pruefungen: Int,
        val zettel: Int,
        val dateien: Int
    ) {
        fun meldung(): String =
            "Sync ok: $zimmerGesendet Zimmer ↑, $zimmerEmpfangen Zimmer ↓, " +
                "$pruefungen Prüfbögen, $zettel Stundenzettel, $dateien Dateien"
    }

    private val basis = serverUrl.trimEnd('/')

    private fun verbinde(pfad: String, methode: String): HttpURLConnection {
        val conn = URL("$basis$pfad").openConnection() as HttpURLConnection
        conn.requestMethod = methode
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        return conn
    }

    private fun httpJson(pfad: String, methode: String, body: JSONObject? = null): JSONObject {
        val conn = verbinde(pfad, methode)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val fehler = runCatching { JSONObject(text).optString("error") }.getOrNull()
            error(fehler?.ifBlank { null } ?: "Server-Fehler $code")
        }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    /** Kompletten Abgleich ausführen (in IO-Kontext aufrufen). */
    suspend fun sync(): Ergebnis {
        require(basis.startsWith("http")) { "Server-URL fehlt oder ist ungültig" }
        require(apiKey.isNotBlank()) { "API-Schlüssel fehlt" }

        // --- 1) Zimmer: Server-Stand holen und zusammenführen (LWW) ---
        val serverRooms = httpJson("/api/sync/rooms", "GET")
            .optJSONArray("rooms") ?: JSONArray()
        val lokal = repository.allRooms().associateBy { it.id }.toMutableMap()

        var empfangen = 0
        val uebernehmen = mutableListOf<TvRoom>()
        for (i in 0 until serverRooms.length()) {
            val o = serverRooms.getJSONObject(i)
            val id = o.optString("id")
            val server = TvRoom(
                id = id,
                station = o.optString("station"),
                zimmer = o.optString("zimmer"),
                lebenslauf = o.optString("lebenslauf"),
                letztePruefung = o.optString("letztePruefung"),
                tvTyp = o.optString("tvTyp"),
                seriennummer = o.optString("seriennummer"),
                freenetId = o.optString("freenetId"),
                gueltigBis = o.optString("gueltigBis"),
                inaktiv = o.optBoolean("inaktiv"),
                updatedAt = o.optString("updatedAt")
            )
            val eigenes = lokal[id]
            if (eigenes == null || server.updatedAt > eigenes.updatedAt) {
                uebernehmen.add(server)
                empfangen++
            }
        }
        if (uebernehmen.isNotEmpty()) repository.importRooms(uebernehmen)

        // --- 2) Zimmer zum Server schicken (Server führt ebenfalls LWW aus) ---
        val alleLokal = repository.allRooms()
        val roomsJson = JSONArray()
        alleLokal.forEach { r ->
            roomsJson.put(JSONObject().apply {
                put("id", r.id); put("station", r.station); put("zimmer", r.zimmer)
                put("lebenslauf", r.lebenslauf); put("letztePruefung", r.letztePruefung)
                put("tvTyp", r.tvTyp); put("seriennummer", r.seriennummer)
                put("freenetId", r.freenetId); put("gueltigBis", r.gueltigBis)
                put("inaktiv", r.inaktiv); put("updatedAt", r.updatedAt)
            })
        }
        val roomsAntwort = httpJson(
            "/api/sync/rooms", "POST", JSONObject().put("rooms", roomsJson)
        )
        val gesendet = roomsAntwort.optInt("uebernommen", alleLokal.size)

        // --- 3) Prüfbögen pushen (Server dedupliziert über die UUID) ---
        val inspections = repository.allInspections()
        val inspJson = JSONArray()
        inspections.forEach { insp ->
            inspJson.put(JSONObject().apply {
                put("uuid", insp.uuid)
                put("roomId", insp.roomId)
                put("datum", insp.datum)
                val punkte = JSONArray()
                insp.punkte().forEach { (t, e, b) ->
                    punkte.put(JSONObject().apply {
                        put("titel", t)
                        if (e == null) put("ergebnis", JSONObject.NULL) else put("ergebnis", e)
                        put("bemerkung", b)
                    })
                }
                insp.extraPunkteListe().forEach { (t, e, b) ->
                    punkte.put(JSONObject().apply {
                        put("titel", t)
                        if (e == null) put("ergebnis", JSONObject.NULL) else put("ergebnis", e)
                        put("bemerkung", b)
                    })
                }
                put("punkte", punkte)
                put("arbeiten", JSONArray(insp.arbeitenListe()))
                put("bemerkungen", insp.bemerkungen)
            })
        }
        val inspAntwort = httpJson(
            "/api/sync/inspections", "POST", JSONObject().put("inspections", inspJson)
        )
        val neuePruefungen = inspAntwort.optInt("neu", 0)

        // --- 4) Stundenzettel pushen (LWW über station+zeitraumStart) ---
        val zettelListe = repository.getAllStundenzettel()
        val zettelJson = JSONArray()
        zettelListe.forEach { z ->
            zettelJson.put(JSONObject().apply {
                put("station", z.station); put("zeitraumStart", z.zeitraumStart)
                put("auftragsnummer", z.auftragsnummer); put("datum", z.datum)
                put("stunden", z.stunden); put("anfahrt", z.anfahrt)
                put("techniker", z.techniker); put("updatedAt", z.updatedAt)
            })
        }
        httpJson("/api/sync/stundenzettel", "POST", JSONObject().put("zettel", zettelJson))

        // --- 5) Fehlende Dateien hochladen (Fotos + Prüfbericht-PDFs) ---
        val vorhandene = mutableSetOf<String>()
        val dateiListe = httpJson("/api/sync/files", "GET").optJSONArray("files") ?: JSONArray()
        for (i in 0 until dateiListe.length()) {
            val o = dateiListe.getJSONObject(i)
            vorhandene.add("${o.optString("path")}|${o.optLong("size")}")
        }
        var hochgeladen = 0
        val root = photoStore.rootDir()
        root.walkTopDown().filter { it.isFile && it.length() > 0 }.forEach { f ->
            val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
            if ("$rel|${f.length()}" !in vorhandene) {
                ladeDateiHoch(rel, f)
                hochgeladen++
            }
        }

        return Ergebnis(gesendet, empfangen, neuePruefungen, zettelListe.size, hochgeladen)
    }

    private fun ladeDateiHoch(relPfad: String, datei: File) {
        val kodiert = URLEncoder.encode(relPfad, "UTF-8")
        val conn = verbinde("/api/sync/file?path=$kodiert", "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(datei.length())
        conn.outputStream.use { out -> datei.inputStream().use { it.copyTo(out) } }
        val code = conn.responseCode
        conn.inputStream?.close()
        if (code !in 200..299) error("Datei-Upload fehlgeschlagen ($code): $relPfad")
    }
}
