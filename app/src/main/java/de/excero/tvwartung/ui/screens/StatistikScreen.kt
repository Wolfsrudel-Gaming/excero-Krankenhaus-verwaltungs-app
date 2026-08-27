package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen

/**
 * Interne Statistik über alle Prüfberichte: Prüfungen pro Monat, n.i.O.-Quoten
 * je Prüfpunkt, Verlängerungen und Prüfungen je Mitarbeiter.
 * Bewusst NUR in der App einsehbar – wird nirgends exportiert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistikScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val alleB by viewModel.alleBerichte.collectAsState()
    val rooms by viewModel.rooms.collectAsState()

    var zeitraum by remember { mutableStateOf(StatZeitraum.GESAMT) }
    val berichte = remember(alleB, zeitraum) {
        val heute = java.time.LocalDate.now()
        val cutoff = when (zeitraum) {
            StatZeitraum.GESAMT -> null
            StatZeitraum.TAGE30 -> heute.minusDays(30).toString()
            StatZeitraum.MONATE12 -> heute.minusDays(365).toString()
            StatZeitraum.JAHR -> "${heute.year}-01-01"
        }
        if (cutoff == null) alleB else alleB.filter { it.datum >= cutoff }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Statistik", fontWeight = FontWeight.Bold)
                    Text(
                        "intern – wird nicht exportiert",
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

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Zeitraum-Auswahl
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatZeitraum.entries.forEach { z ->
                    FilterChip(
                        selected = zeitraum == z,
                        onClick = { zeitraum = z },
                        label = { Text(z.label) }
                    )
                }
            }

            // Kennzahlen oben
            val zimmerGeprueft = remember(berichte) { berichte.map { it.roomId }.toSet().size }
            val verlaengert = remember(berichte) { berichte.count { it.freenetVerlaengert == true } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KennzahlCard("Prüfungen", "${berichte.size}", Modifier.weight(1f))
                KennzahlCard("Zimmer geprüft", "$zimmerGeprueft / ${rooms.count { !it.inaktiv }}", Modifier.weight(1f))
                KennzahlCard("Verlängert", "$verlaengert", Modifier.weight(1f))
            }

            // Kreisdiagramm: i.O. vs. n.i.O. über alle Prüfpunkte
            val ioNio = remember(berichte) {
                var io = 0; var nio = 0
                berichte.forEach { insp ->
                    insp.punkte().forEach { (_, e, _) ->
                        if (e == true) io++ else if (e == false) nio++
                    }
                }
                io to nio
            }
            if (ioNio.first + ioNio.second > 0) {
                StatCard("i.O. / n.i.O. (alle Prüfpunkte)") {
                    IoNioDonut(io = ioNio.first, nio = ioNio.second)
                }
            }

            // Prüfungen pro Monat (letzte 12 Monate mit Prüfungen)
            val proMonat = remember(berichte) {
                berichte.filter { it.datum.length >= 7 }
                    .groupingBy { it.datum.substring(0, 7) }
                    .eachCount()
                    .toSortedMap()
                    .toList()
                    .takeLast(12)
            }
            if (proMonat.isNotEmpty()) {
                StatCard("Prüfungen pro Monat") {
                    val max = proMonat.maxOf { it.second }.coerceAtLeast(1)
                    proMonat.forEach { (monat, anzahl) ->
                        BalkenZeile(
                            label = monatLabel(monat),
                            wert = "$anzahl",
                            anteil = anzahl.toFloat() / max,
                            farbe = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // n.i.O.-Quote je Prüfpunkt
            val quoten = remember(berichte) { nioQuoten(berichte) }
            if (quoten.isNotEmpty()) {
                StatCard("n.i.O.-Quote je Prüfpunkt") {
                    quoten.forEach { (titel, nio, gesamt) ->
                        val anteil = if (gesamt == 0) 0f else nio.toFloat() / gesamt
                        BalkenZeile(
                            label = titel,
                            wert = "$nio / $gesamt",
                            anteil = anteil,
                            farbe = if (nio == 0) OkGreen else ErrorRed
                        )
                    }
                }
            }

            // Prüfungen je Mitarbeiter
            val proMitarbeiter = remember(berichte) {
                berichte.filter { it.mitarbeiter.isNotBlank() }
                    .groupingBy { it.mitarbeiter }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
            }
            if (proMitarbeiter.isNotEmpty()) {
                StatCard("Prüfungen je Mitarbeiter") {
                    val max = proMitarbeiter.maxOf { it.second }.coerceAtLeast(1)
                    proMitarbeiter.forEach { (name, anzahl) ->
                        BalkenZeile(
                            label = name,
                            wert = "$anzahl",
                            anteil = anzahl.toFloat() / max,
                            farbe = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Prüfungen je Station
            val roomsById = remember(rooms) { rooms.associateBy { it.id } }
            val proStation = remember(berichte, roomsById) {
                berichte.mapNotNull { roomsById[it.roomId]?.station }
                    .groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedWith(compareBy(stationComparator) { it.first })
            }
            if (proStation.isNotEmpty()) {
                StatCard("Prüfungen je Station") {
                    val max = proStation.maxOf { it.second }.coerceAtLeast(1)
                    proStation.forEach { (station, anzahl) ->
                        BalkenZeile(
                            label = "Station $station",
                            wert = "$anzahl",
                            anteil = anzahl.toFloat() / max,
                            farbe = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (berichte.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.QueryStats,
                    titel = "Noch keine Daten",
                    hinweis = "Sobald Prüfberichte erfasst sind, erscheinen hier Kennzahlen und Diagramme."
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** (Titel, n.i.O.-Anzahl, beantwortete Prüfungen) je Prüfpunkt, Bogen-Reihenfolge zuerst. */
private fun nioQuoten(berichte: List<Inspection>): List<Triple<String, Int, Int>> {
    val nio = LinkedHashMap<String, Int>()
    val gesamt = LinkedHashMap<String, Int>()
    berichte.forEach { insp ->
        (insp.punkte() + insp.extraPunkteListe()).forEach { (titel, ergebnis, _) ->
            if (ergebnis != null) {
                gesamt[titel] = (gesamt[titel] ?: 0) + 1
                if (!ergebnis) nio[titel] = (nio[titel] ?: 0) + 1
            }
        }
    }
    return gesamt.map { (titel, g) -> Triple(titel, nio[titel] ?: 0, g) }
}

