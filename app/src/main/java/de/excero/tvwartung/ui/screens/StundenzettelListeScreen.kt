package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.files.SignatureStore
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber
import de.excero.tvwartung.util.Dates

/** Alle gespeicherten Stundenzettel – Wochenübersicht, Status und PDF-Export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StundenzettelListeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onTagAufteilen: () -> Unit = {}
) {
    val zettel by viewModel.alleStundenzettel.collectAsState()
    val einsaetze by viewModel.alleEinsaetze.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val me = settings.mitarbeiter.trim()
    val laufend by remember(me) { viewModel.laufenderEinsatz() }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Arbeitszeit & Stundenzettel", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Erinnerung: Einsatz läuft ungewöhnlich lange (vergessen zu beenden?)
            laufend?.let { einsatz ->
                val stunden = elapsedStunden(einsatz.start)
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (stunden >= 10) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                (if (stunden >= 10) "⏰ Einsatz läuft seit ${stunden} Std." else "▶ Einsatz läuft"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Station ${einsatz.station}, gestartet ${Dates.isoDateTimeToGerman(einsatz.start)}." +
                                    if (stunden >= 10) " Nicht vergessen zu beenden." else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Am Feierabend: Gesamtstunden automatisch auf die Stationen verteilen
            item {
                Card(
                    onClick = onTagAufteilen,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Tag aufteilen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Feierabend-Stunden automatisch auf die heutigen Stationen verteilen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Wochenübersicht (aktuelle Woche, Stunden je Tag)
            item {
                WochenUebersicht(einsaetze = einsaetze, mitarbeiter = me)
            }

            if (zettel.isEmpty()) {
                item {
                    Text(
                        "Noch keine Stundenzettel gespeichert. Über das Formular-Symbol neben " +
                            "einer Station in der Übersicht wird einer angelegt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(zettel.size, key = { zettel[it].id }) { index ->
                val z = zettel[index]
                val hatUnterschrift = viewModel.signatureStore.has(z.id, SignatureStore.ROLLE_STATION)
                val hatStunden = z.stunden.isNotBlank()
                val fertig = hatStunden && hatUnterschrift
                Card(
                    onClick = { onOpen(z.id) },
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Assignment,
                            contentDescription = null,
                            tint = if (fertig) OkGreen else WarnAmber,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Station ${z.station}" +
                                        if (z.auftragsnummer.isNotBlank()) " · ${z.auftragsnummer}" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge(if (fertig) "FERTIG" else "OFFEN", if (fertig) OkGreen else WarnAmber)
                            }
                            Text(
                                "ab ${Dates.isoToGerman(z.zeitraumStart)}" +
                                    if (z.datum.isNotBlank()) " · ${z.datum}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                StatusBadge(
                                    if (hatStunden) "${z.stunden} Std." else "Stunden fehlen",
                                    if (hatStunden) OkGreen else WarnAmber
                                )
                                StatusBadge(
                                    if (hatUnterschrift) "unterschrieben" else "ohne Unterschrift",
                                    if (hatUnterschrift) OkGreen else WarnAmber
                                )
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
        }
    }
}

/** Stunden zwischen einem ISO-Startzeitpunkt und jetzt (aufgerundet auf ganze Stunden). */
private fun elapsedStunden(startIso: String): Long = runCatching {
    val start = java.time.LocalDateTime.parse(startIso)
    java.time.Duration.between(start, java.time.LocalDateTime.now()).toHours()
}.getOrDefault(0L)

/** Übersicht der aktuellen Woche (Mo–So): Arbeitsstunden je Tag aus beendeten Einsätzen. */
@Composable
private fun WochenUebersicht(einsaetze: List<de.excero.tvwartung.data.Einsatz>, mitarbeiter: String) {
    val heute = java.time.LocalDate.now()
    val montag = heute.with(java.time.DayOfWeek.MONDAY)
    val tage = (0..6).map { montag.plusDays(it.toLong()) }
    val stundenProTag = remember(einsaetze, mitarbeiter, heute) {
        val map = HashMap<java.time.LocalDate, Double>()
        einsaetze.filter { it.ende.isNotBlank() && (mitarbeiter.isBlank() || it.mitarbeiter.trim() == mitarbeiter) }
            .forEach { e ->
                runCatching {
                    val s = java.time.LocalDateTime.parse(e.start)
                    val en = java.time.LocalDateTime.parse(e.ende)
                    val tag = s.toLocalDate()
                    if (tag >= montag && tag <= montag.plusDays(6)) {
                        val h = java.time.Duration.between(s, en).toMinutes() / 60.0
                        map[tag] = (map[tag] ?: 0.0) + h
                    }
                }
            }
        map
    }
    val gesamt = stundenProTag.values.sum()
    val maxTag = (stundenProTag.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val wochentage = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diese Woche", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "%.1f Std.".format(gesamt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            tage.forEachIndexed { i, tag ->
                val h = stundenProTag[tag] ?: 0.0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        wochentage[i],
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (tag == heute) FontWeight.Bold else FontWeight.Normal,
                        color = if (tag == heute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(24.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = (h / maxTag).toFloat().coerceIn(0f, 1f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        if (h > 0) "%.1f".format(h) else "–",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
