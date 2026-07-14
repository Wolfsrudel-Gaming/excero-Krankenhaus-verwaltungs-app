package de.excero.tvwartung.files

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packt die Fotoordner in eine ZIP-Datei. Die Einträge behalten die
 * HiDrive-Struktur <Station_Zimmer>/<JJJJMMTT>/<Foto>.jpg, sodass die ZIP
 * nur noch entpackt bzw. hochgeladen werden muss.
 */
object ZipExporter {

    /**
     * @param root        Wurzelverzeichnis (Fotos_Zimmer)
     * @param dateFolder  Wenn gesetzt, werden nur Unterordner mit diesem Datum
     *                    (JJJJMMTT) gepackt, sonst alles.
     * @return Anzahl der gepackten Dateien.
     */
    fun export(root: File, out: OutputStream, dateFolder: String? = null): Int {
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
        }
        return count
    }
}
