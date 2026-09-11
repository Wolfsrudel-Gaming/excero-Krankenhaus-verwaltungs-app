package de.excero.tvwartung.util

/**
 * Änderungsverlauf der 2.0-Betas – zentral gepflegt, damit „Was ist neu" und die
 * Info-Seite in den Einstellungen dieselben Inhalte zeigen. Neueste zuerst.
 */
object Changelog {
    val EINTRAEGE: List<Pair<String, List<String>>> = listOf(
        "2.0-beta34" to listOf(
            "Team-Zeile im Stundenzettel löschbar (z. B. versehentlich doppelt erfasst) – sync-fest",
            "Stundenzettel-Liste: „Nur heute“-Filter und neueste zuerst",
        ),
        "2.0-beta33" to listOf(
            "Stundenzettel pro Tag je Station (erneuter Anlauf nach Wochen = eigener Zettel)",
            "„Erneuter Besuch“-Knopf für einen zusätzlichen Stundenzettel am selben Tag",
        ),
        "2.0-beta32" to listOf(
            "Kein-Zutritt entfernen wird jetzt korrekt auf alle Geräte übertragen (kein Zurückkommen nach dem Sync mehr)",
        ),
        "2.0-beta31" to listOf(
            "Einstellungen: API-Schlüssel maskiert (Auge zum Einblenden)",
            "Neue Info-/Über-die-App-Seite mit Version und Änderungsverlauf",
            "Sicherheitsabfrage vor dem Löschen eines Fotos",
        ),
        "2.0-beta30" to listOf(
            "Dashboard-Hinweis, wenn der Prüfzeitraum bald endet und noch Zimmer offen sind",
        ),
        "2.0-beta29" to listOf(
            "Feste Foto-Felder: Fern, Nah, Fernbedienung (bei Freenet-Verlängerung Nah vorher/nachher)",
            "Lager: Web-Lager in der App sichtbar; Materialverbrauch bucht automatisch aufs Lager",
            "Web: Material-Zuordnung – nicht verknüpfte Materialien finden und zuordnen",
        ),
        "2.0-beta27" to listOf(
            "„Tag aufteilen“: Feierabend-Stunden automatisch auf die Stationen verteilen",
            "KI-Prüfung: Fotos zoombar",
        ),
        "2.0-beta25" to listOf(
            "Umfassende Zimmer-Filter (Prüfstatus, Freenet, Zutritt, Station, fällig)",
            "Filter und Sortierung bleiben beim Navigieren erhalten",
        ),
        "2.0" to listOf(
            "Neues Menü links, Dashboard als Startseite, globale Suche",
            "KI-Vorschläge direkt im Prüfbogen, Dark Mode, aufgefrischtes Design",
        ),
    )

    /** Kurzfassung der jeweils neuesten Änderungen für den „Was ist neu"-Dialog. */
    val NEUESTE: List<String> get() = EINTRAEGE.firstOrNull()?.second ?: emptyList()
}
