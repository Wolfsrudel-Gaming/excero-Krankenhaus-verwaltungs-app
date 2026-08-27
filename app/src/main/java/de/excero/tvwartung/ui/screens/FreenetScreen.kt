package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates

/**
 * Stationsübergreifende Freenet-Ablaufübersicht: alle Zimmer nach Restlaufzeit
 * sortiert, damit ablaufende Karten rechtzeitig verlängert werden können.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreenetScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onRoomClick: (String) -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<FreenetStatus?>(null) }

    val aktive = remember(rooms) { rooms.filter { !it.inaktiv } }
    // Kritischstes zuerst: abgelaufen, dann bald ablaufend, dann ohne Datum, dann gültig
    val sortiert = remember(aktive) {
        aktive.sortedWith(
            compareBy(
                {
                    when (FreenetStatus.of(it.gueltigBis)) {
                        FreenetStatus.ABGELAUFEN -> 0
                        FreenetStatus.BALD -> 1
                        FreenetStatus.UNBEKANNT -> 2
                        FreenetStatus.OK -> 3
                    }
                },
                { Dates.daysUntil(it.gueltigBis) ?: Long.MAX_VALUE }
            )
        )
    }
    val counts = remember(aktive) {
        aktive.groupingBy { FreenetStatus.of(it.gueltigBis) }.eachCount()
    }
    val angezeigt = remember(sortiert, filter) {
        filter?.let { f -> sortiert.filter { FreenetStatus.of(it.gueltigBis) == f } } ?: sortiert
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Freenet-Ablaufübersicht", fontWeight = FontWeight.Bold)
                    Text(
                        "${aktive.size} Zimmer · Stand ${Dates.todayGerman()}",
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

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(FreenetStatus.ABGELAUFEN, FreenetStatus.BALD, FreenetStatus.OK).forEach { status ->
                FilterChip(
                    selected = filter == status,
                    onClick = { filter = if (filter == status) null else status },
                    label = { Text("${status.label} (${counts[status] ?: 0})") }
                )
            }
        }

        OutlinedButton(
            onClick = { FreenetLinks.open(context, FreenetLinks.VERLAENGERN) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.width(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Freenet-Guthaben einlösen / verlängern")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(angezeigt.size, key = { angezeigt[it].id }) { index ->
                val room = angezeigt[index]
                val status = FreenetStatus.of(room.gueltigBis)
                val tage = Dates.daysUntil(room.gueltigBis)
                Card(
                    onClick = { onRoomClick(room.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (status) {
                            FreenetStatus.ABGELAUFEN -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
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
                                "${room.station} · Zimmer ${room.zimmer}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Freenet-ID ${room.freenetId.ifBlank { "–" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tage != null) {
                                Text(
                                    when {
                                        tage < 0 -> "seit ${-tage} Tagen abgelaufen"
                                        tage == 0L -> "läuft heute ab"
                                        else -> "noch $tage Tage"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = status.color
                                )
                            }
                        }
                        GueltigBisBadge(room.gueltigBis)
                    }
                }
            }
        }
    }
}
