package de.excero.tvwartung.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val gueltigBis: String               // ISO-Datum oder ""
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
    val bemerkungen: String = ""         // Freitext unten auf dem Bogen
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
}

/**
 * "Kein Zutritt"-Vermerk: Von der Stationsschwester gemeldete Zimmer, die bei
 * dieser Anfahrt nicht betreten werden dürfen. Gilt nur innerhalb des
 * eingestellten Prüfzeitraums und läuft danach automatisch ab.
 */
@Entity(tableName = "room_sperren")
data class RoomSperre(
    @PrimaryKey val roomId: String,
    val gesperrtAm: String               // ISO-Datum der Meldung
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
