package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

/** Gespeicherten Prüfbericht ansehen und als PDF exportieren. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerichtScreen(
    viewModel: AppViewModel,
    inspectionId: Long,
    onBack: () -> Unit
) {
    val inspection by viewModel.inspection(inspectionId).collectAsState(initial = null)
    val current = inspection ?: return

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { viewModel.exportInspectionPdf(it, inspectionId) } }

    var loeschDialog by remember { mutableStateOf(false) }
    if (loeschDialog) {
        AlertDialog(
            onDismissRequest = { loeschDialog = false },
            title = { Text("Bericht löschen?") },
            text = {
                Text(
                    "Der Bericht wandert in den Papierkorb und lässt sich dort " +
                        "jederzeit wiederherstellen (Suche → Papierkorb)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    loeschDialog = false
                    viewModel.loescheBericht(inspectionId)
                    onBack()
                }) { Text("In den Papierkorb", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { loeschDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Prüfbericht", fontWeight = FontWeight.Bold)
                    Text(
                        "${current.roomId} · ${Dates.isoToGerman(current.datum)}" +
                            if (current.mitarbeiter.isNotBlank()) " · ${current.mitarbeiter}" else "",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            },
            actions = {
                IconButton(onClick = { loeschDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "In den Papierkorb",
                        tint = MaterialTheme.colorScheme.error
                    )
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
            Button(
                onClick = {
                    pdfLauncher.launch(
                        "Pruefbericht_${current.roomId}_${Dates.isoToFolder(current.datum)}.pdf"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Als PDF exportieren")
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Prüfpunkte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    (current.punkte() + current.extraPunkteListe()).forEach { (titel, ergebnis, bemerkung) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(titel, style = MaterialTheme.typography.bodyMedium)
                                if (bemerkung.isNotBlank()) {
                                    Text(
                                        bemerkung,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            when (ergebnis) {
                                true -> StatusBadge("i.O.", OkGreen)
                                false -> StatusBadge("n.i.O.", ErrorRed)
                                null -> Text(
                                    "–",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val arbeiten = current.arbeitenListe()
            if (arbeiten.isNotEmpty()) {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Durchgeführte Arbeiten / Material",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        arbeiten.forEach { a ->
                            Text("•  $a", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (current.bemerkungen.isNotBlank()) {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Bemerkungen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(current.bemerkungen, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Fotos ansehen und direkt weitere aufnehmen/hinzufügen
            PhotoSection(
                viewModel = viewModel,
                roomId = current.roomId,
                dateFolder = Dates.isoToFolder(current.datum)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
