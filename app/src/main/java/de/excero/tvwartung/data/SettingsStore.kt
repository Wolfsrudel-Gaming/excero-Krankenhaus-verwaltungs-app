package de.excero.tvwartung.data

import android.content.Context
import de.excero.tvwartung.ui.theme.AppTheme
import de.excero.tvwartung.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Zeitraum, der als "eine Anfahrt" zählt (bestimmt die Prüf-Haken in der Übersicht). */
enum class Pruefzeitraum(val label: String) {
    TAG("Nur heute"),
    WOCHE("Diese Woche (ab Montag)"),
    SEIT_DATUM("Seit festem Datum")
}

data class AppSettings(
    val zeitraum: Pruefzeitraum = Pruefzeitraum.TAG,
    val seitDatum: String = Dates.todayIso(),  // ISO; nur bei SEIT_DATUM relevant
    val serverUrl: String = "",                // z. B. https://example.de/kkh
    val apiKey: String = "",                   // Sync-Schlüssel des Servers
    val autoSync: Boolean = false,             // nach jedem Prüfbogen automatisch synchronisieren
    val mitarbeiter: String = "",              // dieses Gerät = dieser Mitarbeiter
    val lastSync: String = "",                 // Zeitstempel des letzten Abgleichs (Delta-Sync)
    val theme: AppTheme = AppTheme.SYSTEM,     // 2.0: manueller Hell/Dunkel-Override
    val kompaktZimmerliste: Boolean = false,   // 2.0: kompaktere Zimmerliste
    val kiErkennungAktiv: Boolean = true        // 2.0: KI-Fotoerkennung ein-/ausschaltbar
) {
    /** Startdatum (ISO) des aktuellen Prüfzeitraums. */
    fun zeitraumStartIso(): String = when (zeitraum) {
        Pruefzeitraum.TAG -> Dates.todayIso()
        Pruefzeitraum.WOCHE -> Dates.mondayIso()
        Pruefzeitraum.SEIT_DATUM -> seitDatum.ifBlank { Dates.todayIso() }
    }

    fun beschreibung(): String = when (zeitraum) {
        Pruefzeitraum.TAG -> "heute"
        Pruefzeitraum.WOCHE -> "diese Woche"
        Pruefzeitraum.SEIT_DATUM -> "seit ${Dates.isoToGerman(seitDatum)}"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings {
        val zeitraum = runCatching {
            Pruefzeitraum.valueOf(prefs.getString("zeitraum", Pruefzeitraum.TAG.name)!!)
        }.getOrDefault(Pruefzeitraum.TAG)
        return AppSettings(
            zeitraum = zeitraum,
            seitDatum = prefs.getString("seitDatum", Dates.todayIso()) ?: Dates.todayIso(),
            serverUrl = prefs.getString("serverUrl", "") ?: "",
            apiKey = prefs.getString("apiKey", "") ?: "",
            autoSync = prefs.getString("autoSync", "false") == "true",
            mitarbeiter = prefs.getString("mitarbeiter", "") ?: "",
            lastSync = prefs.getString("lastSync", "") ?: "",
            theme = runCatching {
                AppTheme.valueOf(prefs.getString("theme", AppTheme.SYSTEM.name)!!)
            }.getOrDefault(AppTheme.SYSTEM),
            kompaktZimmerliste = prefs.getString("kompaktZimmerliste", "false") == "true",
            kiErkennungAktiv = prefs.getString("kiErkennungAktiv", "true") == "true"
        )
    }

    /** Zuletzt verwendete Suchbegriffe (neueste zuerst, max. 8) für die globale Suche. */
    fun letzteSuchen(): List<String> =
        prefs.getString("letzteSuchen", "")!!.split("\n").filter { it.isNotBlank() }

    fun merkeSuche(begriff: String) {
        val t = begriff.trim()
        if (t.isBlank()) return
        val neu = (listOf(t) + letzteSuchen().filter { !it.equals(t, ignoreCase = true) }).take(8)
        prefs.edit().putString("letzteSuchen", neu.joinToString("\n")).apply()
    }

    /** Vom Server bekannte (aktive) Mitarbeiter für die Geräteeinrichtung. */
    fun bekannteMitarbeiter(): List<String> =
        prefs.getString("bekannteMitarbeiter", "")!!.split("\n").filter { it.isNotBlank() }

    fun setBekannteMitarbeiter(namen: List<String>) {
        prefs.edit().putString("bekannteMitarbeiter", namen.joinToString("\n")).apply()
    }

    /** Vom Server gemeldete knappe Lager-Artikel (Warnungstexte), beim Sync aktualisiert. */
    private val _lagerWarnungen = MutableStateFlow(
        prefs.getString("lagerWarnungen", "")!!.split("\n").filter { it.isNotBlank() }
    )
    val lagerWarnungen: StateFlow<List<String>> = _lagerWarnungen

    fun setLagerWarnungen(texte: List<String>) {
        prefs.edit().putString("lagerWarnungen", texte.joinToString("\n")).apply()
        _lagerWarnungen.value = texte
    }

    /** Im Menü angepinnte Seiten (Routen), reaktiv fürs Drawer-Menü. */
    private val _gepinnt = MutableStateFlow(
        prefs.getString("gepinnteMenue", "")!!.split("\n").filter { it.isNotBlank() }
    )
    val gepinnteMenue: StateFlow<List<String>> = _gepinnt

    fun toggleMenuePin(route: String) {
        val aktuell = _gepinnt.value
        val neu = if (route in aktuell) aktuell - route else aktuell + route
        prefs.edit().putString("gepinnteMenue", neu.joinToString("\n")).apply()
        _gepinnt.value = neu
    }

    /** Zuletzt gesehene Version im „Was ist neu"-Hinweis. */
    fun wasIstNeuGesehen(): String = prefs.getString("wasIstNeuVersion", "") ?: ""
    fun setWasIstNeuGesehen(version: String) {
        prefs.edit().putString("wasIstNeuVersion", version).apply()
    }

    fun update(settings: AppSettings) {
        prefs.edit()
            .putString("zeitraum", settings.zeitraum.name)
            .putString("seitDatum", settings.seitDatum)
            .putString("serverUrl", settings.serverUrl)
            .putString("apiKey", settings.apiKey)
            .putString("autoSync", if (settings.autoSync) "true" else "false")
            .putString("mitarbeiter", settings.mitarbeiter)
            .putString("lastSync", settings.lastSync)
            .putString("theme", settings.theme.name)
            .putString("kompaktZimmerliste", if (settings.kompaktZimmerliste) "true" else "false")
            .putString("kiErkennungAktiv", if (settings.kiErkennungAktiv) "true" else "false")
            .apply()
        _settings.value = settings
    }
}
