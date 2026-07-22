package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.Pruefzeitraum
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.AppTheme
import de.excero.tvwartung.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    var seitDatumText by remember(settings.seitDatum) {
        mutableStateOf(Dates.isoToGerman(settings.seitDatum))
    }
    var dateError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Prüfzeitraum („eine Anfahrt“)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Bestimmt, für welchen Zeitraum Zimmer in der Übersicht als geprüft " +
                            "abgehakt werden – z. B. die ganze Woche, wenn du täglich vor Ort bist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Pruefzeitraum.entries.forEach { zeitraum ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(zeitraum = zeitraum))
                                }
                        ) {
                            RadioButton(
                                selected = settings.zeitraum == zeitraum,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(zeitraum = zeitraum))
                                }
                            )
                            Text(zeitraum.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (settings.zeitraum == Pruefzeitraum.SEIT_DATUM) {
                        OutlinedTextField(
                            value = seitDatumText,
                            onValueChange = { neu ->
                                seitDatumText = neu
                                val iso = Dates.germanToIso(neu)
                                if (iso.isNullOrBlank()) {
                                    dateError = neu.isNotBlank()
                                } else {
                                    dateError = false
                                    viewModel.updateSettings(settings.copy(seitDatum = iso))
                                }
                            },
                            label = { Text("Startdatum (TT.MM.JJJJ)") },
                            singleLine = true,
                            isError = dateError,
                            supportingText = if (dateError) {
                                { Text("Datum bitte als TT.MM.JJJJ eingeben") }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "Aktueller Zeitraum: ${settings.beschreibung()} " +
                            "(ab ${Dates.isoToGerman(settings.zeitraumStartIso())})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Darstellung (2.0-Beta)
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Darstellung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val optionen = listOf(
                        AppTheme.SYSTEM to "Automatisch (Systemeinstellung)",
                        AppTheme.HELL to "Hell",
                        AppTheme.DUNKEL to "Dunkel"
                    )
                    optionen.forEach { (theme, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSettings(settings.copy(theme = theme)) }
                        ) {
                            RadioButton(
                                selected = settings.theme == theme,
                                onClick = { viewModel.updateSettings(settings.copy(theme = theme)) }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = settings.kompaktZimmerliste,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(kompaktZimmerliste = it)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Kompakte Zimmerliste (mehr Zimmer auf einen Blick)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Mitarbeiter (Gerät = Mitarbeiter)
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mitarbeiter (dieses Gerät)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Name des Mitarbeiters, der mit diesem Handy arbeitet – erscheint " +
                            "auf Prüfberichten und Stundenzetteln. Die Liste wird in der " +
                            "Weboberfläche gepflegt und beim Sync aktualisiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TvTypAuswahl(
                        value = settings.mitarbeiter,
                        onValueChange = { viewModel.updateSettings(settings.copy(mitarbeiter = it)) },
                        bekannteTypen = viewModel.bekannteMitarbeiter(),
                        label = "Mitarbeiter-Name"
                    )
                }
            }

            // Server-Synchronisation
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Server-Synchronisation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Gleicht Zimmer, Prüfbögen, Stundenzettel und Fotos automatisch " +
                            "mit dem KKH-Server ab (Weboberfläche für die Verwaltung).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = settings.serverUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(serverUrl = it.trim())) },
                        label = { Text("Server-URL (z. B. https://server.de/kkh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.apiKey,
                        onValueChange = { viewModel.updateSettings(settings.copy(apiKey = it.trim())) },
                        label = { Text("API-Schlüssel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = settings.autoSync,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(autoSync = it)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Automatisch synchronisieren (beim Start und nach jedem Prüfbogen)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    androidx.compose.material3.Button(
                        onClick = { viewModel.syncNow() },
                        enabled = settings.serverUrl.isNotBlank() && settings.apiKey.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Jetzt synchronisieren")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Aktivitätsprotokoll",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Wann welches Zimmer bearbeitet wurde – nur intern, wird nicht exportiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    if (recentActivity.isEmpty()) {
                        Text(
                            "Noch keine Einträge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentActivity.forEach { entry ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    Dates.isoDateTimeToGerman(entry.zeitpunkt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(130.dp)
                                )
                                Text(
                                    "${entry.roomId} · ${entry.aktion}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            // Echtstart: Testdaten bereinigen
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Echtstart vorbereiten",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Löscht alle Test-Prüfbögen, Fotos, Stundenzettel und Sperren. " +
                            "Zimmer, Stationen und Materialkatalog bleiben erhalten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var bestaetigt by remember { mutableStateOf(false) }
                    if (!bestaetigt) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { bestaetigt = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Testdaten bereinigen …") }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.testdatenBereinigen()
                                bestaetigt = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("WIRKLICH löschen – kann nicht rückgängig gemacht werden") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
