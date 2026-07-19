package de.excero.tvwartung.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onStundenzettelListe: () -> Unit
) {
    val inspectionsToday by viewModel.inspectionsToday.collectAsState()
    var onlyToday by remember { mutableStateOf(true) }
    var photoCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { photoCount = viewModel.photoCountToday() }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportZip(it, onlyToday) } }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri -> uri?.let { viewModel.exportExcel(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importExcel(it) } }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { viewModel.exportDayPdf(it) } }

    // Backup: erst Passwort abfragen, dann Datei anlegen bzw. öffnen
    var backupPasswort by remember { mutableStateOf<String?>(null) }
    var zeigeBackupDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = backupPasswort
        if (uri != null && pw != null) viewModel.createBackup(uri, pw)
        backupPasswort = null
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) restoreUri = uri }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Export & Import", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Fotos als ZIP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Packt die Fotoordner in der HiDrive-Struktur " +
                            "Station_Zimmer/JJJJMMTT. Zu jedem geprüften Zimmer wird " +
                            "automatisch das Prüfbericht-PDF in den Ordner gelegt. " +
                            "Heute aufgenommen: $photoCount Fotos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = onlyToday, onCheckedChange = { onlyToday = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (onlyToday) "Nur heutiger Tag (${Dates.todayFolder()})" else "Alle Tage",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!onlyToday) {
                        Text(
                            "Kompletter Export: enthält zusätzlich den Ordner „Stundenzettel/“ " +
                                "mit allen Leistungsnachweisen (Stundenzettel_Station_Zeitraum.pdf).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = {
                            val name = if (onlyToday) "Fotos_Zimmer_${Dates.todayFolder()}.zip"
                            else "Fotos_Zimmer_komplett.zip"
                            zipLauncher.launch(name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ZIP erstellen")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Stundenzettel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Alle gespeicherten Stundenzettel ansehen und bearbeiten – z. B. " +
                            "unterwegs unterschreiben lassen und die Stunden später eintragen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onStundenzettelListe,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gespeicherte Stundenzettel")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Prüfberichte als PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Alle heute ausgefüllten Prüfbögen (${inspectionsToday.size}) als ein " +
                            "PDF mit den zugehörigen Fotos. Einzelne Berichte lassen sich in den " +
                            "Zimmerdetails unter „Prüfberichte“ ansehen und exportieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { pdfLauncher.launch("Pruefberichte_${Dates.todayFolder()}.pdf") },
                        enabled = inspectionsToday.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tages-PDF erstellen")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Excel (KKH-Übersicht)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Exportiert alle Zimmer im Format der KKH-Übersicht plus ein Blatt " +
                            "mit sämtlichen Prüfprotokollen. Heute ausgefüllt: ${inspectionsToday.size} Prüfbögen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { excelLauncher.launch("KKH_Übersicht_${Dates.todayFolder()}.xlsx") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Excel exportieren")
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("KKH-Übersicht importieren (.xlsx)")
                    }
                    Text(
                        "Beim Import werden die Stammdaten aus Tabelle1 übernommen; " +
                            "bestehende Zimmer werden aktualisiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Backup & Gerätewechsel
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Backup & Gerätewechsel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Vollständiges, verschlüsseltes Backup (Datenbank, Fotos, " +
                            "Unterschriften, Einstellungen) – z. B. zum Weiterarbeiten " +
                            "auf dem Tablet: dort die gleiche App installieren und das " +
                            "Backup einspielen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { zeigeBackupDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Backup erstellen (verschlüsselt)")
                    }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Backup einspielen (.kkhbak)")
                    }
                }
            }
        }
    }

    if (zeigeBackupDialog) {
        PasswortDialog(
            titel = "Backup verschlüsseln",
            text = "Passwort für die Backup-Datei festlegen. Ohne dieses Passwort " +
                "lässt sich das Backup nicht wieder einspielen!",
            mitWiederholung = true,
            bestaetigenText = "Backup erstellen",
            onConfirm = { pw ->
                zeigeBackupDialog = false
                backupPasswort = pw
                backupLauncher.launch(
                    "KKH_Backup_${Dates.todayFolder()}_${zeitStempel()}.kkhbak"
                )
            },
            onDismiss = { zeigeBackupDialog = false }
        )
    }

    restoreUri?.let { uri ->
        PasswortDialog(
            titel = "Backup einspielen",
            text = "ACHTUNG: Alle aktuellen Daten auf diesem Gerät (Zimmer, Prüfberichte, " +
                "Fotos, Stundenzettel) werden durch das Backup ERSETZT. Die App startet " +
                "danach automatisch neu.",
            mitWiederholung = false,
            bestaetigenText = "Einspielen & ersetzen",
            onConfirm = { pw ->
                restoreUri = null
                viewModel.restoreBackup(uri, pw)
            },
            onDismiss = { restoreUri = null }
        )
    }
}

private fun zeitStempel(): String =
    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmm"))

/** Passwortabfrage für Backup erstellen/einspielen. */
@Composable
private fun PasswortDialog(
    titel: String,
    text: String,
    mitWiederholung: Boolean,
    bestaetigenText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passwort by remember { mutableStateOf("") }
    var wiederholung by remember { mutableStateOf("") }
    val gueltig = passwort.length >= 6 && (!mitWiederholung || passwort == wiederholung)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = passwort,
                    onValueChange = { passwort = it },
                    label = { Text("Passwort (mind. 6 Zeichen)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mitWiederholung) {
                    OutlinedTextField(
                        value = wiederholung,
                        onValueChange = { wiederholung = it },
                        label = { Text("Passwort wiederholen") },
                        singleLine = true,
                        isError = wiederholung.isNotEmpty() && wiederholung != passwort,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passwort) }, enabled = gueltig) {
                Text(bestaetigenText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
