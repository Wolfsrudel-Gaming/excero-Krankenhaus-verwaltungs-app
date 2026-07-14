package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    viewModel: AppViewModel,
    roomId: String,
    onBack: () -> Unit,
    onStartPruefbogen: () -> Unit
) {
    val room by viewModel.room(roomId).collectAsState(initial = null)
    val current = room ?: return

    var photoRefresh by remember { mutableIntStateOf(0) }
    val photos = remember(roomId, photoRefresh) { viewModel.photoStore.photosToday(roomId) }

    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) pendingPhoto?.delete()
        pendingPhoto = null
        photoRefresh++
    }

    fun capture(label: String) {
        val file = viewModel.photoStore.newPhotoFile(roomId, label)
        pendingPhoto = file
        takePicture.launch(viewModel.photoStore.uriFor(file))
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Station ${current.station} · Zimmer ${current.zimmer}", fontWeight = FontWeight.Bold)
                    Text(current.id, style = MaterialTheme.typography.labelMedium)
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
            Button(
                onClick = onStartPruefbogen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Prüfbogen ausfüllen", style = MaterialTheme.typography.titleMedium)
            }

            StammdatenCard(current, onSave = { viewModel.updateRoom(it) })

            PhotoCard(
                photos = photos,
                onCaptureFern = { capture("fern") },
                onCaptureNah = { capture("nah") },
                onDelete = {
                    viewModel.photoStore.delete(it)
                    photoRefresh++
                }
            )

            LebenslaufCard(current.lebenslauf)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StammdatenCard(room: TvRoom, onSave: (TvRoom) -> Unit) {
    var editing by remember(room.id) { mutableStateOf(false) }
    var tvTyp by remember(room) { mutableStateOf(room.tvTyp) }
    var seriennummer by remember(room) { mutableStateOf(room.seriennummer) }
    var freenetId by remember(room) { mutableStateOf(room.freenetId) }
    var gueltigBis by remember(room) { mutableStateOf(Dates.isoToGerman(room.gueltigBis)) }
    var dateError by remember { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hinterlegte Daten",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!editing) {
                    TextButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bearbeiten")
                    }
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = tvTyp, onValueChange = { tvTyp = it },
                    label = { Text("TV-Typ") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = seriennummer, onValueChange = { seriennummer = it },
                    label = { Text("TV Seriennummer") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = freenetId, onValueChange = { freenetId = it },
                    label = { Text("Freenet TV-ID") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gueltigBis,
                    onValueChange = { gueltigBis = it; dateError = false },
                    label = { Text("Freenet gültig bis (TT.MM.JJJJ)") },
                    singleLine = true,
                    isError = dateError,
                    supportingText = if (dateError) {
                        { Text("Datum bitte als TT.MM.JJJJ eingeben") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val iso = Dates.germanToIso(gueltigBis)
                        if (iso == null) {
                            dateError = true
                        } else {
                            onSave(
                                room.copy(
                                    tvTyp = tvTyp.trim(),
                                    seriennummer = seriennummer.trim(),
                                    freenetId = freenetId.trim(),
                                    gueltigBis = iso
                                )
                            )
                            editing = false
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Speichern")
                    }
                    TextButton(onClick = {
                        editing = false
                        tvTyp = room.tvTyp
                        seriennummer = room.seriennummer
                        freenetId = room.freenetId
                        gueltigBis = Dates.isoToGerman(room.gueltigBis)
                        dateError = false
                    }) { Text("Abbrechen") }
                }
            } else {
                InfoRow("TV-Typ", room.tvTyp)
                InfoRow("TV Seriennummer", room.seriennummer)
                InfoRow("Freenet TV-ID", room.freenetId)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Freenet gültig bis",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    GueltigBisBadge(room.gueltigBis)
                }
                InfoRow("Letzte Prüfung", Dates.isoToGerman(room.letztePruefung))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.ifBlank { "–" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PhotoCard(
    photos: List<File>,
    onCaptureFern: () -> Unit,
    onCaptureNah: () -> Unit,
    onDelete: (File) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Fotos (heute: ${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCaptureFern, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto fern")
                }
                FilledTonalButton(onClick = onCaptureNah, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto nah")
                }
            }
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos.size) { index ->
                        val file = photos[index]
                        Box {
                            AsyncImage(
                                model = file,
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { onDelete(file) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Foto löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Noch keine Fotos für heute – ein Foto von fern und eins von nah aufnehmen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LebenslaufCard(lebenslauf: String) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Lebenslauf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            val entries = lebenslauf.lines().filter { it.isNotBlank() }.reversed()
            if (entries.isEmpty()) {
                Text(
                    "Keine Einträge vorhanden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEach { entry ->
                    Text(entry.trim(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
