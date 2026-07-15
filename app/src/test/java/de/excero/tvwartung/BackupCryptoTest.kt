package de.excero.tvwartung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verifiziert die im BackupManager verwendeten Krypto-Bausteine:
 * PBKDF2-Schlüsselableitung, AES-GCM-Streaming und die schnelle
 * Falsch-Passwort-Erkennung über den KKHCHECK-Prefix.
 */
class BackupCryptoTest {

    private val check = "KKHCHECK".toByteArray(Charsets.US_ASCII)

    private fun key(passwort: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = factory.generateSecret(PBEKeySpec(passwort.toCharArray(), salt, 150_000, 256))
        return SecretKeySpec(k.encoded, "AES")
    }

    @Test
    fun `roundtrip und falsches passwort`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val nutzdaten = "Hallo Stundenzettel ÄÖÜ".toByteArray()

        // Verschlüsseln wie BackupManager.erstellen
        val encOut = ByteArrayOutputStream()
        val enc = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key("geheim123", salt), GCMParameterSpec(128, iv))
        }
        CipherOutputStream(encOut, enc).use {
            it.write(check)
            it.write(nutzdaten)
        }
        val verschluesselt = encOut.toByteArray()

        // Richtiges Passwort: CHECK stimmt, Nutzdaten identisch
        val dec = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key("geheim123", salt), GCMParameterSpec(128, iv))
        }
        CipherInputStream(ByteArrayInputStream(verschluesselt), dec).use { s ->
            val gelesen = s.readBytes()
            assertEquals(check.size + nutzdaten.size, gelesen.size)
            assertEquals(String(check), String(gelesen.copyOfRange(0, check.size)))
            assertEquals(String(nutzdaten), String(gelesen.copyOfRange(check.size, gelesen.size)))
        }

        // Falsches Passwort: erster Block ist Müll -> CHECK-Vergleich schlägt fehl
        val falsch = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key("falsch456", salt), GCMParameterSpec(128, iv))
        }
        val ergebnis = runCatching {
            CipherInputStream(ByteArrayInputStream(verschluesselt), falsch).use { s ->
                val kopf = ByteArray(check.size)
                var g = 0
                while (g < kopf.size) {
                    val n = s.read(kopf, g, kopf.size - g)
                    if (n < 0) break
                    g += n
                }
                g == kopf.size && kopf.contentEquals(check)
            }
        }
        // Entweder Exception (AEAD) oder CHECK-Mismatch – niemals "stimmt"
        assertFalse(ergebnis.getOrDefault(false))
    }
}
