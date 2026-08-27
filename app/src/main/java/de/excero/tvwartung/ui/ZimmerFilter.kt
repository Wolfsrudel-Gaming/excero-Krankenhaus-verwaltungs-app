package de.excero.tvwartung.ui

/** Sortiermodi der Zimmerliste. */
enum class SortModus(val label: String) {
    STATION("Nach Station"),
    ZULETZT_NEU("Zuletzt geprüft"),
    LAENGSTE_OFFEN("Am längsten offen"),
    FREENET("Freenet-Ablauf")
}

/** Prüfstatus im aktuellen Zeitraum. */
enum class PruefStatus(val label: String) {
    ALLE("Alle"),
    UNGEPRUEFT("Nur ungeprüfte"),
    GEPRUEFT("Nur geprüfte")
}

/** Filter nach Freenet-Gültigkeit. */
enum class FreenetFilter(val label: String) {
    ALLE("Alle"),
    ABGELAUFEN("Abgelaufen"),
    BALD("Läuft bald ab"),
    OK("Gültig")
}

/** Filter nach Zutritt/Sperren. */
enum class ZutrittFilter(val label: String) {
    ALLE("Alle"),
    KEIN_ZUTRITT("Nur „kein Zutritt“"),
    WIEDERVORLAGE_FAELLIG("Wiedervorlage fällig")
}

/**
 * Gesammelter Filterzustand der Zimmerliste. Leerer Zustand = keine
 * Einschränkung; [aktiveAnzahl] zählt die gesetzten Kriterien für das Chip-Badge.
 */
data class ZimmerFilter(
    val pruefStatus: PruefStatus = PruefStatus.ALLE,
    val freenet: FreenetFilter = FreenetFilter.ALLE,
    val zutritt: ZutrittFilter = ZutrittFilter.ALLE,
    val station: String = "",
    val faellig: Boolean = false
) {
    val aktiveAnzahl: Int
        get() = listOf(
            pruefStatus != PruefStatus.ALLE,
            freenet != FreenetFilter.ALLE,
            zutritt != ZutrittFilter.ALLE,
            station.isNotBlank(),
            faellig
        ).count { it }
}
