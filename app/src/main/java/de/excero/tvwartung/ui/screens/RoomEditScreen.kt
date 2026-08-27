package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates

/** Neues Zimmer anlegen – bei neuem Stationsnamen entsteht die Station automatisch mit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomEditScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val stationen = remember(rooms) {
        rooms.map { it.station }.distinct().sortedWith(stationComparator)
    }

    var station by remember { mutableStateOf("") }
    var zimmer by remember { mutableStateOf("") }
    var tvTyp by remember { mutableStateOf("") }
    var seriennummer by remember { mutableStateOf("") }
    var freenetId by remember { mutableStateOf("") }
    var gueltigBis by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf<String?>(null) }

    val neueId = "${station.trim()}_${zimmer.trim()}"
    val existiert = rooms.any { it.id.equals(neueId, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Neues Zimmer anlegen", fontWeight = FontWeight.Bold) },
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
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StationAuswahl(
                        value = station,
                        onValueChange = { station = it },
                        bekannteStationen = stationen
                    )
                    OutlinedTextField(
                        value = zimmer, onValueChange = { zimmer = it },
                        label = { Text("Zimmer (z. B. 01a, 05, SZ)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (station.isNotBlank() && zimmer.isNotBlank()) {
                        Text(
                            if (existiert) "⚠ $neueId existiert bereits"
                            else "Neue Zimmer-ID: $neueId" +
                                if (stationen.none { it.equals(station.trim(), true) })
                                    " (neue Station ${station.trim()})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (existiert) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gerät (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    OutlinedTextField(
                        value = freenetId, onValueChange = { freenetId = it },
                        label = { Text("Freenet TV-ID") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gueltigBis,
                        onValueChange = { gueltigBis = it; fehler = null },
                        label = { Text("Freenet gültig bis (TT.MM.JJJJ)") },
                        singleLine = true,
                        isError = fehler != null,
                        supportingText = fehler?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    val iso = Dates.germanToIso(gueltigBis)
                    if (iso == null) {
                        fehler = "Datum bitte als TT.MM.JJJJ eingeben"
                        return@Button
                    }
                    viewModel.createRoom(
                        TvRoom(
                            id = neueId,
                            station = station.trim(),
                            zimmer = zimmer.trim(),
                            lebenslauf = "",
                            letztePruefung = "",
                            tvTyp = tvTyp.trim(),
                            seriennummer = seriennummer.trim(),
                            freenetId = freenetId.trim(),
                            gueltigBis = iso
                        )
                    ) { onCreated(it) }
                },
                enabled = station.isNotBlank() && zimmer.isNotBlank() && !existiert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Zimmer anlegen")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StationAuswahl(
    value: String,
    onValueChange: (String) -> Unit,
    bekannteStationen: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Station (z. B. A4 – neue Namen legen eine Station an)") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Station auswählen")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            bekannteStationen.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        onValueChange(s)
                        expanded = false
                    }
                )
            }
        }
    }
}
