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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

/**
 * Berichtssuche über alle Prüfberichte (Freitext + Filter) sowie der
 * Papierkorb mit gelöschten Berichten zum Wiederherstellen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SucheScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val berichte by viewModel.alleBerichte.collectAsState()
    val geloescht by viewModel.geloeschteBerichte.collectAsState()
    val rooms by viewModel.rooms.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var nurNio by remember { mutableStateOf(false) }
    var stationFilter by remember { mutableStateOf("") }
    var mitarbeiterFilter by remember { mutableStateOf("") }

    val roomsById = remember(rooms) { rooms.associateBy { it.id } }
    val stationen = remember(rooms) {
        rooms.map { it.station }.distinct().sortedWith(stationComparator)
    }
    val mitarbeiter = remember(berichte) {
        berichte.map { it.mitarbeiter }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val treffer = remember(berichte, roomsById, query, nurNio, stationFilter, mitarbeiterFilter) {
        berichte.filter { insp ->
            val station = roomsById[insp.roomId]?.station.orEmpty()
            (stationFilter.isBlank() || station == stationFilter) &&
                (mitarbeiterFilter.isBlank() || insp.mitarbeiter == mitarbeiterFilter) &&
                (!nurNio || (insp.punkte() + insp.extraPunkteListe()).any { it.second == false }) &&
                (query.isBlank() || passtZurSuche(insp, station, query))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Berichte durchsuchen", fontWeight = FontWeight.Bold)
                    Text(
                        "${berichte.size} Berichte · ${geloescht.size} im Papierkorb",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Suche") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Papierkorb (${geloescht.size})") }
            )
        }

        if (tab == 0) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Zimmer, Bemerkung, Arbeit, Mitarbeiter …") },
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
                    selected = nurNio,
                    onClick = { nurNio = !nurNio },
                    label = { Text("nur n.i.O.") }
                )
                AuswahlChip(
                    label = "Station",
                    auswahl = stationFilter,
                    optionen = stationen,
                    onAuswahl = { stationFilter = it }
                )
                AuswahlChip(
                    label = "Mitarbeiter",
                    auswahl = mitarbeiterFilter,
                    optionen = mitarbeiter,
                    onAuswahl = { mitarbeiterFilter = it }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(treffer.size, key = { treffer[it].id }) { index ->
                    BerichtKarte(
                        insp = treffer[index],
                        station = roomsById[treffer[index].roomId]?.station.orEmpty(),
                        onClick = { onOpen(treffer[index].id) }
                    )
                }
                if (treffer.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            titel = if (query.isBlank() && stationFilter.isBlank() &&
                                mitarbeiterFilter.isBlank() && !nurNio
                            ) "Noch keine Berichte" else "Keine Treffer",
                            hinweis = "Suchbegriff anpassen oder Filter zurücksetzen."
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(geloescht.size, key = { geloescht[it].id }) { index ->
                    val insp = geloescht[index]
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${insp.roomId} · ${Dates.isoToGerman(insp.datum)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (insp.mitarbeiter.isNotBlank()) {
                                    Text(
                                        "geprüft von ${insp.mitarbeiter}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.stelleBerichtWieder(insp.id) }) {
                                Icon(
                                    Icons.Outlined.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.width(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Wiederherstellen")
                            }
                        }
                    }
                }
                if (geloescht.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Delete,
                            titel = "Papierkorb ist leer",
                            hinweis = "Berichte lassen sich in der Berichtsansicht über das " +
                                "Papierkorb-Symbol löschen und hier wiederherstellen."
                        )
                    }
                }
            }
        }
    }
}

private fun passtZurSuche(insp: Inspection, station: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val texte = sequenceOf(
        insp.roomId, station, insp.datum, Dates.isoToGerman(insp.datum), insp.mitarbeiter,
        insp.bemerkungen, insp.bemerkungEmpfang, insp.bemerkungSeriennummer,
        insp.bemerkungFreenetId, insp.bemerkungDvd, insp.bemerkungFernbedienung,
        insp.bemerkungHalterung
    ) + insp.arbeitenListe().asSequence() +
        insp.extraPunkteListe().asSequence().flatMap { sequenceOf(it.first, it.third) }
    return texte.any { it.contains(q, ignoreCase = true) }
}

/** Filter-Chip mit Dropdown-Auswahl ("" = alle). */
@Composable
private fun AuswahlChip(
    label: String,
    auswahl: String,
    optionen: List<String>,
    onAuswahl: (String) -> Unit
) {
    var offen by remember { mutableStateOf(false) }
    FilterChip(
        selected = auswahl.isNotBlank(),
        onClick = { offen = true },
        label = { Text(if (auswahl.isBlank()) label else "$label: $auswahl") }
    )
    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
        DropdownMenuItem(
            text = { Text("Alle") },
            onClick = { onAuswahl(""); offen = false }
        )
        optionen.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = { onAuswahl(option); offen = false }
            )
        }
    }
}

@Composable
private fun BerichtKarte(insp: Inspection, station: String, onClick: () -> Unit) {
    val alle = insp.punkte() + insp.extraPunkteListe()
    val nio = alle.count { it.second == false }
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${insp.roomId} · ${Dates.isoToGerman(insp.datum)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val details = buildList {
                    if (station.isNotBlank()) add("Station $station")
                    if (insp.mitarbeiter.isNotBlank()) add(insp.mitarbeiter)
                    val arbeiten = insp.arbeitenListe()
                    if (arbeiten.isNotEmpty()) add(arbeiten.joinToString(", "))
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (insp.bemerkungen.isNotBlank()) {
                    Text(
                        insp.bemerkungen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (nio > 0) {
                StatusBadge("$nio n.i.O.", ErrorRed)
            } else {
                StatusBadge("i.O.", OkGreen)
            }
        }
    }
}
