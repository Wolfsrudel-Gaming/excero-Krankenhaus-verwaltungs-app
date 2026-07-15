package de.excero.tvwartung

import de.excero.tvwartung.files.GcmChunkInputStream
import de.excero.tvwartung.files.GcmChunkOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Random
import javax.crypto.spec.SecretKeySpec

/** Round-Trip der blockweisen GCM-Verschlüsselung (Grundlage des Backups). */
class GcmChunkStreamTest {

    private fun zufallsKey(): SecretKeySpec =
        SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    @Test
    fun `grosse daten ueber blockgrenzen roundtrip`() {
        val key = zufallsKey()
        val ivPrefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        // 3,5 MiB → mehrere volle Blöcke + Rest
        val daten = ByteArray((3.5 * (1 shl 20)).toInt()).also { Random(42).nextBytes(it) }

        val verschluesselt = ByteArrayOutputStream()
        GcmChunkOutputStream(verschluesselt, key, ivPrefix).use { enc ->
            // in unregelmäßigen Häppchen schreiben, um die Pufferung zu testen
            var off = 0
            var stueck = 1
            while (off < daten.size) {
                val n = minOf(stueck, daten.size - off)
                enc.write(daten, off, n)
                off += n
                stueck = (stueck * 3 + 7) % 50_000 + 1
            }
        }

        val entschluesselt = GcmChunkInputStream(
            ByteArrayInputStream(verschluesselt.toByteArray()), key, ivPrefix
        ).readBytes()

        assertArrayEquals(daten, entschluesselt)
    }

    @Test
    fun `falscher schluessel wird sofort erkannt`() {
        val ivPrefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val verschluesselt = ByteArrayOutputStream()
        GcmChunkOutputStream(verschluesselt, zufallsKey(), ivPrefix).use {
            it.write("Testdaten".toByteArray())
        }

        val ergebnis = runCatching {
            GcmChunkInputStream(
                ByteArrayInputStream(verschluesselt.toByteArray()), zufallsKey(), ivPrefix
            ).readBytes()
        }
        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull() is javax.crypto.AEADBadTagException)
    }

    @Test
    fun `leerer inhalt roundtrip`() {
        val key = zufallsKey()
        val ivPrefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val verschluesselt = ByteArrayOutputStream()
        GcmChunkOutputStream(verschluesselt, key, ivPrefix).use { }
        val entschluesselt = GcmChunkInputStream(
            ByteArrayInputStream(verschluesselt.toByteArray()), key, ivPrefix
        ).readBytes()
        assertArrayEquals(ByteArray(0), entschluesselt)
    }
}
