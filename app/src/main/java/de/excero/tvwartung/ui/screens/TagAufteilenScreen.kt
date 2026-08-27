package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates
import kotlinx.coroutines.launch

/**
 * „Tag aufteilen": Am Feierabend die insgesamt vor Ort verbrachten Stunden
 * angeben – die App verteilt sie automatisch auf die heute bearbeiteten
 * Stationen, gewichtet nach dem Arbeitsaufwand je Zettel (Zimmer + Arbeiten),
 * und legt die Anfahrt auf die zuerst besuchte Station.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagAufteilenScreen(viewModel: AppViewModel, onFertig: () -> Unit, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var stundenText by remember { mutableStateOf("") }
    var anfahrtText by remember { mutableStateOf("") }
    var aufteilung by remember { mutableStateOf<AppViewModel.TagAufteilung?>(null) }
    var hinweis by remember { mutableStateOf<String?>(null) }
    var rechnet by remember { mutableStateOf(false) }

    fun berechne() {
        val std = Dates.stundenWert(stundenText)
        if (std <= 0) { hinweis = "Bitte zuerst die Gesamtstunden eingeben."; aufteilung = null; return }
        rechnet = true
        scope.launch {
            val ergebnis = viewModel.berechneTagAufteilung(std, Dates.stundenWert(anfahrtText))
            rechnet = false
            if (ergebnis.posten.isEmpty()) {
                hinweis = "Heute wurden noch keine Prüfungen erfasst – es gibt nichts zu verteilen."
                aufteilung = null
            } else {
                hinweis = null
                aufteilung = ergebnis
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Tag aufteilen", fontWeight = FontWeight.Bold)
                    Text(
                        "Stunden automatisch auf die Stationen verteilen",
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
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wie lange heute vor Ort?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Gesamte Arbeitszeit (ohne Anfahrt) eingeben. Die App teilt sie nach " +
                            "Arbeitsaufwand auf die heute bearbeiteten Stationen auf; die Anfahrt " +
                            "kommt auf die zuerst besuchte Station.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stundenText,
                            onValueChange = { stundenText = it; aufteilung = null },
                            label = { Text("Stunden gesamt (z. B. 7,5)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = anfahrtText,
                            onValueChange = { anfahrtText = it; aufteilung = null },
                            label = { Text("Anfahrt (Std.)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { berechne() },
                        enabled = !rechnet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (rechnet) "Berechne …" else "Aufteilung berechnen")
                    }
                    if (settings.mitarbeiter.isBlank()) {
                        Text(
                            "Hinweis: In den Einstellungen ist kein Mitarbeitername gesetzt – " +
                                "die Stunden landen auf einer Zeile „Unbenannt“.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            hinweis?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            aufteilung?.let { a ->
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Vorschau (${Dates.isoToGerman(a.datum)})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${Dates.stundenText(a.verteilteArbeit)} Std. Arbeit" +
                                (if (a.anfahrt > 0) " + ${Dates.stundenText(a.anfahrt)} Std. Anfahrt" else "") +
                                " werden zu deiner Zeile addiert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        a.posten.forEach { p ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Station ${p.station}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "+ ${Dates.stundenText(p.gesamt)} Std.",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    buildString {
                                        append("${p.zimmer} Zimmer")
                                        if (p.arbeiten > 0) append(", ${p.arbeiten} Arbeiten")
                                        append(" · ${Dates.stundenText(p.arbeitStunden)} Std. Arbeit")
                                        if (p.anfahrt > 0) append(" + ${Dates.stundenText(p.anfahrt)} Anfahrt")
                                        if (p.bereitsErfasst > 0)
                                            append("  (bisher ${Dates.stundenText(p.bereitsErfasst)} → " +
                                                "${Dates.stundenText(p.bereitsErfasst + p.gesamt)})")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.uebernehmeTagAufteilung(a) { onFertig() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auf die Stundenzettel übernehmen")
                }
                OutlinedButton(onClick = { aufteilung = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verwerfen")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
