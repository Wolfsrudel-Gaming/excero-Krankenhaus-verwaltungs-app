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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import de.excero.tvwartung.sync.KiAnalyse
import de.excero.tvwartung.sync.KiEntscheidung
import de.excero.tvwartung.sync.KiFelder
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber

private data class StatusOption(val wert: String?, val label: String)

private val STATUS_OPTIONEN = listOf(
    StatusOption("abweichung", "Abweichungen"),
    StatusOption(null, "Alle"),
    StatusOption("uebereinstimmung", "Bestätigt"),
    StatusOption("unlesbar", "Unlesbar"),
    StatusOption("wartet", "Wartend"),
    StatusOption("fehler", "Fehler")
)

private fun statusFarbe(status: String) = when (status) {
    "uebereinstimmung" -> OkGreen
    "abweichung" -> ErrorRed
    "unlesbar", "wartet" -> WarnAmber
    "fehler" -> ErrorRed
    else -> WarnAmber
}

private fun statusLabel(status: String) = when (status) {
    "uebereinstimmung" -> "Stimmt überein"
    "abweichung" -> "Abweichung"
    "unlesbar" -> "Unlesbar"
    "wartet" -> "Wartet"
    "laeuft" -> "Läuft…"
    "fehler" -> "Fehler"
    else -> status
}

