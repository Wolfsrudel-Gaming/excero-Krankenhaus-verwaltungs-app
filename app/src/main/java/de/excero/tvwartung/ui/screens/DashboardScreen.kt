package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber
import de.excero.tvwartung.util.Dates

/**
 * Neue Startseite (2.0-Beta): KPI-Dashboard statt direkt der Zimmerliste.
 * Team-weite Kennzahlen mit Sprung zu den betroffenen Zimmern/Berichten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onMenuClick: () -> Unit,
    onRoomClick: (String) -> Unit,
    onFreenet: () -> Unit,
    onArbeitszeit: () -> Unit,
    onVerwaltung: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val inspectionsInPeriod by viewModel.inspectionsInPeriod.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val alleStundenzettel by viewModel.alleStundenzettel.collectAsState()
    val lagerWarnungen by viewModel.lagerWarnungen.collectAsState()

    var stationFilter by remember { mutableStateOf("") }
    val aktiveRooms = remember(rooms, stationFilter) {
        rooms.filter { !it.inaktiv && (stationFilter.isBlank() || it.station == stationFilter) }
    }
    val stationen = remember(rooms) { rooms.map { it.station }.distinct().sortedWith(stationComparator) }
    val geprueft = remember(inspectionsInPeriod, stationFilter, rooms) {
        val roomsById = rooms.associateBy { it.id }
        inspectionsInPeriod.map { it.roomId }.toSet()
            .filter { stationFilter.isBlank() || roomsById[it]?.station == stationFilter }
    }
    val freenetKritisch = remember(aktiveRooms) {
        aktiveRooms.count { FreenetStatus.of(it.gueltigBis) == FreenetStatus.ABGELAUFEN }
    }
    val freenetBald = remember(aktiveRooms) {
        aktiveRooms.count { FreenetStatus.of(it.gueltigBis) == FreenetStatus.BALD }
    }
    val offeneZettel = remember(alleStundenzettel) {
        alleStundenzettel.count { it.stunden.isBlank() }
    }
    val letzte = remember(inspectionsInPeriod, stationFilter, rooms) {
        val roomsById = rooms.associateBy { it.id }
        inspectionsInPeriod
            .filter { stationFilter.isBlank() || roomsById[it.roomId]?.station == stationFilter }
            .sortedByDescending { it.datum + it.id }
            .take(8)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Dashboard", fontWeight = FontWeight.Bold)
                    Text(
                        "${Dates.todayGerman()} · ${settings.beschreibung()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menü öffnen")
                }
            },
            actions = { SyncStatusIndicator(viewModel) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = stationFilter.isBlank(),
                        onClick = { stationFilter = "" },
                        label = { Text("Alle Stationen") }
                    )
                    stationen.forEach { station ->
                        FilterChip(
                            selected = stationFilter == station,
                            onClick = { stationFilter = if (stationFilter == station) "" else station },
                            label = { Text(station) }
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiKachel(
                        titel = "Geprüft",
                        wert = "${geprueft.size} / ${aktiveRooms.size}",
                        farbe = OkGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiKachel(
                        titel = "Offene Zettel",
                        wert = "$offeneZettel",
                        farbe = MaterialTheme.colorScheme.primary,
                        onClick = onArbeitszeit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiKachel(
                        titel = "Freenet kritisch",
                        wert = "$freenetKritisch",
                        farbe = if (freenetKritisch > 0) ErrorRed else OkGreen,
                        onClick = onFreenet,
                        modifier = Modifier.weight(1f)
                    )
                    KpiKachel(
                        titel = "Freenet bald fällig",
                        wert = "$freenetBald",
                        farbe = if (freenetBald > 0) WarnAmber else OkGreen,
                        onClick = onFreenet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (lagerWarnungen.isNotEmpty()) {
                item {
                    KpiKachel(
                        titel = "Lager-Nachbestellungen",
                        wert = "${lagerWarnungen.size}",
                        farbe = WarnAmber,
                        onClick = onVerwaltung,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    "Zuletzt geprüft",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (letzte.isEmpty()) {
                item {
                    Text(
                        "Noch keine Prüfungen im aktuellen Zeitraum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(letzte.size, key = { letzte[it].id }) { index ->
                val insp = letzte[index]
                Card(
                    onClick = { onRoomClick(insp.roomId) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(insp.roomId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (insp.mitarbeiter.isNotBlank()) {
                                Text(
                                    insp.mitarbeiter,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            Dates.isoToGerman(insp.datum),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun KpiKachel(
    titel: String,
    wert: String,
    farbe: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val card = @Composable {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                wert,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = farbe
            )
            Spacer(Modifier.height(2.dp))
            Text(
                titel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { card() }
    } else {
        Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { card() }
    }
}
