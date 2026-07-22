package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onRoomClick: (String) -> Unit,
    onExportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStundenzettel: (String) -> Unit,
    onVerwaltung: () -> Unit,
    onNeuesZimmer: () -> Unit,
    onFreenet: () -> Unit,
    onStatistik: () -> Unit,
    onSuche: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val inspectionsInPeriod by viewModel.inspectionsInPeriod.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val gesperrt by viewModel.gesperrteZimmer.collectAsState()
    var query by remember { mutableStateOf("") }
    var sperrDialogStation by remember { mutableStateOf<String?>(null) }

    val checkedInPeriod = remember(inspectionsInPeriod) {
        inspectionsInPeriod.map { it.roomId }.toSet()
    }
    // Wer hat welches Zimmer im Zeitraum geprüft (Team-Sicht)
    val prueferProZimmer = remember(inspectionsInPeriod) {
        inspectionsInPeriod.filter { it.mitarbeiter.isNotBlank() }
            .associate { it.roomId to it.mitarbeiter }
    }
    val update by viewModel.updateVerfuegbar.collectAsState()
    val aktiveRooms = remember(rooms) { rooms.filter { !it.inaktiv } }
    val inaktiveRooms = remember(rooms) { rooms.filter { it.inaktiv } }
    val filtered = remember(aktiveRooms, query) {
        if (query.isBlank()) aktiveRooms
        else aktiveRooms.filter {
            it.id.contains(query, true) ||
                it.station.contains(query, true) ||
                it.zimmer.contains(query, true) ||
                it.seriennummer.contains(query, true) ||
                it.freenetId.contains(query, true)
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.station }.toSortedMap(stationComparator)
    }
    var zeigeInaktive by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("TV-Wartung KKH", fontWeight = FontWeight.Bold)
                    Text(
                        "${Dates.todayGerman()} · ${checkedInPeriod.size} von ${aktiveRooms.size} geprüft (${settings.beschreibung()})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            actions = {
                SyncStatusIndicator(viewModel)
                IconButton(onClick = onVerwaltung) {
                    Icon(Icons.Default.Inventory2, contentDescription = "Material & Prüfpunkte")
                }
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Default.Upload, contentDescription = "Export")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        update?.let { (_, versionName) ->
            Card(
                onClick = { viewModel.installiereUpdate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔄 Update auf Version $versionName verfügbar – tippen zum Installieren",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Zimmer, Station, Seriennummer …") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onFreenet,
                label = { Text("Freenet-Ablauf") },
                leadingIcon = {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            AssistChip(
                onClick = onSuche,
                label = { Text("Berichte & Papierkorb") },
                leadingIcon = {
                    Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            AssistChip(
                onClick = onStatistik,
                label = { Text("Statistik") },
                leadingIcon = {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grouped.forEach { (station, stationRooms) ->
                item(key = "header_$station") {
                    val gesperrtCount = stationRooms.count { it.id in gesperrt }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp)
                    ) {
                        Text(
                            "Station $station",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            buildString {
                                append("${stationRooms.count { it.id in checkedInPeriod }}/${stationRooms.size} geprüft")
                                if (gesperrtCount > 0) append(" · $gesperrtCount kein Zutritt")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (gesperrtCount > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { sperrDialogStation = station }) {
                            Icon(
                                Icons.Default.MeetingRoom,
                                contentDescription = "Zutritt für Station $station festlegen",
                                tint = if (gesperrtCount > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onStundenzettel(station) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = "Stundenzettel für Station $station",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(stationRooms.size, key = { stationRooms[it].id }) { index ->
                    RoomCard(
                        room = stationRooms[index],
                        checkedToday = stationRooms[index].id in checkedInPeriod,
                        blocked = stationRooms[index].id in gesperrt,
                        pruefer = prueferProZimmer[stationRooms[index].id] ?: "",
                        onClick = { onRoomClick(stationRooms[index].id) }
                    )
                }
            }
            if (inaktiveRooms.isNotEmpty()) {
                item(key = "inaktiv_toggle") {
                    TextButton(
                        onClick = { zeigeInaktive = !zeigeInaktive },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            if (zeigeInaktive) "Inaktive Zimmer ausblenden"
                            else "Inaktive Zimmer anzeigen (${inaktiveRooms.size})"
                        )
                    }
                }
                if (zeigeInaktive) {
                    items(inaktiveRooms.size, key = { "inaktiv_${inaktiveRooms[it].id}" }) { index ->
                        val room = inaktiveRooms[index]
                        Card(
                            onClick = { onRoomClick(room.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${room.station} · Zimmer ${room.zimmer}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge("INAKTIV", MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    FloatingActionButton(
        onClick = onNeuesZimmer,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Neues Zimmer anlegen")
    }
    }

    sperrDialogStation?.let { station ->
        val stationRooms = rooms.filter { it.station == station && !it.inaktiv }
        SperrDialog(
            station = station,
            zimmer = stationRooms.map { it.id to it.zimmer },
            gesperrt = gesperrt,
            onToggle = { roomId, blocked, grund -> viewModel.setKeinZutritt(roomId, blocked, grund) },
            onDismiss = { sperrDialogStation = null }
        )
    }
}

/**
 * Nach der Anmeldung bei der Stationsschwester: Zimmer ankreuzen, die bei
 * dieser Anfahrt nicht betreten werden dürfen.
 */
@Composable
private fun SperrDialog(
    station: String,
    zimmer: List<Pair<String, String>>,   // (roomId, Zimmerbezeichnung)
    gesperrt: Set<String>,
    onToggle: (String, Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    var grund by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Station $station – Zutritt") },
        text = {
            Column {
                Text(
                    "Zimmer ankreuzen, die laut Station nicht betreten werden dürfen. " +
                        "Die Markierung gilt für den aktuellen Prüfzeitraum und wird mit " +
                        "Datum im Lebenslauf vermerkt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = grund,
                    onValueChange = { grund = it },
                    label = { Text("Grund (optional, z. B. Isolation)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    zimmer.forEach { (roomId, bezeichnung) ->
                        val blocked = roomId in gesperrt
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = blocked,
                                onCheckedChange = { onToggle(roomId, it, grund) }
                            )
                            Text(
                                "Zimmer $bezeichnung",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (blocked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

@Composable
private fun RoomCard(
    room: TvRoom,
    checkedToday: Boolean,
    blocked: Boolean,
    pruefer: String = "",
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                blocked -> MaterialTheme.colorScheme.errorContainer
                checkedToday -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (blocked) Icons.Default.Block else Icons.Default.Tv,
                contentDescription = null,
                tint = if (blocked) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Zimmer ${room.zimmer}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (blocked) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (blocked) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge("KEIN ZUTRITT", MaterialTheme.colorScheme.error)
                    } else if (checkedToday) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Geprüft",
                            tint = OkGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        if (pruefer.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            StatusBadge(pruefer, OkGreen)
                        }
                    }
                }
                Text(
                    listOf(
                        room.tvTyp.ifBlank { "TV-Typ unbekannt" },
                        "SN ${room.seriennummer.ifBlank { "–" }}"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                GueltigBisBadge(room.gueltigBis)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