/**
 * KI-Prüfung in der App: gleiche Auswertung + Trainings-Entscheidung wie im
 * Web-Panel – jede Bestätigung/Korrektur direkt vor Ort auf der Station.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiPruefungScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var statusFilter by remember { mutableStateOf<String?>("abweichung") }
    var analysen by remember { mutableStateOf<List<KiAnalyse>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var ausgewaehlt by remember { mutableStateOf<KiAnalyse?>(null) }

    LaunchedEffect(statusFilter, refresh) {
        laedt = true
        analysen = viewModel.kiAnalysen(statusFilter)
        laedt = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("KI-Prüfung", fontWeight = FontWeight.Bold)
                    Text(
                        "${analysen.size} Analysen · jede Entscheidung trainiert die KI mit",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STATUS_OPTIONEN.forEach { option ->
                FilterChip(
                    selected = statusFilter == option.wert,
                    onClick = { statusFilter = option.wert },
                    label = { Text(option.label) }
                )
            }
        }

        if (laedt) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (analysen.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Keine Analysen in dieser Ansicht.\nEntweder ist der KI-Service " +
                        "gerade nicht erreichbar, oder es liegt (noch) nichts zur Prüfung an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(analysen.size, key = { analysen[it].id }) { index ->
                    val a = analysen[index]
                    Card(
                        onClick = { ausgewaehlt = a },
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
                                    a.roomId.ifBlank { a.pfad.substringBefore('/') },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (a.felder.isNotEmpty()) {
                                    Text(
                                        a.erkanntKurz(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(statusLabel(a.status), statusFarbe(a.status))
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    ausgewaehlt?.let { analyse ->
        KiDetailDialog(
            viewModel = viewModel,
            analyse = analyse,
            onDismiss = { ausgewaehlt = null },
            onGespeichert = { ausgewaehlt = null; refresh++ }
        )
    }
}

private enum class Wahl { KEINE, KI, STAMM, MANUELL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KiDetailDialog(
    viewModel: AppViewModel,
    analyse: KiAnalyse,
    onDismiss: () -> Unit,
    onGespeichert: () -> Unit
) {
    var fotoDatei by remember(analyse.pfad) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(analyse.pfad) { fotoDatei = viewModel.kiFotoDatei(analyse.pfad) }

    val relevanteFelder = remember(analyse) {
        KiFelder.ALLE.filter { analyse.felder.containsKey(it) || analyse.abgleich.containsKey(it) }
    }
    val wahlen = remember(analyse) { relevanteFelder.associateWith { mutableStateOf(Wahl.KEINE) } }
    val manuelleTexte = remember(analyse) { relevanteFelder.associateWith { mutableStateOf("") } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(analyse.roomId.ifBlank { "KI-Analyse" }, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen")
                        }
                    }
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (fotoDatei != null) {
                        AsyncImage(
                            model = fotoDatei,
                            contentDescription = "Foto",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }

                    Text(
                        "Bildtyp: ${analyse.bildtyp.ifBlank { "–" }} · Modell: ${analyse.modellVersion.ifBlank { "–" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (analyse.fehler.isNotBlank()) {
                        DuplicateWarning(analyse.fehler)
                    }

                    if (relevanteFelder.isEmpty()) {
                        Text(
                            "Keine Felder erkannt. Bei Übersichtsfotos ist das normal.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    relevanteFelder.forEach { feld ->
                        val erkannt = analyse.felder[feld]
                        val abgleich = analyse.abgleich[feld]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    KiFelder.NAME[feld] ?: feld,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (erkannt != null) {
                                    Row {
                                        Text("KI erkannt: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        Text(
                                            erkannt.wert + (erkannt.konfidenz?.let { " (${(it * 100).toInt()}%)" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (abgleich != null) {
                                    Row {
                                        Text("Stammdaten: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        Text(abgleich.stammdaten, style = MaterialTheme.typography.bodySmall)
                                    }
                                    when (abgleich.passt) {
                                        true -> StatusBadge("passt", OkGreen)
                                        false -> StatusBadge("Abweichung", ErrorRed)
                                        null -> {}
                                    }
                                }
                                var wahl by wahlen.getValue(feld)
                                Column {
                                    if (erkannt != null) {
                                        RadioZeile(
                                            label = "KI hat recht" + if (abgleich?.passt == false) " (Stammdaten korrigieren)" else "",
                                            selected = wahl == Wahl.KI,
                                            onClick = { wahl = Wahl.KI }
                                        )
                                    }
                                    if (abgleich != null) {
                                        RadioZeile(
                                            label = "Stammdaten stimmen",
                                            selected = wahl == Wahl.STAMM,
                                            onClick = { wahl = Wahl.STAMM }
                                        )
                                    }
                                    RadioZeile(
                                        label = "Manuell eingeben",
                                        selected = wahl == Wahl.MANUELL,
                                        onClick = { wahl = Wahl.MANUELL }
                                    )
                                    if (wahl == Wahl.MANUELL) {
                                        var text by manuelleTexte.getValue(feld)
                                        OutlinedTextField(
                                            value = text,
                                            onValueChange = { text = it },
                                            label = { Text("Richtiger Wert") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Jede Entscheidung wird als Trainingsbeispiel gespeichert – die KI wird dadurch besser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.kiNeuAnalysieren(analyse.id) { onGespeichert() } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Neu analysieren") }
                        Button(
                            onClick = {
                                val entscheidungen = mutableMapOf<String, KiEntscheidung>()
                                relevanteFelder.forEach { feld ->
                                    val wahl = wahlen.getValue(feld).value
                                    val erkannt = analyse.felder[feld]?.wert
                                    val stamm = analyse.abgleich[feld]?.stammdaten
                                    when (wahl) {
                                        Wahl.KI -> if (!erkannt.isNullOrBlank()) {
                                            entscheidungen[feld] = KiEntscheidung(erkannt, stammdatenUebernehmen = true)
                                        }
                                        Wahl.STAMM -> if (!stamm.isNullOrBlank()) {
                                            entscheidungen[feld] = KiEntscheidung(stamm, stammdatenUebernehmen = false)
                                        }
                                        Wahl.MANUELL -> {
                                            val text = manuelleTexte.getValue(feld).value.trim()
                                            if (text.isNotBlank()) {
                                                entscheidungen[feld] = KiEntscheidung(text, stammdatenUebernehmen = true)
                                            }
                                        }
                                        Wahl.KEINE -> {}
                                    }
                                }
                                if (entscheidungen.isEmpty()) {
                                    return@Button
                                }
                                viewModel.kiBestaetigen(analyse.id, entscheidungen) { onGespeichert() }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Speichern") }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Schließen") }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RadioZeile(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
