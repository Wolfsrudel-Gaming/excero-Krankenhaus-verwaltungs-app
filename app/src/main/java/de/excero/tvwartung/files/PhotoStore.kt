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

    fun rootDir(): File =
        File(context.getExternalFilesDir(null), "Fotos_Zimmer").apply { mkdirs() }

    fun dirFor(roomId: String, dateFolder: String = Dates.todayFolder()): File =
        File(rootDir(), "$roomId/$dateFolder").apply { mkdirs() }

    /** Legt die Zieldatei für ein neues Foto an ("fern" oder "nah"). */
    fun newPhotoFile(roomId: String, label: String): File {
        val time = LocalTime.now().format(timeFormat)
        return File(dirFor(roomId), "${roomId}_${Dates.todayFolder()}_${label}_$time.jpg")
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Alle heute aufgenommenen Fotos eines Zimmers. */
    fun photosToday(roomId: String): List<File> =
        dirFor(roomId).listFiles { f -> f.isFile && f.length() > 0 }
            ?.sortedBy { it.name } ?: emptyList()

    fun delete(file: File) {
        file.delete()
    }

    /** Anzahl der Fotos, die heute insgesamt aufgenommen wurden. */
    fun countToday(): Int {
        val today = Dates.todayFolder()
        return rootDir().listFiles()?.sumOf { roomDir ->
            File(roomDir, today).listFiles { f: File -> f.isFile && f.length() > 0 }?.size ?: 0
        } ?: 0
    }

    /** Leere Dateien entfernen (abgebrochene Kameraaufnahmen). */
    fun cleanupEmptyFiles() {
        rootDir().walkTopDown()
            .filter { it.isFile && it.length() == 0L }
            .forEach { it.delete() }
    }
}
