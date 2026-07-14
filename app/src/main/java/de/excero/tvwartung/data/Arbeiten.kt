package de.excero.tvwartung.data

/**
 * Katalog der durchführbaren Arbeiten bzw. des verbauten Materials.
 * Wird im Prüfbogen zum Ankreuzen angeboten und im Stundenzettel je Station
 * zusammengezählt (Materialnachweis).
 *
 * "Freenet-Karte verlängert" wird nicht hier geführt, sondern aus dem
 * Prüfpunkt "Freenet verlängert" abgeleitet, um Doppeleingaben zu vermeiden.
 */
object Arbeiten {
    const val FREENET_VERLAENGERT = "Freenet-Karte verlängert"

    /** Manuell ankreuzbare Arbeiten (ohne Freenet-Verlängerung). */
    val KATALOG = listOf(
        "Fernbedienung getauscht",
        "FB-Batterien erneuert",
        "Antenne getauscht",
        "CI-Modul getauscht / neu verbaut",
        "TV getauscht",
        "Sendersuchlauf durchgeführt",
        "Kabel / HDMI getauscht",
        "Halterung befestigt",
        "TV neu eingerichtet"
    )
}
