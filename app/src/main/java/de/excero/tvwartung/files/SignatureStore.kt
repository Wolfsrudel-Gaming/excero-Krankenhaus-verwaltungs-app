package de.excero.tvwartung.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Speichert die auf dem Display geleisteten Unterschriften als PNG-Dateien,
 * gebunden an den jeweiligen Stundenzettel. So kann die Unterschrift auf der
 * Station eingeholt und der Zettel später (mit nachgetragenen Stunden) als
 * PDF exportiert werden.
 */
class SignatureStore(private val context: Context) {

    companion object {
        const val ROLLE_STATION = "station"
        const val ROLLE_TECHNIKER = "techniker"
    }

    private fun dir(): File = File(context.filesDir, "signaturen").apply { mkdirs() }

    private fun file(zettelId: Long, rolle: String): File =
        File(dir(), "zettel_${zettelId}_$rolle.png")

    fun save(zettelId: Long, rolle: String, bitmap: Bitmap) {
        runCatching {
            file(zettelId, rolle).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    fun load(zettelId: Long, rolle: String): Bitmap? {
        val f = file(zettelId, rolle)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun delete(zettelId: Long, rolle: String) {
        file(zettelId, rolle).delete()
    }

    /** Alle gespeicherten Unterschrift-Dateien (für die Server-Synchronisation). */
    fun alleDateien(): List<File> =
        dir().listFiles { f -> f.isFile && f.length() > 0 }?.toList() ?: emptyList()

    fun has(zettelId: Long, rolle: String): Boolean =
        file(zettelId, rolle).let { it.exists() && it.length() > 0 }
}