private fun monatLabel(isoMonat: String): String {
    val namen = listOf(
        "Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
        "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"
    )
    val teile = isoMonat.split("-")
    val monat = teile.getOrNull(1)?.toIntOrNull()
    return if (monat in 1..12) "${namen[monat!! - 1]} ${teile[0]}" else isoMonat
}

@Composable
private fun KennzahlCard(titel: String, wert: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                wert,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                titel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(titel: String, inhalt: @Composable () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            inhalt()
        }
    }
}

@Composable
private fun BalkenZeile(
    label: String,
    wert: String,
    anteil: Float,
    farbe: androidx.compose.ui.graphics.Color
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                wert,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(anteil.coerceIn(0.02f, 1f))
                    .background(farbe, RoundedCornerShape(4.dp))
            )
        }
    }
}

enum class StatZeitraum(val label: String) {
    GESAMT("Gesamt"),
    TAGE30("Letzte 30 Tage"),
    JAHR("Dieses Jahr"),
    MONATE12("Letzte 12 Monate")
}

/** Ringdiagramm i.O. (grün) vs. n.i.O. (rot) mit Legende. */
@Composable
private fun IoNioDonut(io: Int, nio: Int) {
    val total = (io + nio).coerceAtLeast(1)
    val ioAnteil = io.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val bogen = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            // Voller Ring in n.i.O.-Farbe, dann i.O.-Anteil grün darüber
            drawArc(ErrorRed, -90f, 360f, false, topLeft = topLeft, size = bogen,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(OkGreen, -90f, ioAnteil * 360f, false, topLeft = topLeft, size = bogen,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DonutLegende(OkGreen, "i.O.", io, "${(ioAnteil * 100).toInt()} %")
            DonutLegende(ErrorRed, "n.i.O.", nio, "${(100 - (ioAnteil * 100).toInt())} %")
        }
    }
}

@Composable
private fun DonutLegende(farbe: androidx.compose.ui.graphics.Color, label: String, wert: Int, prozent: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(farbe))
        Text("$label: $wert  ($prozent)", style = MaterialTheme.typography.bodyMedium)
    }
}
