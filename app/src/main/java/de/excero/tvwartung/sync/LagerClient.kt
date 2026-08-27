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
}
