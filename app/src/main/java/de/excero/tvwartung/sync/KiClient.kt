package de.excero.tvwartung.sync

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Ein erkanntes Feld (Seriennummer, Freenet-ID, Gültig-bis, TV-Typ) samt Konfidenz. */
data class KiFeldWert(val wert: String, val konfidenz: Double?)

/** Abgleich eines Feldes mit den Zimmer-Stammdaten. */
data class KiAbgleich(val stammdaten: String, val passt: Boolean?)

data class KiAnalyse(
    val id: Int,
    val pfad: String,
    val roomId: String,
    val bildtyp: String,
    val felder: Map<String, KiFeldWert>,
    val abgleich: Map<String, KiAbgleich>,
    val status: String,
    val modellVersion: String,
    val fehler: String,
    val erstelltAm: String,
    val analysiertAm: String
) {
    /** Kurztext der erkannten Werte für die Listenansicht. */
    fun erkanntKurz(): String =
        felder.entries.joinToString(" · ") { (feld, w) -> "${KiFelder.NAME[feld] ?: feld}: ${w.wert}" }
}

/** Entscheidung zu einem Feld: übernommener Wert + ob die Stammdaten aktualisiert werden sollen. */
data class KiEntscheidung(val wert: String, val stammdatenUebernehmen: Boolean)

object KiFelder {
    val ALLE = listOf("seriennummer", "freenet_id", "gueltig_bis", "tv_typ")
    val NAME = mapOf(
        "seriennummer" to "Seriennummer",
        "freenet_id" to "Freenet-ID",
        "gueltig_bis" to "Gültig bis",
        "tv_typ" to "TV-Typ"
    )
}

/** Schlanker Client für die KI-Prüfung (Endpunkte unter /api/sync/ki/…), analog zum Web-Panel-Workflow. */
class KiClient(private val serverUrl: String, private val apiKey: String) {

    private val basis = serverUrl.trimEnd('/')

    private fun verbinde(pfad: String, methode: String): HttpURLConnection {
        val conn = URL("$basis$pfad").openConnection() as HttpURLConnection
        conn.requestMethod = methode
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
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

    /** Analysen laden, optional gefiltert nach Status ("abweichung", …) und/oder Zimmer. */
    fun analysen(status: String? = null, room: String? = null): List<KiAnalyse> {
        val query = buildList {
            if (!status.isNullOrBlank()) add("status=$status")
            if (!room.isNullOrBlank()) add("room=" + java.net.URLEncoder.encode(room, "UTF-8"))
        }.joinToString("&")
        val pfad = "/api/sync/ki/analysen" + (if (query.isNotEmpty()) "?$query" else "")
        val arr = httpJson(pfad, "GET").optJSONArray("analysen") ?: org.json.JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val felderJson = o.optJSONObject("felder") ?: JSONObject()
                val felder = KiFelder.ALLE.mapNotNull { feld ->
                    val fo = felderJson.optJSONObject(feld) ?: return@mapNotNull null
                    val wert = fo.optString("wert")
                    if (wert.isBlank()) return@mapNotNull null
                    feld to KiFeldWert(wert, fo.optDouble("konfidenz").takeIf { !it.isNaN() })
                }.toMap()
                val abgleichJson = o.optJSONObject("abgleich") ?: JSONObject()
                val abgleich = KiFelder.ALLE.mapNotNull { feld ->
                    val ao = abgleichJson.optJSONObject(feld) ?: return@mapNotNull null
                    val stamm = ao.optString("stammdaten")
                    if (stamm.isBlank()) return@mapNotNull null
                    val passt: Boolean? = if (ao.isNull("passt")) null else ao.optBoolean("passt")
                    feld to KiAbgleich(stamm, passt)
                }.toMap()
                add(
                    KiAnalyse(
                        id = o.optInt("id"),
                        pfad = o.optString("pfad"),
                        roomId = o.optString("roomId"),
                        bildtyp = o.optString("bildtyp"),
                        felder = felder,
                        abgleich = abgleich,
                        status = o.optString("status"),
                        modellVersion = o.optString("modellVersion"),
                        fehler = o.optString("fehler"),
                        erstelltAm = o.optString("erstelltAm"),
                        analysiertAm = o.optString("analysiertAm")
                    )
                )
            }
        }
    }

    /** Entscheidungen speichern – wird zum Trainingsbeispiel für die KI-Netze. */
    fun bestaetigen(analyseId: Int, entscheidungen: Map<String, KiEntscheidung>) {
        val json = JSONObject()
        val eJson = JSONObject()
        entscheidungen.forEach { (feld, e) ->
            eJson.put(feld, JSONObject().apply {
                put("wert", e.wert)
                put("stammdatenUebernehmen", e.stammdatenUebernehmen)
            })
        }
        json.put("entscheidungen", eJson)
        httpJson("/api/sync/ki/analysen/$analyseId/bestaetigen", "POST", json)
    }

    fun neuAnalysieren(analyseId: Int) {
        httpJson("/api/sync/ki/analysen/$analyseId/neu", "POST")
    }

    /** Anzahl offener Abweichungen (für die Dashboard-Kachel), best effort. */
    fun anzahlAbweichungen(): Int = runCatching { analysen("abweichung").size }.getOrDefault(0)

    /** Ein Foto gezielt herunterladen (z. B. für die Detailansicht). */
    fun ladeFoto(pfad: String, zielDatei: File) {
        val conn = verbinde("/api/sync/file?path=${java.net.URLEncoder.encode(pfad, "UTF-8")}", "GET")
        val code = conn.responseCode
        if (code !in 200..299) error("Foto konnte nicht geladen werden ($code)")
        conn.inputStream.use { input -> zielDatei.outputStream().use { input.copyTo(it) } }
    }
}
