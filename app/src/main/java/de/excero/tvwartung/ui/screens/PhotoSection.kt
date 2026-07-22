package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
        pendingPhoto?.let { (file, label) ->
            if (success) {
                viewModel.logAction(roomId, "Foto aufgenommen ($label)")
                viewModel.aktualisiereBerichtPdf(roomId, dateFolder)
            } else {
                file.delete()
            }
        }
        pendingPhoto = null
        refresh++
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

    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Fotos (${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { capture("fern") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto fern")
                }
                FilledTonalButton(onClick = { capture("nah") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto nah")
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
                Text("Aus Galerie hinzufügen")
            }
            if (photos.isNotEmpty()) {
                // Raster (wrappt in mehrere Reihen) statt seitlichem Scrollen –
                // mehr Fotos auf einen Blick. FlowRow, damit es in scrollbaren
                // Screens (Prüfbogen/Bericht) nicht mit verschachteltem Scrollen kollidiert.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    photos.forEach { file ->
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
                                onClick = {
                                    viewModel.photoStore.delete(file)
                                    viewModel.logAction(roomId, "Foto gelöscht")
                                    viewModel.aktualisiereBerichtPdf(roomId, dateFolder)
                                    refresh++
                                },
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
            } else {
                Text(
                    "Noch keine Fotos – ein Foto von fern und eins von nah aufnehmen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
