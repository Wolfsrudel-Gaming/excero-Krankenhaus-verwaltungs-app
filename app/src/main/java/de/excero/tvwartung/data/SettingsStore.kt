package de.excero.tvwartung.data

import android.content.Context
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
    val lastSync: String = ""                  // Zeitstempel des letzten Abgleichs (Delta-Sync)
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
            lastSync = prefs.getString("lastSync", "") ?: ""
        )
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
            .apply()
        _settings.value = settings
    }
}
