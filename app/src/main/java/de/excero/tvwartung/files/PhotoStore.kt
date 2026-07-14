package de.excero.tvwartung.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import de.excero.tvwartung.util.Dates
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Verwaltet die Zimmerfotos im app-eigenen externen Speicher unter
 * Fotos_Zimmer/<Station_Zimmer>/<JJJJMMTT>/ — exakt die Struktur, die
 * anschließend in den HiDrive hochgeladen wird.
 */
class PhotoStore(private val context: Context) {

    private val timeFormat = DateTimeFormatter.ofPattern("HHmmss")
    private val bildEndungen = setOf("jpg", "jpeg", "png")

    private fun istBild(f: File): Boolean =
        f.isFile && f.length() > 0 && f.extension.lowercase() in bildEndungen

    fun rootDir(): File =
        File(context.getExternalFilesDir(null), "Fotos_Zimmer").apply { mkdirs() }

    fun dirFor(roomId: String, dateFolder: String = Dates.todayFolder()): File =
        File(rootDir(), "$roomId/$dateFolder").apply { mkdirs() }

    /** Legt die Zieldatei für ein neues Foto an ("fern", "nah" oder "galerie"). */
    fun newPhotoFile(roomId: String, label: String): File {
        val time = LocalTime.now().format(timeFormat)
        val dir = dirFor(roomId)
        var file = File(dir, "${roomId}_${Dates.todayFolder()}_${label}_$time.jpg")
        var suffix = 1
        while (file.exists()) {
            file = File(dir, "${roomId}_${Dates.todayFolder()}_${label}_${time}_$suffix.jpg")
            suffix++
        }
        return file
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Kopiert ein Bild aus der Galerie (SAF-Uri) in den Zimmerordner des heutigen Tages. */
    fun importFromGallery(roomId: String, uri: Uri): File? {
        val target = newPhotoFile(roomId, "galerie")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    /** Alle heute aufgenommenen Fotos eines Zimmers. */
    fun photosToday(roomId: String): List<File> =
        photosFor(roomId, Dates.todayFolder())

    /** Fotos (nur Bilddateien) eines Zimmers an einem bestimmten Tag (JJJJMMTT). */
    fun photosFor(roomId: String, dateFolder: String): List<File> {
        if (dateFolder.isBlank()) return emptyList()
        return File(rootDir(), "$roomId/$dateFolder")
            .listFiles { f -> istBild(f) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Zielpfad für das Prüfbericht-PDF eines Zimmers im jeweiligen Tagesordner
     * (legt den Ordner an). Das PDF liegt damit direkt bei den Fotos und wird
     * beim ZIP-Export mit hochgeladen.
     */
    fun pdfFileFor(roomId: String, dateFolder: String): File =
        File(dirFor(roomId, dateFolder), "Pruefbericht_${roomId}_$dateFolder.pdf")

    fun delete(file: File) {
        file.delete()
    }

    /** Anzahl der Fotos, die heute insgesamt aufgenommen wurden. */
    fun countToday(): Int {
        val today = Dates.todayFolder()
        return rootDir().listFiles()?.sumOf { roomDir ->
            File(roomDir, today).listFiles { f -> istBild(f) }?.size ?: 0
        } ?: 0
    }

    /** Leere Dateien entfernen (abgebrochene Kameraaufnahmen). */
    fun cleanupEmptyFiles() {
        rootDir().walkTopDown()
            .filter { it.isFile && it.length() == 0L }
            .forEach { it.delete() }
    }
}
