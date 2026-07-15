package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.Material
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.WarnAmber

/**
 * Verwaltung von Materialkatalog (inkl. Lagerbestand) und eigenen Prüfpunkten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerwaltungScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val materialien by viewModel.materialien.collectAsState()
    val pruefpunkte by viewModel.customPruefpunkte.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Material & Prüfpunkte", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
            // Materialkatalog & Lagerbestand
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Materialkatalog & Lagerbestand",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Diese Arbeiten erscheinen im Prüfbogen zum Ankreuzen. Bei Einträgen mit " +
                            "Bestandsführung wird der Bestand beim Speichern eines Prüfbogens " +
                            "automatisch um 1 reduziert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    materialien.forEach { material ->
                        MaterialRow(material, viewModel)
                    }
                    HorizontalDivider()
                    NeuesMaterialEingabe(viewModel)
                }
            }

            // Eigene Prüfpunkte
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eigene Prüfpunkte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Zusätzliche Prüfpunkte, die im Prüfbogen unter den Standardpunkten " +
                            "erscheinen. Die Standardpunkte des Papierbogens bleiben unverändert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    if (pruefpunkte.isEmpty()) {
                        Text(
                            "Noch keine eigenen Prüfpunkte angelegt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    pruefpunkte.forEach { punkt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                punkt.titel,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (punkt.aktiv) TextDecoration.None
                                else TextDecoration.LineThrough,
                                color = if (punkt.aktiv) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.updatePruefpunkt(punkt.copy(aktiv = !punkt.aktiv))
                            }) {
                                Text(if (punkt.aktiv) "Deaktivieren" else "Aktivieren")
                            }
                        }
                    }
                    HorizontalDivider()
                    NeuerPruefpunktEingabe(viewModel)
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(material: Material, viewModel: AppViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                material.name,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (material.aktiv) TextDecoration.None else TextDecoration.LineThrough,
                color = if (material.aktiv) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = material.bestandAktiv,
                    onCheckedChange = { viewModel.updateMaterial(material.copy(bestandAktiv = it)) },
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "Bestand führen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    viewModel.updateMaterial(material.copy(aktiv = !material.aktiv))
                }) {
                    Text(if (material.aktiv) "Ausblenden" else "Einblenden")
                }
            }
        }
        if (material.bestandAktiv) {
            FilledTonalIconButton(
                onClick = { viewModel.updateMaterial(material.copy(bestand = material.bestand - 1)) },
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Default.Remove, contentDescription = "Bestand verringern") }
            Text(
                "${material.bestand}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    material.bestand < 0 -> ErrorRed
                    material.bestand <= 2 -> WarnAmber
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            FilledTonalIconButton(
                onClick = { viewModel.updateMaterial(material.copy(bestand = material.bestand + 1)) },
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "Bestand erhöhen") }
        }
    }
}

@Composable
private fun NeuesMaterialEingabe(viewModel: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var mitBestand by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Neues Material / neue Arbeit") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mitBestand, onCheckedChange = { mitBestand = it })
            Text("Bestand führen", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    viewModel.addMaterial(name, mitBestand)
                    name = ""
                },
                enabled = name.isNotBlank()
            ) { Text("Hinzufügen") }
        }
    }
}

@Composable
private fun NeuerPruefpunktEingabe(viewModel: AppViewModel) {
    var titel by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = titel,
            onValueChange = { titel = it },
            label = { Text("Neuer Prüfpunkt") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                viewModel.addPruefpunkt(titel)
                titel = ""
            },
            enabled = titel.isNotBlank()
        ) { Text("Hinzufügen") }
    }
}
