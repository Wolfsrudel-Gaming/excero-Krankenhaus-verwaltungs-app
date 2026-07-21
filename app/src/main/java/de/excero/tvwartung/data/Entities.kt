package de.excero.tvwartung.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein Fernseher-Standort (Zimmer) mit den in der KKH-Übersicht hinterlegten Stammdaten.
 * Datumsfelder werden ISO-formatiert gespeichert (yyyy-MM-dd), leere Strings bedeuten "nicht gesetzt".
 */
@Entity(tableName = "tv_rooms")
data class TvRoom(
    @PrimaryKey val id: String,          // z. B. "A4_01a" (Station_Zimmer)
    val station: String,                 // z. B. "A4"
    val zimmer: String,                  // z. B. "01a"
    val lebenslauf: String,              // Historie, ein Eintrag pro Zeile
    val letztePruefung: String,          // ISO-Datum oder ""
    val tvTyp: String,
    val seriennummer: String,
    val freenetId: String,
    val gueltigBis: String,              // ISO-Datum oder ""
    @ColumnInfo(defaultValue = "0")
    val inaktiv: Boolean = false,        // Zimmer aufgelöst/TV abgebaut – bleibt mit Historie erhalten
    @ColumnInfo(defaultValue = "")
    val updatedAt: String = ""           // letzte Änderung (ISO-Datum+Zeit) für die Server-Synchronisation
)

/**
 * Ein ausgefüllter Prüfbogen ("Prüfung TV-Empfangsgeräte").
 * Die Prüfpunkte entsprechen 1:1 dem Papierbogen; null = nicht geprüft, true = i.O., false = n.i.O.
 */
@Entity(tableName = "inspections")
data class Inspection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val datum: String,                   // ISO-Datum der Prüfung
    val empfangVorhanden: Boolean?,
    val seriennummerStimmt: Boolean?,
    val freenetIdStimmt: Boolean?,
    val dvdTest: Boolean?,
    val fernbedienung: Boolean?,
    val halterungFest: Boolean?,
    val gueltigkeitAusreichend: Boolean?, // Gültigkeit Freenet > 3 Monate?
    val freenetVerlaengert: Boolean?,
    val bemerkungEmpfang: String = "",
    val bemerkungSeriennummer: String = "",
    val bemerkungFreenetId: String = "",
    val bemerkungDvd: String = "",
    val bemerkungFernbedienung: String = "",
    val bemerkungHalterung: String = "",
    val bemerkungen: String = "",        // Freitext unten auf dem Bogen
    val arbeiten: String = "",           // durchgeführte Arbeiten / verbautes Material, ein Eintrag pro Zeile
    @ColumnInfo(defaultValue = "")
    val extraPunkte: String = "",        // eigene Prüfpunkte als JSON: [{"t":Titel,"e":true/false/null,"b":Bemerkung}]
    @ColumnInfo(defaultValue = "")
    val uuid: String = "",               // geräteübergreifend eindeutige ID für die Server-Synchronisation
    @ColumnInfo(defaultValue = "")
    val mitarbeiter: String = "",        // wer geprüft hat (Gerät = Mitarbeiter)
    @ColumnInfo(defaultValue = "0")
    val geloescht: Boolean = false       // Papierkorb: ausgeblendet statt endgültig weg
) {
    /** Prüfpunkte in Bogen-Reihenfolge: Titel, Ergebnis, Bemerkung. */
    fun punkte(): List<Triple<String, Boolean?, String>> = listOf(
        Triple("Empfang vorhanden?", empfangVorhanden, bemerkungEmpfang),
        Triple("Seriennummer TV stimmt?", seriennummerStimmt, bemerkungSeriennummer),
        Triple("Freenet TV-ID stimmt?", freenetIdStimmt, bemerkungFreenetId),
        Triple("DVD-Test", dvdTest, bemerkungDvd),
        Triple("Fernbedienung", fernbedienung, bemerkungFernbedienung),
        Triple("Halterung (fest?)", halterungFest, bemerkungHalterung),
        Triple("Gültigkeit Freenet > 3 Monate?", gueltigkeitAusreichend, ""),
        Triple("Freenet verlängert", freenetVerlaengert, "")
    )

    /** Durchgeführte Arbeiten / verbautes Material als Liste. */
    fun arbeitenListe(): List<String> =
        arbeiten.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Eigene Prüfpunkte: Titel, Ergebnis, Bemerkung (aus JSON). */
    fun extraPunkteListe(): List<Triple<String, Boolean?, String>> {
        if (extraPunkte.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(extraPunkte)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val e: Boolean? = if (o.isNull("e")) null else o.getBoolean("e")
                    add(Triple(o.optString("t"), e, o.optString("b")))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        /** Eigene Prüfpunkte als JSON serialisieren. */
        fun extraPunkteJson(punkte: List<Triple<String, Boolean?, String>>): String {
            if (punkte.isEmpty()) return ""
            val arr = JSONArray()
            punkte.forEach { (t, e, b) ->
                arr.put(JSONObject().apply {
                    put("t", t)
                    if (e == null) put("e", JSONObject.NULL) else put("e", e)
                    put("b", b)
                })
            }
            return arr.toString()
        }
    }
}

/**
 * "Kein Zutritt"-Vermerk: Von der Stationsschwester gemeldete Zimmer, die bei
 * dieser Anfahrt nicht betreten werden dürfen. Gilt nur innerhalb des
 * eingestellten Prüfzeitraums und läuft danach automatisch ab.
 */
@Entity(tableName = "room_sperren")
data class RoomSperre(
    @PrimaryKey val roomId: String,
    val gesperrtAm: String,              // ISO-Datum der Meldung
    @ColumnInfo(defaultValue = "")
    val grund: String = ""               // optional, z. B. "Isolation"
)

/**
 * Materialkatalog inkl. einfachem Lagerbestand. Die aktiven Einträge erscheinen
 * im Prüfbogen als ankreuzbare Arbeiten; bei Einträgen mit Bestandsführung wird
 * der Bestand beim Speichern eines Prüfbogens automatisch reduziert.
 */
@Entity(tableName = "materialien")
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                    // z. B. "Fernbedienung getauscht"
    val bestand: Int = 0,
    val bestandAktiv: Boolean = false,   // true = Verbrauch wird vom Bestand abgezogen
    val aktiv: Boolean = true,           // false = erscheint nicht mehr im Prüfbogen
    val sortIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val updatedAt: String = ""           // letzte Bestandsänderung (LWW, bidirektionaler Sync mit dem Web-Lager)
)

