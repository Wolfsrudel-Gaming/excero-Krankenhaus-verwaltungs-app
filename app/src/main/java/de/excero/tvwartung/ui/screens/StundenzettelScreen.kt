package de.excero.tvwartung.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.StundenzettelEntity
import de.excero.tvwartung.files.SignatureStore
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

/**
 * Stundenzettel bearbeiten: Unterschriften können sofort auf der Station
 * eingeholt und gespeichert werden; die Stunden lassen sich später eintragen
 * und erst dann das PDF erzeugen.
 *
 * @param station  Öffnet/erzeugt den Zettel der Station für den aktuellen Zeitraum.
 * @param zettelId Öffnet einen gespeicherten Zettel aus der Liste (station wird ignoriert).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StundenzettelScreen(
    viewModel: AppViewModel,
    station: String?,
    zettelId: Long?,
    onBack: () -> Unit
) {
    val gespeichert by produceState<StundenzettelEntity?>(initialValue = null, station, zettelId) {
        value = if (zettelId != null) viewModel.ladeStundenzettelById(zettelId)
        else station?.let { viewModel.ladeStundenzettel(it) }
    }
    val zettel = gespeichert ?: return

    val basis by produceState<AppViewModel.StundenzettelBasis?>(initialValue = null, zettel.id) {
        value = viewModel.stundenzettelVorschau(zettel)
    }

    var auftragsnummer by remember { mutableStateOf("") }
    var datum by remember { mutableStateOf("") }
    var stunden by remember { mutableStateOf("") }
    var anfahrt by remember { mutableStateOf("") }
    var techniker by remember { mutableStateOf("") }
    var geladen by remember { mutableStateOf(false) }

    LaunchedEffect(zettel.id) {
        auftragsnummer = zettel.auftragsnummer
        datum = zettel.datum.ifBlank { Dates.todayGerman() }
        stunden = zettel.stunden
        anfahrt = zettel.anfahrt
        techniker = zettel.techniker
        geladen = true
    }

    fun aktuelleEingabe(): StundenzettelEntity = zettel.copy(
        auftragsnummer = auftragsnummer.trim(),
        datum = datum.trim(),
        stunden = stunden.trim(),
        anfahrt = anfahrt.trim(),
        techniker = techniker.trim()
    )

    // Unterschriften: neu gezeichnete Striche haben Vorrang vor gespeicherten
    val sigStationState = remember(zettel.id) { SignatureState() }
    val sigTechnikerState = remember(zettel.id) { SignatureState() }
    var sigStationGespeichert by remember(zettel.id) {
        mutableStateOf(viewModel.signatureStore.load(zettel.id, SignatureStore.ROLLE_STATION))
    }
    var sigTechnikerGespeichert by remember(zettel.id) {
        mutableStateOf(viewModel.signatureStore.load(zettel.id, SignatureStore.ROLLE_TECHNIKER))
    }

    /** Neue Striche als Datei sichern und die Bitmap fürs PDF liefern. */
    fun sichereSignatur(state: SignatureState, gespeicherteBitmap: Bitmap?, rolle: String): Bitmap? {
        val neu = state.toBitmap()
        if (neu != null) {
            viewModel.signatureStore.save(zettel.id, rolle, neu)
            return neu
        }
        return gespeicherteBitmap
    }

    fun speichereAlles() {
        sichereSignatur(sigStationState, sigStationGespeichert, SignatureStore.ROLLE_STATION)
        sichereSignatur(sigTechnikerState, sigTechnikerGespeichert, SignatureStore.ROLLE_TECHNIKER)
        viewModel.speichereStundenzettel(aktuelleEingabe())
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val sigStation = sichereSignatur(sigStationState, sigStationGespeichert, SignatureStore.ROLLE_STATION)
            val sigTechniker = sichereSignatur(sigTechnikerState, sigTechnikerGespeichert, SignatureStore.ROLLE_TECHNIKER)
            viewModel.exportStundenzettel(
                uri = uri,
                eingabe = aktuelleEingabe(),
                signaturStation = sigStation,
                signaturTechniker = sigTechniker
            )
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Stundenzettel", fontWeight = FontWeight.Bold)
                    Text(
                        "Station ${zettel.station}" +
                            if (auftragsnummer.isNotBlank()) " · $auftragsnummer" else "",
                        style = MaterialTheme.typography.labelMedium
                    )
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
            // Auftrag & Stunden
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auftrag & Stunden", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Alles wird gespeichert – Unterschrift jetzt einholen, " +
                            "Stunden später eintragen und dann erst das PDF erzeugen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = auftragsnummer, onValueChange = { auftragsnummer = it },
                        label = { Text("Auftragsnummer") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = datum, onValueChange = { datum = it },
                        label = { Text("Datum (TT.MM.JJJJ)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stunden, onValueChange = { stunden = it },
                            label = { Text("Arbeitsstunden (z. B. 3,5)") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = anfahrt, onValueChange = { anfahrt = it },
                            label = { Text("Anfahrt (Std.)") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        "Zeitraum: ${basis?.zeitraum ?: "wird geladen …"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    val leistungen = basis?.leistungen.orEmpty()
                    if (leistungen.isEmpty()) {
                        Text(
                            "In diesem Zeitraum wurden noch keine Prüfungen dieser Station erfasst.",
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

            // Unterschriften (werden gespeichert)
            SignaturCard(
                titel = "Unterschrift Station",
                state = sigStationState,
                gespeichert = sigStationGespeichert,
                onDelete = {
                    sigStationState.clear()
                    viewModel.signatureStore.delete(zettel.id, SignatureStore.ROLLE_STATION)
                    sigStationGespeichert = null
                }
            )
            SignaturCard(
                titel = "Unterschrift Dienstleister",
                state = sigTechnikerState,
                gespeichert = sigTechnikerGespeichert,
                onDelete = {
                    sigTechnikerState.clear()
                    viewModel.signatureStore.delete(zettel.id, SignatureStore.ROLLE_TECHNIKER)
                    sigTechnikerGespeichert = null
                }
            )

            OutlinedButton(
                onClick = {
                    speichereAlles()
                    onBack()
                },
                enabled = geladen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Speichern (Unterschrift & Eingaben)")
            }
            Button(
                onClick = {
                    pdfLauncher.launch(
                        "Stundenzettel_${zettel.station}_${Dates.todayFolder()}.pdf"
                    )
                },
                enabled = !basis?.leistungen.isNullOrEmpty() && geladen,
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
private fun SignaturCard(
    titel: String,
    state: SignatureState,
    gespeichert: Bitmap?,
    onDelete: () -> Unit
) {
    val hatNeueStriche = state.hasContent()
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (gespeichert != null && !hatNeueStriche) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Unterschrift vorhanden",
                        tint = OkGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDelete) { Text("Löschen") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                // Gespeicherte Unterschrift als Hintergrund, solange nichts Neues gezeichnet wird
                if (gespeichert != null && !hatNeueStriche) {
                    Image(
                        bitmap = gespeichert.asImageBitmap(),
                        contentDescription = "Gespeicherte Unterschrift",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SignaturePad(state = state, modifier = Modifier.fillMaxSize())
            }
            Text(
                if (gespeichert != null && !hatNeueStriche)
                    "Unterschrift gespeichert – zum Ersetzen einfach neu unterschreiben."
                else
                    "Mit dem Finger im Feld unterschreiben – wird beim Speichern/PDF mitgesichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
