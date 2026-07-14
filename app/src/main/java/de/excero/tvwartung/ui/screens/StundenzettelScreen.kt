package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StundenzettelScreen(
    viewModel: AppViewModel,
    station: String,
    onBack: () -> Unit
) {
    val basis by produceState<AppViewModel.StundenzettelBasis?>(initialValue = null, station) {
        value = viewModel.stundenzettelVorschau(station)
    }

    var datum by remember { mutableStateOf(Dates.todayGerman()) }
    var von by remember { mutableStateOf("") }
    var bis by remember { mutableStateOf("") }
    var anfahrt by remember { mutableStateOf("") }
    var techniker by remember { mutableStateOf("") }

    val sigStation = remember { SignatureState() }
    val sigTechniker = remember { SignatureState() }

    val arbeitsstunden = remember(von, bis) { berechneStunden(von, bis) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val arbeitszeit = if (von.isNotBlank() || bis.isNotBlank()) "$von – $bis" else ""
            val anfahrtText = anfahrt.trim().let { if (it.isBlank()) "" else "$it Std." }
            viewModel.exportStundenzettel(
                uri = uri,
                station = station,
                eingabe = AppViewModel.StundenEingabe(
                    datum = datum.trim(),
                    arbeitszeit = arbeitszeit,
                    arbeitsstunden = arbeitsstunden,
                    anfahrt = anfahrtText,
                    techniker = techniker.trim()
                ),
                signaturStation = sigStation.toBitmap(),
                signaturTechniker = sigTechniker.toBitmap()
            )
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Stundenzettel", fontWeight = FontWeight.Bold)
                    Text("Station $station", style = MaterialTheme.typography.labelMedium)
                }
            },
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
            // Zeiten
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zeiten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = datum, onValueChange = { datum = it },
                        label = { Text("Datum (TT.MM.JJJJ)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = von, onValueChange = { von = it },
                            label = { Text("Arbeit von (HH:MM)") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bis, onValueChange = { bis = it },
                            label = { Text("bis (HH:MM)") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (arbeitsstunden.isNotBlank()) {
                        Text(
                            "Arbeitsstunden: $arbeitsstunden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedTextField(
                        value = anfahrt, onValueChange = { anfahrt = it },
                        label = { Text("Anfahrt (Stunden, z. B. 0,5)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = techniker, onValueChange = { techniker = it },
                        label = { Text("Name (Dienstleister / Techniker)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Vorschau der Leistungen
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Leistungen (${basis?.leistungen?.size ?: 0} Zimmer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        basis?.zeitraum ?: "wird geladen …",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    val leistungen = basis?.leistungen.orEmpty()
                    if (leistungen.isEmpty()) {
                        Text(
                            "Für den aktuellen Zeitraum wurden noch keine Prüfungen dieser Station erfasst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        leistungen.forEach { l ->
                            val text = if (l.arbeiten.isEmpty()) "TV überprüft"
                            else "TV überprüft; " + l.arbeiten.joinToString(", ")
                            Text(
                                "Zi. ${l.zimmer} (${Dates.isoToGerman(l.datum)}): $text",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val material = basis?.material.orEmpty()
                        if (material.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Material:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                material.joinToString("  ·  ") { "${it.first} ${it.second}×" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Unterschriften
            SignaturCard("Unterschrift Station", sigStation)
            SignaturCard("Unterschrift Dienstleister", sigTechniker)

            Button(
                onClick = {
                    pdfLauncher.launch("Stundenzettel_${station}_${Dates.todayFolder()}.pdf")
                },
                enabled = !basis?.leistungen.isNullOrEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stundenzettel als PDF")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SignaturCard(titel: String, state: SignatureState) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { state.clear() }) { Text("Löschen") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                SignaturePad(state = state, modifier = Modifier.fillMaxSize())
            }
            Text(
                "Mit dem Finger im Feld unterschreiben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Arbeitsstunden aus "HH:MM"–"HH:MM" berechnen, z. B. "3,5 Std."; sonst "". */
private fun berechneStunden(von: String, bis: String): String {
    val v = parseMinuten(von) ?: return ""
    val b = parseMinuten(bis) ?: return ""
    val diff = b - v
    if (diff <= 0) return ""
    val stunden = diff / 60.0
    val text = String.format("%.2f", stunden).trimEnd('0').trimEnd('.', ',').replace('.', ',')
    return "$text Std."
}

private fun parseMinuten(text: String): Int? {
    val t = text.trim().replace('.', ':')
    val parts = t.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
