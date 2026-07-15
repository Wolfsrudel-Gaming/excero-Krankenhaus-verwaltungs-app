package de.excero.tvwartung.files

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Blockweise AES-GCM-Verschlüsselung für beliebig große Dateien mit konstantem
 * Speicherverbrauch. (Androids Krypto-Provider puffert bei GCM sonst den
 * kompletten Datenstrom im RAM, was bei großen Backups zum OOM führt.)
 *
 * Drahtformat je Block: 4 Byte Länge (big-endian) + Ciphertext.
 * Klartext je Block: 1 Typ-Byte ('D' Daten / 'E' Ende) + Nutzdaten.
 * IV je Block: 8 Byte zufälliger Präfix + 4 Byte Blockzähler.
 */
internal object GcmChunk {
    const val CHUNK_SIZE = 1 shl 20   // 1 MiB Klartext je Block
    const val IV_PREFIX_SIZE = 8
    const val TYP_DATEN = 'D'.code.toByte()
    const val TYP_ENDE = 'E'.code.toByte()

    fun iv(prefix: ByteArray, counter: Int): ByteArray = ByteArray(12).also {
        System.arraycopy(prefix, 0, it, 0, IV_PREFIX_SIZE)
        it[8] = (counter ushr 24).toByte()
        it[9] = (counter ushr 16).toByte()
        it[10] = (counter ushr 8).toByte()
        it[11] = counter.toByte()
    }
}

internal class GcmChunkOutputStream(
    private val out: OutputStream,
    private val key: SecretKeySpec,
    private val ivPrefix: ByteArray
) : OutputStream() {

    private val buffer = ByteArray(GcmChunk.CHUNK_SIZE)
    private var pos = 0
    private var counter = 0
    private var geschlossen = false

    private fun schreibeBlock(typ: Byte, daten: ByteArray, len: Int) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, GcmChunk.iv(ivPrefix, counter)))
        }
        val klartext = ByteArray(len + 1)
        klartext[0] = typ
        System.arraycopy(daten, 0, klartext, 1, len)
        val ct = cipher.doFinal(klartext)
        out.write((ct.size ushr 24) and 0xFF)
        out.write((ct.size ushr 16) and 0xFF)
        out.write((ct.size ushr 8) and 0xFF)
        out.write(ct.size and 0xFF)
        out.write(ct)
        counter++
    }

    private fun flushBlock() {
        if (pos > 0) {
            schreibeBlock(GcmChunk.TYP_DATEN, buffer, pos)
            pos = 0
        }
    }

    override fun write(b: Int) {
        buffer[pos++] = b.toByte()
        if (pos == buffer.size) flushBlock()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var rest = len
        while (rest > 0) {
            val n = minOf(rest, buffer.size - pos)
            System.arraycopy(b, o, buffer, pos, n)
            pos += n
            o += n
            rest -= n
            if (pos == buffer.size) flushBlock()
        }
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        if (geschlossen) return
        geschlossen = true
        flushBlock()
        schreibeBlock(GcmChunk.TYP_ENDE, ByteArray(0), 0)
        out.flush()
    }
}

internal class GcmChunkInputStream(
    private val input: InputStream,
    private val key: SecretKeySpec,
    private val ivPrefix: ByteArray
) : InputStream() {

    private var buffer = ByteArray(0)
    private var pos = 0
    private var counter = 0
    private var ende = false

    private fun leseGenau(n: Int): ByteArray {
        val b = ByteArray(n)
        var gelesen = 0
        while (gelesen < n) {
            val r = input.read(b, gelesen, n - gelesen)
            if (r < 0) throw IOException("Backup-Datei unvollständig")
            gelesen += r
        }
        return b
    }

    /** @return false, wenn der Ende-Block erreicht wurde. */
    private fun naechsterBlock(): Boolean {
        if (ende) return false
        val lenBytes = leseGenau(4)
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
            ((lenBytes[1].toInt() and 0xFF) shl 16) or
            ((lenBytes[2].toInt() and 0xFF) shl 8) or
            (lenBytes[3].toInt() and 0xFF)
        if (len < 17 || len > GcmChunk.CHUNK_SIZE + 1 + 16) {
            throw IOException("Backup-Datei beschädigt")
        }
        val ct = leseGenau(len)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, GcmChunk.iv(ivPrefix, counter)))
        }
        val klartext = cipher.doFinal(ct)   // wirft AEADBadTagException bei falschem Passwort
        counter++
        return when (klartext[0]) {
            GcmChunk.TYP_ENDE -> {
                ende = true
                false
            }
            GcmChunk.TYP_DATEN -> {
                buffer = klartext
                pos = 1
                true
            }
            else -> throw IOException("Backup-Datei beschädigt (unbekannter Blocktyp)")
        }
    }

    override fun read(): Int {
        if (pos >= buffer.size && !naechsterBlock()) return -1
        return buffer[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= buffer.size && !naechsterBlock()) return -1
        val n = minOf(len, buffer.size - pos)
        System.arraycopy(buffer, pos, b, off, n)
        pos += n
        return n
    }
}
