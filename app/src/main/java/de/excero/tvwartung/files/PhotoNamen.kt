package de.excero.tvwartung.files

/**
 * Dateinamen der Zimmerfotos: <roomId>_<JJJJMMTT>_<label>_<nr>_<kürzel>.jpg
 *
 * Bewusst ohne Uhrzeit. Die laufende Nummer hält die Reihenfolge je Label, das
 * Zufallskürzel verhindert, dass zwei Geräte für dasselbe Zimmer denselben Namen
 * vergeben (gleicher Pfad = beim Sync würde ein Foto das andere überschreiben).
 * Ältere Fotos heißen noch …_<label>_<HHmmss>.jpg und bleiben lesbar.
 */
object PhotoNamen {
    private val ALTE_ZEIT = Regex("_\\d{6}(_\\d+)?$")
    private val TAG = Regex("\\d{8}")

    fun neuerName(
        roomId: String, dateFolder: String, label: String,
        vorhandene: Collection<String>, kuerzel: String
    ): String {
        val prefix = "${roomId}_${dateFolder}_${label}_"
        val hoechste = vorhandene
            .filter { it.startsWith(prefix) && !istAltesSchema(it) }
            .mapNotNull { it.removePrefix(prefix).substringBefore('_').toIntOrNull() }
            .maxOrNull() ?: 0
        return prefix + "%02d".format(hoechste + 1) + "_" + kuerzel + ".jpg"
    }

    fun istAltesSchema(name: String): Boolean =
        ALTE_ZEIT.containsMatchIn(name.substringBeforeLast('.'))

    /** Label-Kürzel ("fern", "nah", …) – steht direkt hinter dem Tagesordner-Block. */
    fun label(name: String): String? {
        val teile = name.substringBeforeLast('.').split("_")
        val tag = teile.indexOfFirst { TAG.matches(it) }
        return teile.getOrNull(tag + 1)?.takeIf { tag >= 0 }
    }

    /** Neueres Foto = größer; Fotos im neuen Schema liegen immer nach denen mit Uhrzeit. */
    val nachAlter: Comparator<String> = compareBy<String>({ !istAltesSchema(it) }, { it })
}
