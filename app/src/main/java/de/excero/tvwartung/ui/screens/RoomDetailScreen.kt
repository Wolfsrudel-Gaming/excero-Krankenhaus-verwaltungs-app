package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.excero.tvwartung.data.ActivityLog
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
    onStartPruefbogen: () -> Unit,
    onOpenBericht: (Long) -> Unit
) {
    val room by viewModel.room(roomId).collectAsState(initial = null)
    val activity by viewModel.activityFor(roomId).collectAsState(initial = emptyList())
    val berichte by viewModel.inspectionsFor(roomId).collectAsState(initial = emptyList())
    val gesperrt by viewModel.gesperrteZimmer.collectAsState()
    val current = room ?: return
    val blocked = roomId in gesperrt
    var zeigeSperrDialog by remember { mutableStateOf(false) }
    var zeigeArchivDialog by remember { mutableStateOf(false) }

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
            if (current.inaktiv) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "ZIMMER INAKTIV",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Dieses Zimmer ist archiviert (TV abgebaut/aufgelöst). Die Historie bleibt erhalten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.setInaktiv(roomId, false) }) {
                            Text("Zimmer reaktivieren")
                        }
                    }
                }
            }

            if (blocked) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "KEIN ZUTRITT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "Dieses Zimmer wurde von der Station für die aktuelle Anfahrt gesperrt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.setKeinZutritt(roomId, false) }) {
                            Text("Zutritt wieder möglich – Sperre aufheben")
                        }
                    }
                }
            }

            Button(
                onClick = onStartPruefbogen,
                enabled = !blocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (blocked) "Kein Zutritt" else "Prüfbogen ausfüllen",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!blocked && !current.inaktiv) {
                TextButton(
                    onClick = { zeigeSperrDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Als „kein Zutritt“ markieren",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            StammdatenCard(
                room = current,
                viewModel = viewModel,
                onSave = { viewModel.updateRoom(it) }
            )

            FreenetCard(current)

            PhotoSection(viewModel = viewModel, roomId = roomId)

            BerichteCard(berichte, onOpenBericht)

            LebenslaufCard(current.lebenslauf)

            ActivityCard(activity)

            if (!current.inaktiv) {
                TextButton(
                    onClick = { zeigeArchivDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Zimmer inaktiv setzen (TV abgebaut/aufgelöst)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (zeigeSperrDialog) {
        var grund by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { zeigeSperrDialog = false },
            title = { Text("Kein Zutritt vermerken") },
            text = {
                Column {
                    Text(
                        "Das Zimmer wird für die aktuelle Anfahrt rot markiert und der " +
                            "Lebenslauf erhält einen Vermerk mit heutigem Datum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = grund,
                        onValueChange = { grund = it },
                        label = { Text("Grund (optional, z. B. Isolation)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setKeinZutritt(roomId, true, grund)
                    zeigeSperrDialog = false
                }) { Text("Vermerken") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeSperrDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (zeigeArchivDialog) {
        AlertDialog(
            onDismissRequest = { zeigeArchivDialog = false },
            title = { Text("Zimmer inaktiv setzen?") },
            text = {
                Text(
                    "Das Zimmer ${current.id} wird aus der normalen Übersicht ausgeblendet. " +
                        "Historie, Prüfberichte und Fotos bleiben erhalten; das Zimmer kann " +
                        "jederzeit reaktiviert werden."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setInaktiv(roomId, true)
                    zeigeArchivDialog = false
                }) { Text("Inaktiv setzen") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeArchivDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun StammdatenCard(
    room: TvRoom,
    viewModel: AppViewModel,
    onSave: (TvRoom) -> Unit
) {
    var editing by remember(room.id) { mutableStateOf(false) }
    var tvTyp by remember(room) { mutableStateOf(room.tvTyp) }
    var seriennummer by remember(room) { mutableStateOf(room.seriennummer) }
    var freenetId by remember(room) { mutableStateOf(room.freenetId) }
    var gueltigBis by remember(room) { mutableStateOf(Dates.isoToGerman(room.gueltigBis)) }
    var dateError by remember { mutableStateOf(false) }

    // Duplikate live prüfen – sowohl im Anzeigemodus (Bestand) als auch beim Bearbeiten
    val freenetDups = viewModel.freenetIdDuplikate(
        if (editing) freenetId else room.freenetId, room.id
    )
    val serialDups = viewModel.seriennummerDuplikate(
        if (editing) seriennummer else room.seriennummer, room.id
    )

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
                TvTypAuswahl(
                    value = tvTyp,
                    onValueChange = { tvTyp = it },
                    bekannteTypen = viewModel.bekannteTvTypen()
                )
                OutlinedTextField(
                    value = seriennummer, onValueChange = { seriennummer = it },
                    label = { Text("TV Seriennummer") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (serialDups.isNotEmpty()) {
                    DuplicateWarning("Seriennummer auch hinterlegt bei: ${serialDups.joinToString()}")
                }
                OutlinedTextField(
                    value = freenetId, onValueChange = { freenetId = it },
                    label = { Text("Freenet TV-ID") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (freenetDups.isNotEmpty()) {
                    DuplicateWarning("Freenet-ID auch registriert bei: ${freenetDups.joinToString()}")
                }
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
                if (serialDups.isNotEmpty()) {
                    DuplicateWarning("Seriennummer auch hinterlegt bei: ${serialDups.joinToString()}")
                }
                InfoRow("Freenet TV-ID", room.freenetId)
                if (freenetDups.isNotEmpty()) {
                    DuplicateWarning("Freenet-ID auch registriert bei: ${freenetDups.joinToString()}")
                }
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
private fun FreenetCard(room: TvRoom) {
    val context = LocalContext.current
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Freenet TV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "ID ${room.freenetId.ifBlank { "–" }} · Verlängerung und Aktivierung laufen über die Freenet-Webseite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { FreenetLinks.open(context, FreenetLinks.VERLAENGERN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verlängern")
                }
                OutlinedButton(
                    onClick = { FreenetLinks.open(context, FreenetLinks.AKTIVIEREN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aktivieren")
                }
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

/** Alle gespeicherten Prüfberichte dieses Zimmers, antippbar zum Ansehen/PDF-Export. */
@Composable
private fun BerichteCard(
    berichte: List<de.excero.tvwartung.data.Inspection>,
    onOpenBericht: (Long) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FactCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Prüfberichte (${berichte.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            if (berichte.isEmpty()) {
                Text(
                    "Noch keine Prüfberichte in der App erfasst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                berichte.forEach { bericht ->
                    val nio = bericht.punkte().count { it.second == false }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                Dates.isoToGerman(bericht.datum),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (nio == 0) "alles i.O." else "$nio Punkt(e) n.i.O.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (nio == 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = { onOpenBericht(bericht.id) }) {
                            Text("Ansehen / PDF")
                        }
                    }
                }
            }
        }
    }
}

/** Internes Bearbeitungsprotokoll (mit Uhrzeit) – wird nicht exportiert. */
@Composable
private fun ActivityCard(entries: List<ActivityLog>) {
    var expanded by remember { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Bearbeitungszeiten (intern)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (entries.size > 5) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Weniger" else "Alle ${entries.size}")
                    }
                }
            }
            Text(
                "Nur zur eigenen Einsicht – wird nicht exportiert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            if (entries.isEmpty()) {
                Text(
                    "Noch keine Bearbeitungen erfasst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                (if (expanded) entries else entries.take(5)).forEach { entry ->
                    Text(
                        "${Dates.isoDateTimeToGerman(entry.zeitpunkt)} · ${entry.aktion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