/** Selbst angelegte, zusätzliche Prüfpunkte für den Prüfbogen. */
@Entity(tableName = "custom_pruefpunkte")
data class CustomPruefpunkt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titel: String,
    val aktiv: Boolean = true,
    val sortIndex: Int = 0
)

/**
 * Gespeicherter Stundenzettel je Station und Prüfzeitraum. Typischer Ablauf:
 * Unterschrift auf der Station einholen (wird mitgespeichert), Stunden später
 * eintragen und erst dann das PDF exportieren.
 */
@Entity(tableName = "stundenzettel")
data class StundenzettelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val station: String,
    val zeitraumStart: String,           // ISO-Datum des Zeitraumbeginns
    val auftragsnummer: String = "",
    val datum: String = "",              // Tag der Leistung (deutsch, frei editierbar)
    val stunden: String = "",            // Arbeitsstunden, z. B. "3,5"
    val anfahrt: String = "",            // Anfahrt in Stunden (Freitext)
    val techniker: String = "",
    @ColumnInfo(defaultValue = "")
    val updatedAt: String = ""           // letzte Änderung für die Server-Synchronisation
)

/**
 * Internes Aktivitätsprotokoll: wann wurde welches Zimmer bearbeitet.
 * Wird bewusst NICHT exportiert – nur zur eigenen Einsicht in der App.
 */
@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val zeitpunkt: String,               // ISO-Datum+Zeit, z. B. 2026-07-14T10:32:05
    val aktion: String                   // z. B. "Prüfbogen gespeichert"
)


/**
 * Team-Stundenzettel: eine Zeile je Mitarbeiter auf dem gemeinsamen
 * Stationszettel (Stunden + Anfahrt), über den Server geteilt (LWW).
 */
@Entity(tableName = "stundenzettel_eintraege", primaryKeys = ["station", "zeitraumStart", "mitarbeiter"])
data class StundenzettelEintrag(
    val station: String,
    val zeitraumStart: String,           // ISO-Datum des Zeitraumbeginns
    val mitarbeiter: String,
    val stunden: String = "",            // z. B. "3,5"
    val anfahrt: String = "",
    val updatedAt: String = ""
)

/**
 * Einsatz-Zeiterfassung: "Einsatz starten/beenden" je Station –
 * befüllt die eigene Stundenzeile automatisch vor.
 */
@Entity(tableName = "einsaetze")
data class Einsatz(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val station: String,
    val mitarbeiter: String,
    val start: String,                   // ISO-Datum+Zeit
    val ende: String = ""                // leer = läuft noch
)
