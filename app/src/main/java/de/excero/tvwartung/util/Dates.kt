package de.excero.tvwartung.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    private val german: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val folder: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val dateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val germanDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun todayIso(): String = LocalDate.now().format(iso)

    fun nowIsoDateTime(): String = java.time.LocalDateTime.now().withNano(0).format(dateTime)

    /** ISO-Datum+Zeit → "14.07.2026 10:32". */
    fun isoDateTimeToGerman(value: String): String =
        runCatching { java.time.LocalDateTime.parse(value, dateTime).format(germanDateTime) }
            .getOrDefault(value)

    /** Stunden zwischen zwei ISO-Zeitstempeln, auf Viertelstunden gerundet, z. B. "3,25". */
    fun stundenZwischen(startIso: String, endeIso: String): String = runCatching {
        val start = java.time.LocalDateTime.parse(startIso, dateTime)
        val ende = java.time.LocalDateTime.parse(endeIso, dateTime)
        val minuten = java.time.temporal.ChronoUnit.MINUTES.between(start, ende).coerceAtLeast(0)
        val viertel = Math.round(minuten / 15.0) * 0.25
        String.format("%.2f", viertel).trimEnd('0').trimEnd('.', ',').replace('.', ',')
            .ifBlank { "0" }
    }.getOrDefault("")

    /** Montag der aktuellen Woche (ISO). */
    fun mondayIso(): String =
        LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(iso)

    fun todayGerman(): String = LocalDate.now().format(german)

    /** Aktueller Zeitstempel „TT.MM.JJJJ HH:MM" (für Foto-Wasserzeichen). */
    fun nowStempel(): String = java.time.LocalDateTime.now().format(germanDateTime)

    /** Ordnername im Format JJJJMMTT, z. B. 20260714. */
    fun todayFolder(): String = LocalDate.now().format(folder)

    /** ISO-Datum → Ordnername (JJJJMMTT); leere/ungültige Werte → "". */
    fun isoToFolder(isoDate: String): String =
        parseIso(isoDate)?.format(folder) ?: ""

    fun parseIso(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim(), iso) }.getOrNull()

    fun parseGerman(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim(), german) }.getOrNull()

    /** ISO-Datum → deutsche Anzeige (dd.MM.yyyy); leere/ungültige Werte unverändert. */
    fun isoToGerman(value: String): String =
        parseIso(value)?.format(german) ?: value

    /** Deutsche Eingabe (dd.MM.yyyy) → ISO; akzeptiert auch bereits ISO-formatierte Werte. */
    fun germanToIso(value: String): String? {
        if (value.isBlank()) return ""
        parseGerman(value)?.let { return it.format(iso) }
        parseIso(value)?.let { return it.format(iso) }
        return null
    }

    /** Beliebige gespeicherte Schreibweise defensiv in ISO normalisieren. */
    fun normalizeIso(value: String): String {
        if (value.isBlank()) return ""
        parseIso(value)?.let { return it.format(iso) }
        parseGerman(value)?.let { return it.format(iso) }
        return value
    }

    /** Verbleibende Tage bis zu einem ISO-Datum; null wenn nicht parsebar. */
    fun daysUntil(isoDate: String): Long? {
        val date = parseIso(isoDate) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), date)
    }

    /** Excel-Serienwert (Tage seit 30.12.1899) → LocalDate. */
    fun fromExcelSerial(serial: Double): LocalDate =
        LocalDate.of(1899, 12, 30).plusDays(serial.toLong())

    /** LocalDate → Excel-Serienwert. */
    fun toExcelSerial(date: LocalDate): Long =
        ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date)
}
