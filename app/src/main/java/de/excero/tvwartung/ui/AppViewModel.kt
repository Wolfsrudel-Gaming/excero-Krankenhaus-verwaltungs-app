package de.excero.tvwartung.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.excero.tvwartung.App
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.excel.XlsxReader
import de.excero.tvwartung.excel.XlsxWriter
import de.excero.tvwartung.files.ZipExporter
import de.excero.tvwartung.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<App>()
    val repository get() = app.repository
    val photoStore get() = app.photoStore

    val rooms: StateFlow<List<TvRoom>> = repository.rooms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inspectionsToday: StateFlow<List<Inspection>> =
        repository.inspectionsOn(Dates.todayIso())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun room(id: String): Flow<TvRoom?> = repository.room(id)

    fun inspectionsFor(roomId: String): Flow<List<Inspection>> = repository.inspectionsFor(roomId)

    fun updateRoom(room: TvRoom) {
        viewModelScope.launch { repository.updateRoom(room) }
    }

    fun saveInspection(inspection: Inspection, lebenslaufEintrag: String, neuesGueltigBis: String?) {
        viewModelScope.launch {
            repository.saveInspection(inspection, lebenslaufEintrag, neuesGueltigBis)
            _message.value = "Prüfbogen für ${inspection.roomId} gespeichert"
        }
    }

    /** Excel-Export in eine vom Nutzer gewählte Datei (SAF-Uri). */
    fun exportExcel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val rooms = repository.allRooms()
                val inspections = repository.allInspections()
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    XlsxWriter.write(rooms, inspections, out)
                } ?: error("Datei konnte nicht geöffnet werden")
                rooms.size
            }.onSuccess {
                _message.value = "Excel-Export abgeschlossen ($it Zimmer)"
            }.onFailure {
                _message.value = "Excel-Export fehlgeschlagen: ${it.message}"
            }
        }
    }

    /** ZIP aller Fotos (optional nur des heutigen Tages) in eine SAF-Uri schreiben. */
    fun exportZip(uri: Uri, onlyToday: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    ZipExporter.export(
                        root = photoStore.rootDir(),
                        out = out,
                        dateFolder = if (onlyToday) Dates.todayFolder() else null
                    )
                } ?: error("Datei konnte nicht geöffnet werden")
            }.onSuccess {
                _message.value = if (it == 0) "Keine Fotos gefunden" else "ZIP erstellt ($it Fotos)"
            }.onFailure {
                _message.value = "ZIP-Export fehlgeschlagen: ${it.message}"
            }
        }
    }

    /** KKH-Übersicht (.xlsx) importieren; vorhandene Zimmer werden aktualisiert. */
    fun importExcel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val rooms = app.contentResolver.openInputStream(uri)?.use { input ->
                    XlsxReader.readRooms(input)
                } ?: error("Datei konnte nicht gelesen werden")
                require(rooms.isNotEmpty()) { "Keine Zimmerdaten in der Datei gefunden" }
                repository.importRooms(rooms)
                rooms.size
            }.onSuccess {
                _message.value = "Import abgeschlossen ($it Zimmer)"
            }.onFailure {
                _message.value = "Import fehlgeschlagen: ${it.message}"
            }
        }
    }

    suspend fun photoCountToday(): Int = withContext(Dispatchers.IO) {
        photoStore.countToday()
    }
}
