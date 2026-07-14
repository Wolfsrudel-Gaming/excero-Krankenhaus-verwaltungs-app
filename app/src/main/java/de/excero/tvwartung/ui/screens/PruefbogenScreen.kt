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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.util.Dates

/** Zustand eines Prüfpunkts: null = offen, true = i.O., false = n.i.O. */
private class PruefpunktState(
    val titel: String,
    val hinweis: String? = null,
    val kurzbefundNiO: String
) {
    var ergebnis by mutableStateOf<Boolean?>(null)
    var bemerkung by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruefbogenScreen(
    viewModel: AppViewModel,
    roomId: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val room by viewModel.room(roomId).collectAsState(initial = null)
    val current = room ?: return

    val punkte = remember(current.id) {
        listOf(
            PruefpunktState("Empfang vorhanden?", kurzbefundNiO = "kein Empfang"),
            PruefpunktState(
                "Seriennummer TV stimmt?",
                hinweis = "Hinterlegt: ${current.seriennummer.ifBlank { "–" }}",
                kurzbefundNiO = "Seriennummer abweichend"
            ),
            PruefpunktState(
                "Freenet TV-ID stimmt?",
                hinweis = "Hinterlegt: ${current.freenetId.ifBlank { "–" }}",
                kurzbefundNiO = "Freenet-ID abweichend"
            ),
            PruefpunktState("DVD-Test", kurzbefundNiO = "DVD defekt"),
            PruefpunktState("Fernbedienung", kurzbefundNiO = "FB defekt"),
            PruefpunktState("Halterung (fest?)", kurzbefundNiO = "Halterung locker"),
            PruefpunktState(
                "Gültigkeit Freenet > 3 Monate?",
                hinweis = "Gültig bis: ${Dates.isoToGerman(current.gueltigBis).ifBlank { "–" }}",
                kurzbefundNiO = ""
            ),
            PruefpunktState("Freenet verlängert", kurzbefundNiO = "")
        )
    }
    var tvTyp by remember(current.id) { mutableStateOf(current.tvTyp) }
    var neuesGueltigBis by remember(current.id) { mutableStateOf("") }
    var gueltigBisKorrektur by remember(current.id) { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }
    var korrekturError by remember { mutableStateOf(false) }
    var bemerkungen by remember(current.id) { mutableStateOf("") }
    var serienUebernehmen by remember(current.id) { mutableStateOf(false) }
    var freenetIdUebernehmen by remember(current.id) { mutableStateOf(false) }
    var eintragManuell by remember(current.id) { mutableStateOf<String?>(null) }

    val freenetVerlaengert = punkte[7].ergebnis == true
    val tvTypGeaendert = tvTyp.trim() != current.tvTyp.trim() && tvTyp.isNotBlank()

    // Duplikat-Hinweise für zu übernehmende Werte
    val serienDups = if (serienUebernehmen && punkte[1].bemerkung.isNotBlank())
        viewModel.seriennummerDuplikate(punkte[1].bemerkung, current.id) else emptyList()
    val freenetDups = if (freenetIdUebernehmen && punkte[2].bemerkung.isNotBlank())
        viewModel.freenetIdDuplikate(punkte[2].bemerkung, current.id) else emptyList()

    // Automatisch vorgeschlagener Lebenslauf-Eintrag aus den Prüfergebnissen
    val vorschlag = buildString {
        append("TV überprüft")
        if (freenetVerlaengert) append(", Freenet verlängert")
        punkte.forEachIndexed { index, p ->
            if (p.ergebnis == false && p.kurzbefundNiO.isNotBlank()) {
                val text = when {
                    index == 1 && serienUebernehmen -> "Seriennummer angepasst"
                    index == 2 && freenetIdUebernehmen -> "Freenet-ID angepasst"
                    else -> p.kurzbefundNiO
                }
                append(", $text")
            }
        }
        if (tvTypGeaendert) append(", TV-Typ angepasst")
        if (gueltigBisKorrektur.isNotBlank() && !freenetVerlaengert) append(", Gültigkeitsdatum korrigiert")
        if (bemerkungen.isNotBlank()) append("; $bemerkungen")
    }
    val eintrag = eintragManuell ?: vorschlag

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Prüfbogen", fontWeight = FontWeight.Bold)
                    Text(
                        "Zimmer ${current.zimmer} (${current.station}) · ${Dates.todayGerman()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Gerätedaten: TV-Marke direkt im Prüfbogen anpassbar
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gerät", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TvTypAuswahl(
                        value = tvTyp,
                        onValueChange = { tvTyp = it },
                        bekannteTypen = viewModel.bekannteTvTypen()
                    )
                    if (tvTypGeaendert) {
                        Text(
                            "TV-Typ wird beim Speichern von „${current.tvTyp.ifBlank { "–" }}“ auf „${tvTyp.trim()}“ geändert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            punkte.forEachIndexed { index, punkt ->
                PruefpunktCard(punkt) {
                    when (index) {
                        1 -> if (punkt.ergebnis == false && punkt.bemerkung.isNotBlank()) {
                            UebernehmenCheckbox(
                                text = "Als neue Seriennummer übernehmen",
                                checked = serienUebernehmen,
                                onCheckedChange = { serienUebernehmen = it }
                            )
                            if (serienDups.isNotEmpty()) {
                                DuplicateWarning(
                                    "Achtung: Diese Seriennummer ist bereits hinterlegt bei ${serienDups.joinToString()} – bitte genauer hinschauen."
                                )
                            }
                        }
                        2 -> if (punkt.ergebnis == false && punkt.bemerkung.isNotBlank()) {
                            UebernehmenCheckbox(
                                text = "Als neue Freenet-ID übernehmen",
                                checked = freenetIdUebernehmen,
                                onCheckedChange = { freenetIdUebernehmen = it }
                            )
                            if (freenetDups.isNotEmpty()) {
                                DuplicateWarning(
                                    "Achtung: Diese Freenet-ID ist bereits registriert bei ${freenetDups.joinToString()} – bitte genauer hinschauen."
                                )
                            }
                        }
                        6 -> if (punkt.ergebnis == false) {
                            OutlinedTextField(
                                value = gueltigBisKorrektur,
                                onValueChange = { gueltigBisKorrektur = it; korrekturError = false },
                                label = { Text("Gültig bis korrigieren (TT.MM.JJJJ, optional)") },
                                singleLine = true,
                                isError = korrekturError,
                                supportingText = if (korrekturError) {
                                    { Text("Datum bitte als TT.MM.JJJJ eingeben") }
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        7 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    FreenetLinks.open(context, FreenetLinks.VERLAENGERN)
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Freenet-Shop")
                                }
                                TextButton(onClick = {
                                    FreenetLinks.open(context, FreenetLinks.AKTIVIEREN)
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Aktivierung")
                                }
                            }
                            if (freenetVerlaengert) {
                                OutlinedTextField(
                                    value = neuesGueltigBis,
                                    onValueChange = { neuesGueltigBis = it; dateError = false },
                                    label = { Text("Neues Gültigkeitsdatum (TT.MM.JJJJ)") },
                                    singleLine = true,
                                    isError = dateError,
                                    supportingText = if (dateError) {
                                        { Text("Datum bitte als TT.MM.JJJJ eingeben") }
                                    } else null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bemerkungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = bemerkungen,
                        onValueChange = { bemerkungen = it },
                        placeholder = { Text("Freitext, z. B. besondere Vorkommnisse") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Lebenslauf-Eintrag (${Dates.todayGerman()})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Wird automatisch aus den Prüfergebnissen erzeugt und kann angepasst werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = eintrag,
                        onValueChange = { eintragManuell = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            Button(
                onClick = {
                    // Neues Gültigkeitsdatum: Verlängerung hat Vorrang vor Korrektur
                    val isoNeu = if (freenetVerlaengert && neuesGueltigBis.isNotBlank()) {
                        val iso = Dates.germanToIso(neuesGueltigBis)
                        if (iso == null) {
                            dateError = true
                            return@Button
                        }
                        iso
                    } else if (gueltigBisKorrektur.isNotBlank()) {
                        val iso = Dates.germanToIso(gueltigBisKorrektur)
                        if (iso == null) {
                            korrekturError = true
                            return@Button
                        }
                        iso
                    } else null

                    val inspection = Inspection(
                        roomId = current.id,
                        datum = Dates.todayIso(),
                        empfangVorhanden = punkte[0].ergebnis,
                        seriennummerStimmt = punkte[1].ergebnis,
                        freenetIdStimmt = punkte[2].ergebnis,
                        dvdTest = punkte[3].ergebnis,
                        fernbedienung = punkte[4].ergebnis,
                        halterungFest = punkte[5].ergebnis,
                        gueltigkeitAusreichend = punkte[6].ergebnis,
                        freenetVerlaengert = punkte[7].ergebnis,
                        bemerkungEmpfang = punkte[0].bemerkung.trim(),
                        bemerkungSeriennummer = punkte[1].bemerkung.trim(),
                        bemerkungFreenetId = punkte[2].bemerkung.trim(),
                        bemerkungDvd = punkte[3].bemerkung.trim(),
                        bemerkungFernbedienung = punkte[4].bemerkung.trim(),
                        bemerkungHalterung = punkte[5].bemerkung.trim(),
                        bemerkungen = bemerkungen.trim()
                    )

                    viewModel.saveInspection(
                        inspection = inspection,
                        lebenslaufEintrag = "${Dates.todayGerman()}: ${eintrag.trim()}",
                        neuesGueltigBis = isoNeu,
                        neueSeriennummer = if (serienUebernehmen) punkte[1].bemerkung.trim() else null,
                        neueFreenetId = if (freenetIdUebernehmen) punkte[2].bemerkung.trim() else null,
                        neuerTvTyp = if (tvTypGeaendert) tvTyp.trim() else null
                    )
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Prüfbogen speichern", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UebernehmenCheckbox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PruefpunktCard(punkt: PruefpunktState, extraContent: @Composable () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(punkt.titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            punkt.hinweis?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = punkt.ergebnis == true,
                    onClick = { punkt.ergebnis = if (punkt.ergebnis == true) null else true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = OkGreen,
                        activeContentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) { Text("i.O.") }
                SegmentedButton(
                    selected = punkt.ergebnis == false,
                    onClick = { punkt.ergebnis = if (punkt.ergebnis == false) null else false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = ErrorRed,
                        activeContentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) { Text("n.i.O.") }
            }
            if (punkt.ergebnis == false) {
                OutlinedTextField(
                    value = punkt.bemerkung,
                    onValueChange = { punkt.bemerkung = it },
                    label = { Text("Bemerkung / tatsächlicher Wert") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            extraContent()
        }
    }
}
