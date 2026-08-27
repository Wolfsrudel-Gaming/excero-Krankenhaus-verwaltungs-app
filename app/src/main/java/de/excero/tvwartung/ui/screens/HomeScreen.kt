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
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber
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
    onSuche: () -> Unit,
    onGlobalSuche: () -> Unit
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

    // „Nur ungeprüfte" – kann von der Dashboard-Kachel „Offene Zimmer" gesetzt werden
    val nurOffenGlobal by viewModel.zimmerNurOffen.collectAsState()
    var nurOffen by remember { mutableStateOf(false) }
    LaunchedEffect(nurOffenGlobal) {
        if (nurOffenGlobal) { nurOffen = true; viewModel.setZimmerNurOffen(false) }
    }

    val filtered = remember(aktiveRooms, query, nurOffen, checkedInPeriod) {
        aktiveRooms.filter { room ->
            val passtSuche = query.isBlank() ||
                room.id.contains(query, true) ||
                room.station.contains(query, true) ||
                room.zimmer.contains(query, true) ||
                room.seriennummer.contains(query, true) ||
                room.freenetId.contains(query, true)
            passtSuche && (!nurOffen || room.id !in checkedInPeriod)
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.station }.toSortedMap(stationComparator)
    }
    var sortModus by remember { mutableStateOf(SortModus.STATION) }
    val flach = remember(filtered, sortModus) {
        when (sortModus) {
            SortModus.STATION -> filtered
            SortModus.ZULETZT_NEU -> filtered.sortedByDescending { it.letztePruefung }
            SortModus.LAENGSTE_OFFEN ->
                filtered.sortedWith(compareBy { it.letztePruefung.ifBlank { "0000-00-00" } })
            SortModus.FREENET ->
                filtered.sortedWith(compareBy { it.gueltigBis.ifBlank { "9999-99-99" } })
        }
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
                IconButton(onClick = onGlobalSuche) {
                    Icon(Icons.Outlined.Search, contentDescription = "Suchen")
                }
                IconButton(onClick = onVerwaltung) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = "Material & Prüfpunkte")
                }
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Outlined.Upload, contentDescription = "Export")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
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
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = nurOffen,
                onClick = { nurOffen = !nurOffen },
                label = { Text("Nur ungeprüfte") }
            )
            AssistChip(
                onClick = onFreenet,
                label = { Text("Freenet-Ablauf") },
                leadingIcon = {
                    Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            AssistChip(
                onClick = onSuche,
                label = { Text("Berichte & Papierkorb") },
                leadingIcon = {
                    Icon(Icons.Outlined.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            AssistChip(
                onClick = onStatistik,
                label = { Text("Statistik") },
                leadingIcon = {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        // Sortierung
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            SortModus.entries.forEach { m ->
                FilterChip(
                    selected = sortModus == m,
                    onClick = { sortModus = m },
                    label = { Text(m.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sortModus != SortModus.STATION) {
                items(flach.size, key = { "flach_${flach[it].id}" }) { index ->
                    RoomCard(
                        room = flach[index],
                        checkedToday = flach[index].id in checkedInPeriod,
                        blocked = flach[index].id in gesperrt,
                        pruefer = prueferProZimmer[flach[index].id] ?: "",
                        kompakt = settings.kompaktZimmerliste,
                        zeigeLetztePruefung = true,
                        onClick = { onRoomClick(flach[index].id) }
                    )
                }
            }
            if (sortModus == SortModus.STATION) grouped.forEach { (station, stationRooms) ->
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
                                Icons.Outlined.MeetingRoom,
                                contentDescription = "Zutritt für Station $station festlegen",
                                tint = if (gesperrtCount > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onStundenzettel(station) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Assignment,
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
                        kompakt = settings.kompaktZimmerliste,
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
        Icon(Icons.Outlined.Add, contentDescription = "Neues Zimmer anlegen")
    }
    }

    sperrDialogStation?.let { station ->
        val stationRooms = rooms.filter { it.station == station && !it.inaktiv }
        SperrDialog(
            station = station,
            zimmer = stationRooms.map { it.id to it.zimmer },
            gesperrt = gesperrt,
            onToggle = { roomId, blocked, grund, wiedervorlage ->
                viewModel.setKeinZutritt(roomId, blocked, grund, wiedervorlage)
            },
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
    onToggle: (String, Boolean, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var grund by remember { mutableStateOf("") }
    var wiedervorlage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Station $station – Zutritt") },
        text = {
            Column {
                Text(
                    "Zimmer ankreuzen, die laut Station nicht betreten werden dürfen. " +
                        "Grund und Wiedervorlage gelten für die jetzt angekreuzten Zimmer.",
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
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = wiedervorlage,
                    onValueChange = { wiedervorlage = it },
                    label = { Text("Wiedervorlage am (TT.MM.JJJJ, optional)") },
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
                                onCheckedChange = {
                                    val iso = if (it) Dates.germanToIso(wiedervorlage) ?: "" else ""
                                    onToggle(roomId, it, grund, iso)
                                }
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

/** Sortier-/Filtermodi der Zimmerliste. */
private enum class SortModus(val label: String) {
    STATION("Nach Station"),
    ZULETZT_NEU("Zuletzt geprüft"),
    LAENGSTE_OFFEN("Am längsten offen"),
    FREENET("Freenet-Ablauf")
}

@Composable
private fun RoomCard(
    room: TvRoom,
    checkedToday: Boolean,
    blocked: Boolean,
    pruefer: String = "",
    kompakt: Boolean = false,
    zeigeLetztePruefung: Boolean = false,
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
                .padding(horizontal = 14.dp, vertical = if (kompakt) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (blocked) Icons.Outlined.Block else Icons.Outlined.Tv,
                contentDescription = null,
                tint = if (blocked) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (kompakt) 22.dp else 28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (zeigeLetztePruefung) "${room.station} · Zimmer ${room.zimmer}"
                        else "Zimmer ${room.zimmer}",
                        style = if (kompakt) MaterialTheme.typography.bodyLarge
                        else MaterialTheme.typography.titleMedium,
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
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Geprüft",
                            tint = OkGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        if (pruefer.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            StatusBadge(pruefer, OkGreen)
                        }
                    }
                    // Kompakt: Freenet-Ampel kompakt rechts in die Titelzeile
                    if (kompakt) {
                        Spacer(Modifier.weight(1f))
                        GueltigBisBadge(room.gueltigBis)
                    }
                }
                if (!kompakt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            room.tvTyp.ifBlank { "TV-Typ unbekannt" } + " · SN ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MonoText(
                            room.seriennummer.ifBlank { "–" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    if (zeigeLetztePruefung) {
                        Text(
                            "Zuletzt geprüft: " +
                                (room.letztePruefung.takeIf { it.isNotBlank() }
                                    ?.let { Dates.isoToGerman(it) } ?: "noch nie"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (room.letztePruefung.isBlank()) WarnAmber
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    GueltigBisBadge(room.gueltigBis)
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
