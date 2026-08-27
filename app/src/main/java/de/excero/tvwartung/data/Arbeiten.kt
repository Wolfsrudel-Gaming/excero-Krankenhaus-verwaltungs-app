package de.excero.tvwartung.data

/**
 * Vorbelegung des Materialkatalogs. Der eigentliche Katalog lebt in der
 * Datenbank (Tabelle "materialien") und ist in der App frei anpassbar;
 * diese Liste wird nur beim ersten Start eingespielt.
 *
 * "Freenet-Karte verlängert" wird nicht hier geführt, sondern aus dem
 * Prüfpunkt "Freenet verlängert" abgeleitet, um Doppeleingaben zu vermeiden.
 */
object Arbeiten {
    const val FREENET_VERLAENGERT = "Freenet-Karte verlängert"

    /** (Name, Bestandsführung aktiv) – Arbeiten ohne Materialverbrauch führen keinen Bestand. */
    val SEED: List<Pair<String, Boolean>> = listOf(
        "Fernbedienung getauscht" to true,
        "FB-Batterien erneuert" to true,
        "Antenne getauscht" to true,
        "CI-Modul getauscht / neu verbaut" to true,
        "TV getauscht" to true,
        "Sendersuchlauf durchgeführt" to false,
        "Kabel / HDMI getauscht" to true,
        "Halterung befestigt" to false,
        "TV neu eingerichtet" to false
    )

    /** Reihenfolge für die Material-Zusammenfassung im Stundenzettel. */
    val KATALOG: List<String> = SEED.map { it.first }
}
