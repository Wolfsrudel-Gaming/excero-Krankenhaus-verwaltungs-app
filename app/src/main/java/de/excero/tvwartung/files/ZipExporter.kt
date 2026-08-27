package de.excero.tvwartung.files

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packt die Fotoordner in eine ZIP-Datei. Die Einträge behalten die
 * HiDrive-Struktur <Station_Zimmer>/<JJJJMMTT>/<Foto>.jpg, sodass die ZIP
 * nur noch entpackt bzw. hochgeladen werden muss. Beim kompletten Export
 * (kein Datumsfilter) kommt zusätzlich ein Ordner "Stundenzettel/" mit den
 * Leistungsnachweis-PDFs dazu.
 */
object ZipExporter {

    /**
     * @param root             Wurzelverzeichnis (Fotos_Zimmer)
     * @param dateFolder       Wenn gesetzt, werden nur Unterordner mit diesem Datum
     *                         (JJJJMMTT) gepackt, sonst alles.
     * @param stundenzettelDir Wenn gesetzt, kommt der Inhalt zusätzlich unter
     *                         "Stundenzettel/" in die ZIP (nur beim kompletten Export sinnvoll,
     *                         Stundenzettel sind nicht auf einen einzelnen Tag begrenzt).
     * @return Anzahl der gepackten Dateien.
     */
    fun export(
        root: File,
        out: OutputStream,
        dateFolder: String? = null,
        stundenzettelDir: File? = null
    ): Int {
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            root.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { roomDir ->
                roomDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach dates@{ dayDir ->
                    if (dateFolder != null && dayDir.name != dateFolder) return@dates
                    dayDir.listFiles { f -> f.isFile && f.length() > 0 }?.sortedBy { it.name }?.forEach { photo ->
                        zip.putNextEntry(ZipEntry("${roomDir.name}/${dayDir.name}/${photo.name}"))
                        photo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
            stundenzettelDir?.listFiles { f -> f.isFile && f.length() > 0 }
                ?.sortedBy { it.name }?.forEach { pdf ->
                    zip.putNextEntry(ZipEntry("Stundenzettel/${pdf.name}"))
                    pdf.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
        }
        return count
    }
}
