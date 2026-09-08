package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates
import java.io.File

/**
 * Wiederverwendbarer Fotobereich (Kamera fern/nah, Galerie, Vorschau, Löschen)
 * für ein Zimmer an einem bestimmten Tag. Wird in Zimmerdetails, Prüfbogen und
 * Prüfbericht eingebunden, sodass Fotos ohne Screenwechsel entstehen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSection(
    viewModel: AppViewModel,
    roomId: String,
    dateFolder: String = Dates.todayFolder(),
    freenetVerlaengert: Boolean = false,
    modifier: Modifier = Modifier
) {
    var refresh by remember(roomId, dateFolder) { mutableIntStateOf(0) }
    val photos = remember(roomId, dateFolder, refresh) {
        viewModel.photoStore.photosFor(roomId, dateFolder)
    }

    var pendingPhoto by remember { mutableStateOf<Pair<File, String>?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        pendingPhoto?.let { (file, _) ->
            if (success) {
                // Wasserzeichen (Station/Zimmer + Zeitstempel) einbrennen, dann aktualisieren
                viewModel.verarbeiteNeuesFoto(roomId, file, dateFolder) { refresh++ }
            } else {
                file.delete()
                refresh++
            }
        }
        pendingPhoto = null
    }

    val pickFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryPhotos(roomId, uris, dateFolder) { refresh++ }
        }
    }

    fun capture(label: String) {
        val file = viewModel.photoStore.newPhotoFile(roomId, label, dateFolder)
        pendingPhoto = file to label
        takePicture.launch(viewModel.photoStore.uriFor(file))
    }
    fun loesche(file: File) {
        viewModel.photoStore.delete(file)
        viewModel.logAction(roomId, "Foto gelöscht")
        viewModel.aktualisiereBerichtPdf(roomId, dateFolder)
        refresh++
    }
    // Zweistufige Bestätigung vor dem Löschen (Foto lässt sich nicht wiederherstellen)
    var loeschKandidat by remember { mutableStateOf<File?>(null) }
    loeschKandidat?.let { datei ->
        AlertDialog(
            onDismissRequest = { loeschKandidat = null },
            title = { Text("Foto löschen?") },
            text = { Text("Dieses Foto wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = { loesche(datei); loeschKandidat = null }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { loeschKandidat = null }) { Text("Abbrechen") }
            }
        )
    }

    // Feste Foto-Felder: je Aufnahmetyp genau ein Bild. Bei verlängertem Freenet
    // zwei Nah-Felder (Stand vor/nach der Verlängerung).
    val felder = buildList {
        add("fern" to "Fern")
        if (freenetVerlaengert) {
            add("nah1" to "Nah (vorher)")
            add("nah2" to "Nah (nachher)")
        } else {
            add("nah" to "Nah")
        }
        add("fernbedienung" to "Fernbedienung")
    }
    // Neuestes Foto zu einem Feld (Label steckt im Dateinamen: …_<label>_<zeit>.jpg)
    fun fotoFuer(label: String): File? =
        photos.filter { it.name.contains("_${label}_") }.maxByOrNull { it.name }
    val feldFotos = felder.mapNotNull { fotoFuer(it.first) }.toSet()
    val weitereFotos = photos.filterNot { it in feldFotos }

    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Fotos (${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // Feste Felder als Kacheln – tippen nimmt genau dieses Bild auf
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                felder.forEach { (label, caption) ->
                    FotoFeld(
                        caption = caption,
                        foto = fotoFuer(label),
                        onCapture = { capture(label) },
                        onDelete = { fotoFuer(label)?.let { loeschKandidat = it } }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    pickFromGallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Weitere Fotos aus Galerie")
            }

            if (weitereFotos.isNotEmpty()) {
                Text(
                    "Weitere Fotos (${weitereFotos.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    weitereFotos.forEach { file ->
                        Box {
                            AsyncImage(
                                model = file,
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { loeschKandidat = file },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Foto löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ein festes Foto-Feld (Kachel): zeigt das aufgenommene Bild oder – solange leer –
 * ein Kamera-Symbol; Antippen nimmt genau dieses Bild auf, ✕ löscht es.
 */
@Composable
private fun FotoFeld(
    caption: String,
    foto: File?,
    onCapture: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(104.dp)
    ) {
        Box(
            Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (foto == null) Modifier.clickable(onClick = onCapture) else Modifier)
        ) {
            if (foto != null) {
                AsyncImage(
                    model = foto,
                    contentDescription = caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Foto löschen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Aufnehmen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (foto != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
