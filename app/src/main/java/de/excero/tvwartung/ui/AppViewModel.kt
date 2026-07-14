package de.excero.tvwartung.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.excero.tvwartung.App
import de.excero.tvwartung.data.ActivityLog
import de.excero.tvwartung.data.AppSettings
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.excel.XlsxReader
import de.excero.tvwartung.excel.XlsxWriter
import de.excero.tvwartung.files.ZipExporter
import de.excero.tvwartung.pdf.PruefberichtPdf
import de.excero.tvwartung.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<App>()
    val repository get() = app.repository
    val photoStore get() = app.photoStore

    val settings: StateFlow<AppSettings> = app.settingsStore.settings

    fun updateSettings(settings: AppSettings) = app.settingsStore.update(settings)

    val rooms: StateFlow<List<TvRoom>> = repository.rooms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Prüfbögen innerhalb des eingestellten Zeitraums ("eine Anfahrt"). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val inspectionsInPeriod: StateFlow<List<Inspection>> = settings
        .flatMapLatest { repository.inspectionsSince(it.zeitraumStartIso()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inspectionsToday: StateFlow<List<Inspection>> =
        repository.inspectionsOn(Dates.todayIso())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentActivity: StateFlow<List<ActivityLog>> = repository.recentActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Zimmer, die für die aktuelle Anfahrt als "kein Zutritt" gemeldet sind.
     * Ältere Vermerke (vor Beginn des Prüfzeitraums) laufen automatisch ab.
     */
    val gesperrteZimmer: StateFlow<Set<String>> =
        combine(repository.sperren, settings) { sperren, s ->
            val start = s.zeitraumStartIso()
            sperren.filter { it.gesperrtAm >= start }.map { it.roomId }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setKeinZutritt(roomId: String, gesperrt: Boolean) {
        viewModelScope.launch { repository.setKeinZutritt(roomId, gesperrt) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun room(id: String): Flow<TvRoom?> = repository.room(id)

    fun inspectionsFor(roomId: String): Flow<List<Inspection>> = repository.inspectionsFor(roomId)

    fun activityFor(roomId: String): Flow<List<ActivityLog>> = repository.activityFor(roomId)

    /** Alle bekannten TV-Marken (für die Schnellauswahl). */
    fun bekannteTvTypen(): List<String> =
        rooms.value.map { it.tvTyp.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    /** Zimmer (außer [exceptRoomId]), bei denen dieselbe Freenet-ID hinterlegt ist. */
    fun freenetIdDuplikate(freenetId: String, exceptRoomId: String): List<String> {
        val id = freenetId.trim()
        if (id.isEmpty()) return emptyList()
        return rooms.value.filter { it.id != exceptRoomId && it.freenetId.trim() == id }.map { it.id }
    }

    /** Zimmer (außer [exceptRoomId]), bei denen dieselbe TV-Seriennummer hinterlegt ist. */
    fun seriennummerDuplikate(seriennummer: String, exceptRoomId: String): List<String> {
        val sn = seriennummer.trim()
        if (sn.isEmpty()) return emptyList()
        return rooms.value.filter { it.id != exceptRoomId && it.seriennummer.trim() == sn }.map { it.id }
    }

    fun updateRoom(room: TvRoom, logAktion: String = "Stammdaten geändert") {
        viewModelScope.launch { repository.updateRoom(room, logAktion) }
    }

    fun logAction(roomId: String, aktion: String) {
        viewModelScope.launch { repository.logAction(roomId, aktion) }
    }

    fun saveInspection(
        inspection: Inspection,
        lebenslaufEintrag: String,
        neuesGueltigBis: String? = null,
        neueSeriennummer: String? = null,
        neueFreenetId: String? = null,
        neuerTvTyp: String? = null
    ) {
        viewModelScope.launch {
            repository.saveInspection(
                inspection, lebenslaufEintrag, neuesGueltigBis,
                neueSeriennummer, neueFreenetId, neuerTvTyp
            )
            _message.value = "Prüfbogen für ${inspection.roomId} gespeichert"
        }
    }

    /** Bilder aus der Galerie in den heutigen Zimmerordner übernehmen. */
    fun importGalleryPhotos(roomId: String, uris: List<Uri>, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = uris.count { photoStore.importFromGallery(roomId, it) != null }
            if (imported > 0) {
                repository.logAction(roomId, "$imported Foto(s) aus Galerie hinzugefügt")
            }
            _message.value = when {
                imported == 0 && uris.isNotEmpty() -> "Bilder konnten nicht übernommen werden"
                else -> "$imported Bild(er) übernommen"
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun inspection(id: Long): Flow<Inspection?> = repository.inspection(id)

    /** Fotos, die zum Prüfdatum eines Berichts gehören. */
    fun photosForInspection(inspection: Inspection) =
        photoStore.photosFor(inspection.roomId, Dates.isoToFolder(inspection.datum))

    private suspend fun buildReport(inspection: Inspection): PruefberichtPdf.Report? {
        val room = repository.getRoom(inspection.roomId) ?: return null
        return PruefberichtPdf.Report(
            room = room,
            inspection = inspection,
            photos = photoStore.photosFor(inspection.roomId, Dates.isoToFolder(inspection.datum))
        )
    }

    /** Einzelnen Prüfbericht als PDF exportieren. */
    fun exportInspectionPdf(uri: Uri, inspectionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val inspection = repository.getInspection(inspectionId)
                    ?: error("Prüfbericht nicht gefunden")
                val report = buildReport(inspection) ?: error("Zimmer nicht gefunden")
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    PruefberichtPdf.write(listOf(report), out)
                } ?: error("Datei konnte nicht geöffnet werden")
            }.onSuccess {
                _message.value = "PDF exportiert"
            }.onFailure {
                _message.value = "PDF-Export fehlgeschlagen: ${it.message}"
            }
        }
    }

    /** Alle Prüfberichte eines Tages als ein gemeinsames PDF exportieren. */
    fun exportDayPdf(uri: Uri, isoDate: String = Dates.todayIso()) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val inspections = repository.inspectionsForDate(isoDate)
                require(inspections.isNotEmpty()) { "Keine Prüfberichte an diesem Tag" }
                val reports = inspections.mapNotNull { buildReport(it) }
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    PruefberichtPdf.write(reports, out)
                } ?: error("Datei konnte nicht geöffnet werden")
                reports.size
            }.onSuccess {
                _message.value = "PDF mit $it Prüfbericht(en) exportiert"
            }.onFailure {
                _message.value = "PDF-Export fehlgeschlagen: ${it.message}"
            }
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
                // Zu jedem geprüften Zimmer das Prüfbericht-PDF in seinen Bilderordner legen,
                // damit es mit in die ZIP (und später in den HiDrive) wandert.
                val pdfs = schreibePdfsInZimmerordner(onlyToday)
                val files = app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    ZipExporter.export(
                        root = photoStore.rootDir(),
                        out = out,
                        dateFolder = if (onlyToday) Dates.todayFolder() else null
                    )
                } ?: error("Datei konnte nicht geöffnet werden")
                files to pdfs
            }.onSuccess { (files, pdfs) ->
                _message.value = when {
                    files == 0 -> "Keine Fotos oder Berichte gefunden"
                    else -> "ZIP erstellt ($files Dateien, davon $pdfs Prüfbericht-PDF)"
                }
            }.onFailure {
                _message.value = "ZIP-Export fehlgeschlagen: ${it.message}"
            }
        }
    }

    /**
     * Erzeugt für jedes geprüfte Zimmer (im gewählten Zeitraum) das Prüfbericht-PDF
     * und legt es in dessen Tagesordner Fotos_Zimmer/<Zimmer>/<JJJJMMTT>/ ab.
     * @return Anzahl geschriebener PDFs.
     */
    private suspend fun schreibePdfsInZimmerordner(onlyToday: Boolean): Int {
        val today = Dates.todayFolder()
        // Pro Zimmer + Tag ein PDF (fasst mehrere Prüfbögen desselben Tages zusammen)
        val gruppen = repository.allInspections()
            .groupBy { it.roomId to Dates.isoToFolder(it.datum) }
        var count = 0
        for ((key, inspektionen) in gruppen) {
            val (roomId, dateFolder) = key
            if (dateFolder.isBlank()) continue
            if (onlyToday && dateFolder != today) continue
            val room = repository.getRoom(roomId) ?: continue
            val reports = inspektionen.map {
                PruefberichtPdf.Report(room, it, photoStore.photosFor(roomId, dateFolder))
            }
            runCatching {
                photoStore.pdfFileFor(roomId, dateFolder).outputStream().use { out ->
                    PruefberichtPdf.write(reports, out)
                }
                count++
            }
        }
        return count
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
