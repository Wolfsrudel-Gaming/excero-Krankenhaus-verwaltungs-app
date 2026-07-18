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
import de.excero.tvwartung.data.Arbeiten
import de.excero.tvwartung.data.CustomPruefpunkt
import de.excero.tvwartung.data.Material
import de.excero.tvwartung.data.StundenzettelEntity
import de.excero.tvwartung.pdf.PruefberichtPdf
import de.excero.tvwartung.pdf.StundenzettelPdf
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

    val materialien: StateFlow<List<Material>> = repository.materialien
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aktiveMaterialien: StateFlow<List<Material>> = repository.aktiveMaterialien
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPruefpunkte: StateFlow<List<CustomPruefpunkt>> = repository.customPruefpunkte
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aktiveCustomPruefpunkte: StateFlow<List<CustomPruefpunkt>> = repository.aktiveCustomPruefpunkte
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Firmenlogo für die PDF-Köpfe (aus den Assets, einmalig geladen). */
    val logo: android.graphics.Bitmap? by lazy {
        runCatching {
            app.assets.open("logo.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun addMaterial(name: String, bestandAktiv: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addMaterial(name, bestandAktiv) }
    }

    fun updateMaterial(material: Material) {
        viewModelScope.launch { repository.updateMaterial(material) }
    }

    fun addPruefpunkt(titel: String) {
        if (titel.isBlank()) return
        viewModelScope.launch { repository.addPruefpunkt(titel) }
    }

    fun updatePruefpunkt(punkt: CustomPruefpunkt) {
        viewModelScope.launch { repository.updatePruefpunkt(punkt) }
    }

    /** Neues Zimmer (ggf. mit neuer Station) anlegen; onDone erhält die Zimmer-ID. */
    fun createRoom(room: de.excero.tvwartung.data.TvRoom, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.createRoom(room) }
                .onSuccess {
                    _message.value = "Zimmer ${room.id} angelegt"
                    onDone(room.id)
                }
                .onFailure { _message.value = "Anlegen fehlgeschlagen: ${it.message}" }
        }
    }

    fun setInaktiv(roomId: String, inaktiv: Boolean) {
        viewModelScope.launch { repository.setInaktiv(roomId, inaktiv) }
    }

    /**
     * Zimmer, die für die aktuelle Anfahrt als "kein Zutritt" gemeldet sind.
     * Ältere Vermerke (vor Beginn des Prüfzeitraums) laufen automatisch ab.
     */
    val gesperrteZimmer: StateFlow<Set<String>> =
        combine(repository.sperren, settings) { sperren, s ->
            val start = s.zeitraumStartIso()
            sperren.filter { it.gesperrtAm >= start }.map { it.roomId }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setKeinZutritt(roomId: String, gesperrt: Boolean, grund: String = "") {
        viewModelScope.launch { repository.setKeinZutritt(roomId, gesperrt, grund) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // ----- Server-Synchronisation -----

    @Volatile
    private var syncLaeuft = false

    /** Manuell oder automatisch mit dem Server abgleichen. */
    fun syncNow(leise: Boolean = false) {
        val s = settings.value
        if (s.serverUrl.isBlank() || s.apiKey.isBlank()) {
            if (!leise) _message.value = "Bitte Server-URL und API-Schlüssel in den Einstellungen hinterlegen"
            return
        }
        if (syncLaeuft) return
        syncLaeuft = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                de.excero.tvwartung.sync.SyncManager(repository, photoStore, signatureStore, s.serverUrl, s.apiKey).sync()
            }.onSuccess {
                _message.value = it.meldung()
            }.onFailure {
                if (!leise) _message.value = "Sync fehlgeschlagen: ${it.message}"
            }
            syncLaeuft = false
        }
    }

    init {
        // Beim App-Start automatisch abgleichen, falls aktiviert
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (settings.value.autoSync) syncNow(leise = true)
        }
    }

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
            if (settings.value.autoSync) syncNow(leise = true)
        }
    }

    /** Bilder aus der Galerie in den Zimmerordner des angegebenen Tages übernehmen. */
    fun importGalleryPhotos(
        roomId: String,
        uris: List<Uri>,
        dateFolder: String = Dates.todayFolder(),
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = uris.count { photoStore.importFromGallery(roomId, it, dateFolder) != null }
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
                    PruefberichtPdf.write(listOf(report), out, logo)
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
                    PruefberichtPdf.write(reports, out, logo)
                } ?: error("Datei konnte nicht geöffnet werden")
                reports.size
            }.onSuccess {
                _message.value = "PDF mit $it Prüfbericht(en) exportiert"
            }.onFailure {
                _message.value = "PDF-Export fehlgeschlagen: ${it.message}"
            }
        }
    }

    /** Basisdaten des Stundenzettels einer Station (Vorschau + Export). */
    data class StundenzettelBasis(
        val station: String,
        val zeitraum: String,
        val leistungen: List<StundenzettelPdf.Leistung>,
        val material: List<Pair<String, Int>>
    )

    val signatureStore get() = app.signatureStore

    val alleStundenzettel: StateFlow<List<StundenzettelEntity>> = repository.stundenzettel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Gespeicherten Stundenzettel für Station + aktuellen Zeitraum laden;
     * ein neuer Zettel wird sofort gespeichert, damit Unterschriften eine
     * feste Zettel-ID haben.
     */
    suspend fun ladeStundenzettel(station: String): StundenzettelEntity =
        withContext(Dispatchers.IO) {
            val start = settings.value.zeitraumStartIso()
            repository.getStundenzettel(station, start) ?: repository.saveStundenzettel(
                StundenzettelEntity(
                    station = station,
                    zeitraumStart = start,
                    auftragsnummer = repository.naechsteAuftragsnummer(),
                    datum = Dates.todayGerman()
                )
            )
        }

    /** Gespeicherten Stundenzettel per ID laden (aus der Liste). */
    suspend fun ladeStundenzettelById(id: Long): StundenzettelEntity? =
        withContext(Dispatchers.IO) { repository.getStundenzettelById(id) }

    /** Stundenzettel-Eingaben speichern (Stunden lassen sich später wieder ändern). */
    fun speichereStundenzettel(zettel: StundenzettelEntity, quiet: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveStundenzettel(zettel)
            if (!quiet) _message.value = "Stundenzettel gespeichert"
        }
    }

    /**
     * Leistungen/Material im Zeitfenster des Zettels: von seinem Zeitraumbeginn
     * bis zum Beginn des nächsten Zettels derselben Station – so bleiben auch
     * ältere gespeicherte Stundenzettel korrekt exportierbar.
     */
    private suspend fun stundenzettelBasis(zettel: StundenzettelEntity): StundenzettelBasis {
        val (start, ende) = repository.zettelFenster(zettel)
        val roomsById = repository.allRooms().associateBy { it.id }
        val inspektionen = repository.allInspections()
            .filter {
                it.datum >= start && (ende == null || it.datum < ende) &&
                    roomsById[it.roomId]?.station == zettel.station
            }
            .sortedWith(compareBy({ roomsById[it.roomId]?.zimmer ?: "" }, { it.datum }))
        val leistungen = inspektionen.map {
            StundenzettelPdf.Leistung(
                zimmer = roomsById[it.roomId]?.zimmer ?: it.roomId,
                datum = it.datum,
                arbeiten = it.arbeitenListe()
            )
        }
        // Material zusammenzählen (Katalogreihenfolge, dann Freenet, dann Sonstiges)
        val counts = LinkedHashMap<String, Int>()
        inspektionen.forEach { insp ->
            insp.arbeitenListe().forEach { a -> counts[a] = (counts[a] ?: 0) + 1 }
        }
        val order = Arbeiten.KATALOG + Arbeiten.FREENET_VERLAENGERT
        val material = order.filter { counts.containsKey(it) }.map { it to counts.getValue(it) } +
            counts.keys.filter { it !in order }.sorted().map { it to counts.getValue(it) }
        val zeitraumText = if (ende == null) "ab ${Dates.isoToGerman(start)}"
        else "${Dates.isoToGerman(start)} – ${
            Dates.parseIso(ende)?.minusDays(1)?.let { Dates.isoToGerman(it.toString()) } ?: Dates.isoToGerman(ende)
        }"
        return StundenzettelBasis(
            station = zettel.station,
            zeitraum = zeitraumText,
            leistungen = leistungen,
            material = material
        )
    }

    /** Vorschau der Stundenzettel-Daten für den Eingabe-Screen. */
    suspend fun stundenzettelVorschau(zettel: StundenzettelEntity): StundenzettelBasis =
        withContext(Dispatchers.IO) { stundenzettelBasis(zettel) }

    /**
     * Stundenzettel/Leistungsnachweis als PDF – mit Auftragsnummer, Stunden,
     * Anfahrt und den gespeicherten Unterschriften. Die Eingaben werden dabei
     * auch gespeichert, damit die Stunden später anpassbar bleiben.
     */
    fun exportStundenzettel(
        uri: Uri,
        eingabe: StundenzettelEntity,
        signaturStation: android.graphics.Bitmap?,
        signaturTechniker: android.graphics.Bitmap?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveStundenzettel(eingabe)
                val basis = stundenzettelBasis(eingabe)
                require(basis.leistungen.isNotEmpty()) {
                    "Für Station ${eingabe.station} wurden im Zeitraum keine Prüfungen erfasst"
                }
                val zettel = StundenzettelPdf.Stundenzettel(
                    station = basis.station,
                    zeitraum = basis.zeitraum,
                    leistungen = basis.leistungen,
                    material = basis.material,
                    auftragsnummer = eingabe.auftragsnummer,
                    datum = eingabe.datum,
                    arbeitsstunden = eingabe.stunden.trim().let { if (it.isBlank()) "" else "$it Std." },
                    anfahrt = eingabe.anfahrt.trim().let { if (it.isBlank()) "" else "$it Std." },
                    techniker = eingabe.techniker,
                    signaturStation = signaturStation,
                    signaturTechniker = signaturTechniker,
                    logo = logo
                )
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    StundenzettelPdf.write(zettel, out)
                } ?: error("Datei konnte nicht geöffnet werden")
                basis.leistungen.size
            }.onSuccess {
                _message.value = "Stundenzettel erstellt ($it Zimmer)"
            }.onFailure {
                _message.value = "Stundenzettel fehlgeschlagen: ${it.message}"
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
                    PruefberichtPdf.write(reports, out, logo)
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

    // ----- Backup & Gerätewechsel -----

    /** Vollständiges verschlüsseltes Backup (DB, Fotos, Unterschriften, Einstellungen). */
    fun createBackup(uri: Uri, passwort: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    de.excero.tvwartung.files.BackupManager(app).erstellen(out, passwort.toCharArray())
                } ?: error("Datei konnte nicht geöffnet werden")
            }.onSuccess {
                _message.value = "Backup erstellt ($it Dateien)"
            }.onFailure {
                _message.value = "Backup fehlgeschlagen: ${it.message}"
            }
        }
    }

    /**
     * Backup einspielen – ersetzt alle Daten auf diesem Gerät und startet die
     * App anschließend neu, damit die wiederhergestellte Datenbank sauber lädt.
     */
    fun restoreBackup(uri: Uri, passwort: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    de.excero.tvwartung.files.BackupManager(app).einspielen(input, passwort.toCharArray())
                } ?: error("Datei konnte nicht gelesen werden")
            }.onSuccess {
                _message.value = "Backup eingespielt ($it Dateien) – App startet neu …"
                kotlinx.coroutines.delay(1500)
                neustart()
            }.onFailure {
                _message.value = "Einspielen fehlgeschlagen: ${it.message}"
            }
        }
    }

    private fun neustart() {
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
        if (intent != null) app.startActivity(intent)
        kotlin.system.exitProcess(0)
    }
}
