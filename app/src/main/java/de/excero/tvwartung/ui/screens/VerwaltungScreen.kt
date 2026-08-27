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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.launch
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
            // Nachbestell-Warnung aus dem Server-Lager (beim Sync aktualisiert)
            val lagerWarnungen by viewModel.lagerWarnungen.collectAsState()
            if (lagerWarnungen.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = WarnAmber.copy(alpha = 0.15f)
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "⚠️ Nachbestellen (Server-Lager unter Mindestbestand)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarnAmber
                        )
                        lagerWarnungen.forEach { w ->
                            Text("•  $w", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Live-Vorschau: so wirkt sich der Katalog auf den Prüfbogen aus
            val aktivesMaterial = materialien.filter { it.aktiv }
            val aktivePruefpunkte = pruefpunkte.filter { it.aktiv }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Vorschau Prüfbogen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "So wirken sich Änderungen unten direkt auf den Prüfbogen aus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Arbeiten / Material (${aktivesMaterial.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (aktivesMaterial.isEmpty()) "– keine aktiven Einträge –"
                        else aktivesMaterial.joinToString("  ·  ") { it.name },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Zusätzliche Prüfpunkte (${aktivePruefpunkte.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (aktivePruefpunkte.isEmpty()) {
                        Text("– nur die Standardpunkte –", style = MaterialTheme.typography.bodySmall)
                    } else {
                        aktivePruefpunkte.forEach {
                            Text("•  ${it.titel}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

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

            // Lieferanten (nur lesen, aus dem Web-Lager)
            LieferantenCard(viewModel)
        }
    }
}

@Composable
private fun LieferantenCard(viewModel: AppViewModel) {
    var lieferanten by remember { mutableStateOf<List<de.excero.tvwartung.sync.Lieferant>?>(null) }
    var laedt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun laden() {
        scope.launch {
            laedt = true
            lieferanten = viewModel.ladeLieferanten()
            laedt = false
        }
    }
    LaunchedEffect(Unit) { laden() }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lieferanten",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { laden() }, enabled = !laedt) { Text("Aktualisieren") }
            }
            Text(
                "Nur lesend – gepflegt im Web-Lager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            val liste = lieferanten
            when {
                laedt && liste == null -> SkeletonBox(Modifier.fillMaxWidth().height(48.dp), corner = 10)
                liste.isNullOrEmpty() -> Text(
                    "Keine Lieferanten geladen (Server nicht erreichbar oder keine hinterlegt).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> liste.forEach { l ->
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(l.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val details = listOfNotNull(
                            l.kontakt.ifBlank { null },
                            l.telefon.ifBlank { null },
                            l.email.ifBlank { null },
                            l.kundennummer.ifBlank { null }?.let { "Kd-Nr. $it" }
                        )
                        if (details.isNotEmpty()) {
                            Text(
                                details.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(material: Material, viewModel: AppViewModel) {
    var zeigeBuchung by remember { mutableStateOf(false) }
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
                if (material.bestandAktiv) {
                    TextButton(onClick = { zeigeBuchung = true }) { Text("Buchen…") }
                }
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
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Bestand verringern") }
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
            ) { Icon(Icons.Outlined.Add, contentDescription = "Bestand erhöhen") }
        }
    }

    if (zeigeBuchung) {
        MaterialBuchungDialog(
            material = material,
            onBuchen = { neuerBestand ->
                viewModel.updateMaterial(material.copy(bestand = neuerBestand))
                zeigeBuchung = false
            },
            onDismiss = { zeigeBuchung = false }
        )
    }
}

/** Manuelle Bestandsbuchung: Eingang (+), Ausgang (–) oder Korrektur (=). */
@Composable
private fun MaterialBuchungDialog(
    material: Material,
    onBuchen: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var typ by remember { mutableStateOf("ausgang") }
    var mengeText by remember { mutableStateOf("") }
    val menge = mengeText.toIntOrNull()
    val neuerBestand = when (typ) {
        "eingang" -> material.bestand + (menge ?: 0)
        "ausgang" -> material.bestand - (menge ?: 0)
        else -> menge ?: material.bestand
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buchen: ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aktueller Bestand: ${material.bestand}", style = MaterialTheme.typography.bodySmall)
                listOf("eingang" to "Eingang (+)", "ausgang" to "Ausgang (–)", "korrektur" to "Korrektur (= setzen)")
                    .forEach { (wert, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { typ = wert }
                        ) {
                            RadioButton(selected = typ == wert, onClick = { typ = wert })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                OutlinedTextField(
                    value = mengeText,
                    onValueChange = { mengeText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (typ == "korrektur") "Neuer Bestand" else "Menge") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (menge != null) {
                    Text(
                        "Neuer Bestand: $neuerBestand",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (neuerBestand < 0) ErrorRed else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onBuchen(neuerBestand) }, enabled = menge != null) { Text("Buchen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
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
