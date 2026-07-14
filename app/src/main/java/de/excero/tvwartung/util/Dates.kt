package de.excero.tvwartung.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    private val german: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val folder: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun todayIso(): String = LocalDate.now().format(iso)

    fun todayGerman(): String = LocalDate.now().format(german)

    /** Ordnername im Format JJJJMMTT, z. B. 20260714. */
    fun todayFolder(): String = LocalDate.now().format(folder)

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
