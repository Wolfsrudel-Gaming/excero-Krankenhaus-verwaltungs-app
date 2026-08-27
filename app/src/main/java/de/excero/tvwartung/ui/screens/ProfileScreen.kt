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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

/** Persönliche Auswertung des Mitarbeiters dieses Geräts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val berichte by viewModel.alleBerichte.collectAsState()
    val eintraege by viewModel.alleEintraege.collectAsState()
    val aktivitaet by viewModel.recentActivity.collectAsState()

    val me = settings.mitarbeiter.trim()

    val meineBerichte = remember(berichte, me) {
        if (me.isBlank()) emptyList() else berichte.filter { it.mitarbeiter.trim() == me }
    }
    val meineStunden = remember(eintraege, me) {
        if (me.isBlank()) 0.0
        else eintraege.filter { it.mitarbeiter.trim() == me }
            .sumOf { it.stunden.replace(",", ".").trim().toDoubleOrNull() ?: 0.0 }
    }
    val nioQuote = remember(meineBerichte) {
        var nio = 0; var gesamt = 0
        meineBerichte.forEach { insp ->
            insp.punkte().forEach { (_, ergebnis, _) ->
                if (ergebnis != null) { gesamt++; if (ergebnis == false) nio++ }
            }
        }
        if (gesamt == 0) null else nio * 100.0 / gesamt
    }
    val letzteZimmer = remember(meineBerichte) {
        meineBerichte.sortedByDescending { it.datum + it.id }
            .distinctBy { it.roomId }.take(8)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Mein Profil", fontWeight = FontWeight.Bold)
                    Text(
                        me.ifBlank { "kein Mitarbeiter hinterlegt" },
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

        if (me.isBlank()) {
            EmptyState(
                icon = Icons.Outlined.Person,
                titel = "Kein Mitarbeiter hinterlegt",
                hinweis = "In den Einstellungen den Mitarbeiter-Namen dieses Geräts eintragen – " +
                    "dann erscheinen hier deine persönlichen Zahlen."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfilKpi("Meine Prüfungen", "${meineBerichte.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    ProfilKpi("Meine Stunden", stundenText(meineStunden), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
            }
            item {
                ProfilKpi(
                    titel = "n.i.O.-Quote",
                    wert = nioQuote?.let { "${it.toInt()} %" } ?: "–",
                    farbe = when {
                        nioQuote == null -> MaterialTheme.colorScheme.primary
                        nioQuote < 15 -> OkGreen
                        else -> ErrorRed
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Zuletzt von mir geprüft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (letzteZimmer.isEmpty()) {
                item {
                    Text(
                        "Noch keine eigenen Prüfungen erfasst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(letzteZimmer.size, key = { letzteZimmer[it].id }) { i ->
                val insp = letzteZimmer[i]
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(insp.roomId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(Dates.isoToGerman(insp.datum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (aktivitaet.isNotEmpty()) {
                item {
                    Text("Letzte Aktivität (Team)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(aktivitaet.take(5).size, key = { "akt_${aktivitaet[it].zeitpunkt}_$it" }) { i ->
                    val a = aktivitaet[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            Dates.isoDateTimeToGerman(a.zeitpunkt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(130.dp)
                        )
                        Text("${a.roomId} · ${a.aktion}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun stundenText(std: Double): String =
    if (std == std.toLong().toDouble()) "${std.toLong()} h" else String.format("%.1f h", std)

@Composable
private fun ProfilKpi(titel: String, wert: String, farbe: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(wert, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = farbe)
            Spacer(Modifier.height(2.dp))
            Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
