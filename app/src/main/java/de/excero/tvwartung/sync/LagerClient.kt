package de.excero.tvwartung.sync

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lieferant (nur lesend in der App; gepflegt im Web-Lager). */
data class Lieferant(
    val name: String,
    val kontakt: String,
    val telefon: String,
    val email: String,
    val kundennummer: String,
    val notiz: String
)

/** Lager-Artikel aus dem Web-Lager (nur lesend in der App). */
data class LagerArtikel(
    val bezeichnung: String,
    val artikelnummer: String,
    val kategorie: String,
    val einheit: String,
    val ekPreis: Double?,
    val vkPreis: Double?,
    val bestand: Double,
    val mindestbestand: Double,
    val appMaterialName: String,
    val lieferant: String
) {
    /** Bestand unter dem Mindestbestand → nachbestellen. */
    val nachbestellen: Boolean get() = mindestbestand > 0 && bestand < mindestbestand
}

/** Liest Lager-Zusatzdaten (Lieferanten) read-only vom Server. */
class LagerClient(serverUrl: String, private val apiKey: String) {

    private val basis = serverUrl.trimEnd('/')

    fun lieferanten(): List<Lieferant> {
        val conn = URL("$basis/api/sync/lieferanten").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val code = conn.responseCode
        if (code !in 200..299) error("Server-Fehler $code")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(text).optJSONArray("lieferanten") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Lieferant(
                        name = o.optString("name"),
                        kontakt = o.optString("kontakt"),
                        telefon = o.optString("telefon"),
                        email = o.optString("email"),
                        kundennummer = o.optString("kundennummer"),
                        notiz = o.optString("notiz")
                    )
                )
            }
        }
    }

    /** Vollständiger Lager-Artikelkatalog aus dem Web-Lager. */
    fun artikel(): List<LagerArtikel> {
        val conn = URL("$basis/api/sync/lager-artikel").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val code = conn.responseCode
        if (code !in 200..299) error("Server-Fehler $code")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(text).optJSONArray("artikel") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    LagerArtikel(
                        bezeichnung = o.optString("bezeichnung"),
                        artikelnummer = o.optString("artikelnummer"),
                        kategorie = o.optString("kategorie"),
                        einheit = o.optString("einheit"),
                        ekPreis = if (o.isNull("ekPreis")) null else o.optDouble("ekPreis"),
                        vkPreis = if (o.isNull("vkPreis")) null else o.optDouble("vkPreis"),
                        bestand = o.optDouble("bestand", 0.0),
                        mindestbestand = o.optDouble("mindestbestand", 0.0),
                        appMaterialName = o.optString("appMaterialName"),
                        lieferant = o.optString("lieferant")
                    )
                )
            }
        }
    }
}
