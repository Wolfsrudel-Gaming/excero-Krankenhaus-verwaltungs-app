package de.excero.tvwartung.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import de.excero.tvwartung.util.Dates
import java.io.File
import java.io.FileOutputStream
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
    fun newPhotoFile(roomId: String, label: String, dateFolder: String = Dates.todayFolder()): File {
        val time = LocalTime.now().format(timeFormat)
        val dir = dirFor(roomId, dateFolder)
        var file = File(dir, "${roomId}_${dateFolder}_${label}_$time.jpg")
        var suffix = 1
        while (file.exists()) {
            file = File(dir, "${roomId}_${dateFolder}_${label}_${time}_$suffix.jpg")
            suffix++
        }
        return file
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Kopiert ein Bild aus der Galerie (SAF-Uri) in den Zimmerordner des angegebenen Tages. */
    fun importFromGallery(roomId: String, uri: Uri, dateFolder: String = Dates.todayFolder()): File? {
        val target = newPhotoFile(roomId, "galerie", dateFolder)
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

    /**
     * Brennt ein Wasserzeichen (mehrere Zeilen, z. B. Station/Zimmer + Zeitstempel)
     * unten ins Foto ein – als Nachweis, wann und wo es aufgenommen wurde. Die
     * EXIF-Ausrichtung wird vorher angewandt, damit die Schrift richtig herum steht.
     */
    fun applyWatermark(file: File, zeilen: List<String>) {
        runCatching {
            val orientation = ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val aufrecht = nachExif(decoded, orientation)
            val bmp = if (aufrecht.isMutable) aufrecht else aufrecht.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            val textSize = (w * 0.030f).coerceIn(26f, 90f)
            val pad = textSize * 0.5f
            val zeilenHoehe = textSize * 1.3f
            val bandHoehe = pad * 2 + zeilenHoehe * zeilen.size

            val hintergrund = Paint().apply { color = Color.argb(115, 0, 0, 0) }
            canvas.drawRect(0f, h - bandHoehe, w, h, hintergrund)

            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                setShadowLayer(textSize * 0.12f, 0f, 0f, Color.BLACK)
                isFakeBoldText = true
            }
            var y = h - bandHoehe + pad + textSize
            zeilen.forEach { zeile ->
                canvas.drawText(zeile, pad, y, text)
                y += zeilenHoehe
            }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
    }

    private fun nachExif(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

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
