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
    private val signatureStore: de.excero.tvwartung.files.SignatureStore?,
    private val settingsStore: de.excero.tvwartung.data.SettingsStore?,
    private val serverUrl: String,
    private val apiKey: String
) {

    data class Ergebnis(
        val zimmerGesendet: Int,
        val zimmerEmpfangen: Int,
        val pruefungen: Int,
        val zettel: Int,
        val dateien: Int,
        val kollegenBerichte: Int = 0,
        val hinweis: String = ""
    ) {
        fun meldung(): String =
            "Sync ok: $zimmerGesendet Zimmer ↑, $zimmerEmpfangen Zimmer ↓, " +
                "$pruefungen Prüfbögen ↑, $kollegenBerichte Berichte ↓, " +
                "$zettel Stundenzettel, $dateien Dateien" + hinweis
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
                put("mitarbeiter", insp.mitarbeiter)
                put("geloescht", insp.geloescht)
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

        // --- 4b) Team-Stundenzettel-Einträge: Push (LWW) und Pull ---
        var kollegenBerichte = 0
        runCatching {
            // Header vom Server ziehen (Web-Edits → alle Geräte)
            val serverZettel = httpJson("/api/sync/stundenzettel", "GET")
                .optJSONArray("zettel") ?: JSONArray()
            for (i in 0 until serverZettel.length()) {
                val o = serverZettel.getJSONObject(i)
                repository.applyStundenzettel(
                    de.excero.tvwartung.data.StundenzettelEntity(
                        station = o.optString("station"),
                        zeitraumStart = o.optString("zeitraumStart"),
                        auftragsnummer = o.optString("auftragsnummer"),
                        datum = o.optString("datum"),
                        stunden = o.optString("stunden"),
                        anfahrt = o.optString("anfahrt"),
                        techniker = o.optString("techniker"),
                        updatedAt = o.optString("updatedAt")
                    )
                )
            }

            val eintraegeJson = JSONArray()
            repository.getAllEintraege().forEach { e ->
                eintraegeJson.put(JSONObject().apply {
                    put("station", e.station); put("zeitraumStart", e.zeitraumStart)
                    put("mitarbeiter", e.mitarbeiter); put("stunden", e.stunden)
                    put("anfahrt", e.anfahrt); put("updatedAt", e.updatedAt)
                })
            }
            httpJson("/api/sync/zettel-eintraege", "POST", JSONObject().put("eintraege", eintraegeJson))
            val serverEintraege = httpJson("/api/sync/zettel-eintraege", "GET")
                .optJSONArray("eintraege") ?: JSONArray()
            val lokalEintraege = repository.getAllEintraege()
                .associateBy { "${it.station}|${it.zeitraumStart}|${it.mitarbeiter}" }
            for (i in 0 until serverEintraege.length()) {
                val o = serverEintraege.getJSONObject(i)
                val key = "${o.optString("station")}|${o.optString("zeitraumStart")}|${o.optString("mitarbeiter")}"
                val eigener = lokalEintraege[key]
                if (eigener == null || o.optString("updatedAt") > eigener.updatedAt) {
                    repository.applyEintrag(
                        de.excero.tvwartung.data.StundenzettelEintrag(
                            station = o.optString("station"),
                            zeitraumStart = o.optString("zeitraumStart"),
                            mitarbeiter = o.optString("mitarbeiter"),
                            stunden = o.optString("stunden"),
                            anfahrt = o.optString("anfahrt"),
                            updatedAt = o.optString("updatedAt")
                        )
                    )
                }
            }

            // --- 4c) Berichte der Kollegen holen (Delta über lastSync) ---
            val since = settingsStore?.settings?.value?.lastSync ?: ""
            val pfad = if (since.isBlank()) "/api/sync/inspections"
            else "/api/sync/inspections?since=" + URLEncoder.encode(since, "UTF-8")
            val serverInsp = httpJson(pfad, "GET").optJSONArray("inspections") ?: JSONArray()
            val fremde = mutableListOf<de.excero.tvwartung.data.Inspection>()
            for (i in 0 until serverInsp.length()) {
                val o = serverInsp.getJSONObject(i)
                fremde.add(inspectionAusJson(o))
            }
            kollegenBerichte = repository.importInspections(fremde)

            // --- 4d) Mitarbeiterliste vom Server (für die Geräteeinrichtung) ---
            val maJson = httpJson("/api/sync/mitarbeiter", "GET")
                .optJSONArray("mitarbeiter") ?: JSONArray()
            val namen = buildList {
                for (i in 0 until maJson.length()) {
                    val o = maJson.getJSONObject(i)
                    if (o.optBoolean("aktiv", true)) add(o.optString("name"))
                }
            }
            settingsStore?.setBekannteMitarbeiter(namen)
        }

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
        // Unterschriften ebenfalls hochladen (unter _signaturen/, kollidiert nicht mit Zimmern)
        signatureStore?.alleDateien()?.forEach { f ->
            val rel = "_signaturen/${f.name}"
            if ("$rel|${f.length()}" !in vorhandene) {
                ladeDateiHoch(rel, f)
                hochgeladen++
            }
        }

        // --- 6) Vollständigkeit: Sperren, Material, Prüfpunkte, Aktivität ---
        // Prinzip: Es wird immer alles übertragen; was die Web-Seite damit
        // anzeigt, entscheidet sie selbst. Tolerant gegenüber älteren Servern.
        var hinweis = ""
        runCatching {
            val sperrenJson = JSONArray()
            repository.getAllSperren().forEach { sp ->
                sperrenJson.put(JSONObject().apply {
                    put("roomId", sp.roomId); put("gesperrtAm", sp.gesperrtAm); put("grund", sp.grund)
                })
            }
            httpJson("/api/sync/sperren", "POST", JSONObject().put("sperren", sperrenJson))

            val materialJson = JSONArray()
            repository.getAllMaterial().forEach { m ->
                materialJson.put(JSONObject().apply {
                    put("name", m.name); put("bestand", m.bestand)
                    put("bestandAktiv", m.bestandAktiv); put("aktiv", m.aktiv)
                    put("sortIndex", m.sortIndex)
                })
            }
            httpJson("/api/sync/material", "POST", JSONObject().put("material", materialJson))

            val punkteJson = JSONArray()
            repository.getAllPruefpunkte().forEach { pp ->
                punkteJson.put(JSONObject().apply {
                    put("titel", pp.titel); put("aktiv", pp.aktiv); put("sortIndex", pp.sortIndex)
                })
            }
            httpJson("/api/sync/pruefpunkte", "POST", JSONObject().put("punkte", punkteJson))

            val aktJson = JSONArray()
            repository.getAllActivity().forEach { a ->
                aktJson.put(JSONObject().apply {
                    put("roomId", a.roomId); put("zeitpunkt", a.zeitpunkt); put("aktion", a.aktion)
                })
            }
            httpJson("/api/sync/aktivitaet", "POST", JSONObject().put("eintraege", aktJson))
        }.onFailure {
            hinweis = " – Hinweis: Server-Update nötig für Sperren/Material/Aktivität"
        }

        settingsStore?.let { st -> st.update(st.settings.value.copy(lastSync = de.excero.tvwartung.util.Dates.nowIsoDateTime())) }
        return Ergebnis(gesendet, empfangen, neuePruefungen, zettelListe.size, hochgeladen, kollegenBerichte, hinweis)
    }

    /** Server-JSON → Inspection: Standardpunkte über die Titel zuordnen, Rest = eigene Punkte. */
    private fun inspectionAusJson(o: JSONObject): de.excero.tvwartung.data.Inspection {
        val punkte = o.optJSONArray("punkte") ?: JSONArray()
        val map = mutableMapOf<String, Pair<Boolean?, String>>()
        val extras = mutableListOf<Triple<String, Boolean?, String>>()
        val standard = listOf(
            "Empfang vorhanden?", "Seriennummer TV stimmt?", "Freenet TV-ID stimmt?",
            "DVD-Test", "Fernbedienung", "Halterung (fest?)",
            "Gültigkeit Freenet > 3 Monate?", "Freenet verlängert"
        )
        for (i in 0 until punkte.length()) {
            val p = punkte.getJSONObject(i)
            val titel = p.optString("titel")
            val ergebnis: Boolean? = if (p.isNull("ergebnis")) null else p.optBoolean("ergebnis")
            val bemerkung = p.optString("bemerkung")
            if (titel in standard && titel !in map) map[titel] = ergebnis to bemerkung
            else extras.add(Triple(titel, ergebnis, bemerkung))
        }
        fun e(t: String) = map[t]?.first
        fun b(t: String) = map[t]?.second ?: ""
        val arbeitenArr = o.optJSONArray("arbeiten") ?: JSONArray()
        val arbeiten = buildList { for (i in 0 until arbeitenArr.length()) add(arbeitenArr.optString(i)) }
        return de.excero.tvwartung.data.Inspection(
            roomId = o.optString("roomId"),
            datum = o.optString("datum"),
            empfangVorhanden = e(standard[0]),
            seriennummerStimmt = e(standard[1]),
            freenetIdStimmt = e(standard[2]),
            dvdTest = e(standard[3]),
            fernbedienung = e(standard[4]),
            halterungFest = e(standard[5]),
            gueltigkeitAusreichend = e(standard[6]),
            freenetVerlaengert = e(standard[7]),
            bemerkungEmpfang = b(standard[0]),
            bemerkungSeriennummer = b(standard[1]),
            bemerkungFreenetId = b(standard[2]),
            bemerkungDvd = b(standard[3]),
            bemerkungFernbedienung = b(standard[4]),
            bemerkungHalterung = b(standard[5]),
            bemerkungen = o.optString("bemerkungen"),
            arbeiten = arbeiten.joinToString("\n"),
            extraPunkte = de.excero.tvwartung.data.Inspection.extraPunkteJson(extras),
            uuid = o.optString("uuid"),
            mitarbeiter = o.optString("mitarbeiter"),
            geloescht = o.optBoolean("geloescht", false)
        )
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
