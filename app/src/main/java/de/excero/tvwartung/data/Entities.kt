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
)
