package de.excero.tvwartung.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates

private sealed interface Treffer {
    val titel: String
    val untertitel: String
    data class Zimmer(override val titel: String, override val untertitel: String, val id: String) : Treffer
    data class Bericht(override val titel: String, override val untertitel: String, val id: Long, val foto: java.io.File?) : Treffer
    data class Zettel(override val titel: String, override val untertitel: String, val id: Long) : Treffer
    data class Materialposten(override val titel: String, override val untertitel: String) : Treffer
}

/**
 * Globale Suche über Zimmer, Berichte, Stundenzettel und Material. Von der
 * Kopfzeile aus per Lupe erreichbar; zeigt bei leerer Eingabe die letzten Suchen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onRoom: (String) -> Unit,
    onBericht: (Long) -> Unit,
    onStundenzettel: (Long) -> Unit,
    onVerwaltung: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val berichte by viewModel.alleBerichte.collectAsState()
    val zettel by viewModel.alleStundenzettel.collectAsState()
    val material by viewModel.materialien.collectAsState()

    var query by remember { mutableStateOf("") }
    val letzte = remember { viewModel.letzteSuchen() }
    val roomsById = remember(rooms) { rooms.associateBy { it.id } }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun merken() = viewModel.merkeSuche(query)

    val q = query.trim()
    val zimmerTreffer = remember(q, rooms) {
        if (q.isBlank()) emptyList() else rooms.filter {
            it.id.contains(q, true) || it.station.contains(q, true) || it.zimmer.contains(q, true) ||
                it.tvTyp.contains(q, true) || it.seriennummer.contains(q, true) || it.freenetId.contains(q, true)
        }.take(20).map {
            Treffer.Zimmer("${it.station} · Zimmer ${it.zimmer}",
                listOfNotNull(it.tvTyp.ifBlank { null }, it.seriennummer.ifBlank { null }?.let { s -> "SN $s" })
                    .joinToString(" · "), it.id)
        }
    }
    val berichtTreffer = remember(q, berichte, roomsById) {
        if (q.isBlank()) emptyList() else berichte.filter { insp ->
            val station = roomsById[insp.roomId]?.station.orEmpty()
            insp.roomId.contains(q, true) || station.contains(q, true) ||
                insp.mitarbeiter.contains(q, true) || insp.bemerkungen.contains(q, true) ||
                insp.arbeitenListe().any { it.contains(q, true) } ||
                Dates.isoToGerman(insp.datum).contains(q, true)
        }.take(20).map {
            Treffer.Bericht("${it.roomId} · ${Dates.isoToGerman(it.datum)}",
                listOfNotNull(it.mitarbeiter.ifBlank { null }, it.arbeitenListe().firstOrNull())
                    .joinToString(" · "), it.id, viewModel.erstesFotoFuer(it))
        }
    }
    val zettelTreffer = remember(q, zettel) {
        if (q.isBlank()) emptyList() else zettel.filter {
            it.station.contains(q, true) || it.auftragsnummer.contains(q, true) ||
                it.datum.contains(q, true) || it.techniker.contains(q, true)
        }.take(20).map {
            Treffer.Zettel("Station ${it.station}",
                listOfNotNull(it.auftragsnummer.ifBlank { null }, it.datum.ifBlank { null })
                    .joinToString(" · "), it.id)
        }
    }
    val materialTreffer = remember(q, material) {
        if (q.isBlank()) emptyList() else material.filter { it.name.contains(q, true) }.take(20).map {
            Treffer.Materialposten(it.name,
                if (it.bestandAktiv) "Bestand: ${it.bestand}" else "Materialkatalog")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zimmer, Bericht, Stundenzettel, Material …") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { merken(); keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
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

        if (q.isBlank()) {
            if (letzte.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    titel = "Alles durchsuchen",
                    hinweis = "Zimmer, Prüfberichte, Stundenzettel und Material – tippe einen Begriff ein."
                )
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zuletzt gesucht", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        letzte.forEach { begriff ->
                            AssistChip(onClick = { query = begriff }, label = { Text(begriff) })
                        }
                    }
                }
            }
        } else {
            val alle = zimmerTreffer + berichtTreffer + zettelTreffer + materialTreffer
            if (alle.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    titel = "Keine Treffer",
                    hinweis = "Für „$q“ wurde nichts gefunden."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gruppe("Zimmer (${zimmerTreffer.size})", zimmerTreffer)
                    gruppe("Berichte (${berichtTreffer.size})", berichtTreffer)
                    gruppe("Stundenzettel (${zettelTreffer.size})", zettelTreffer)
                    gruppe("Material (${materialTreffer.size})", materialTreffer)

                    items(alle.size, key = { alle[it].hashCode() }) { i ->
                        val t = alle[i]
                        TrefferKarte(t) {
                            merken()
                            when (t) {
                                is Treffer.Zimmer -> onRoom(t.id)
                                is Treffer.Bericht -> onBericht(t.id)
                                is Treffer.Zettel -> onStundenzettel(t.id)
                                is Treffer.Materialposten -> onVerwaltung()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.gruppe(titel: String, treffer: List<Treffer>) {
    if (treffer.isEmpty()) return
    item(key = "header_$titel") {
        Text(
            titel,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun TrefferKarte(treffer: Treffer, onClick: () -> Unit) {
    val icon: ImageVector = when (treffer) {
        is Treffer.Zimmer -> Icons.Outlined.Tv
        is Treffer.Bericht -> Icons.Outlined.PictureAsPdf
        is Treffer.Zettel -> Icons.Outlined.Assignment
        is Treffer.Materialposten -> Icons.Outlined.Inventory2
    }
    Card(onClick = onClick, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val foto = (treffer as? Treffer.Bericht)?.foto
            if (foto != null) {
                AsyncImage(
                    model = foto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(treffer.titel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (treffer.untertitel.isNotBlank()) {
                    Text(
                        treffer.untertitel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
