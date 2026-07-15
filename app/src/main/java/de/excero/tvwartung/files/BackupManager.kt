package de.excero.tvwartung.files

import android.content.Context
import android.os.Build
import de.excero.tvwartung.data.AppDatabase
import de.excero.tvwartung.util.Dates
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vollständiges, verschlüsseltes Backup (AES-256-GCM, Schlüssel per PBKDF2 aus
 * dem Passwort). Enthält Datenbank, Fotos, Unterschriften und Einstellungen –
 * zum Übertragen auf ein anderes Gerät (z. B. Tablet).
 *
 * Dateiformat: "KKHBAK1" + Salt(16) + IV(12) + AES-GCM( "KKHCHECK" + ZIP ).
 */
class BackupManager(private val context: Context) {

    companion object {
        private val MAGIC = "KKHBAK1".toByteArray(Charsets.US_ASCII)
        private val CHECK = "KKHCHECK".toByteArray(Charsets.US_ASCII)
        private const val ITERATIONS = 150_000
        private const val KEY_BITS = 256
        private const val DB_NAME = "tvwartung.db"
    }

    private fun ableiten(passwort: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(PBEKeySpec(passwort, salt, ITERATIONS, KEY_BITS))
        return SecretKeySpec(key.encoded, "AES")
    }

    private fun fotosDir(): File = File(context.getExternalFilesDir(null), "Fotos_Zimmer")
    private fun signaturenDir(): File = File(context.filesDir, "signaturen")

    /** Backup erstellen und verschlüsselt in den Ausgabestrom schreiben. */
    fun erstellen(out: OutputStream, passwort: CharArray): Int {
        // Datenbank in konsistenten Zustand bringen (WAL leeren)
        AppDatabase.get(context).query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, ableiten(passwort, salt), GCMParameterSpec(128, iv))
        }

        var dateien = 0
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        CipherOutputStream(out, cipher).use { enc ->
            enc.write(CHECK)
            ZipOutputStream(enc.buffered()).use { zip ->
                fun schreibe(name: String, quelle: File) {
                    zip.putNextEntry(ZipEntry(name))
                    quelle.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    dateien++
                }

                // Manifest
                val manifest = JSONObject().apply {
                    put("format", 1)
                    put("erstellt", Dates.nowIsoDateTime())
                    put("geraet", "${Build.MANUFACTURER} ${Build.MODEL}")
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Datenbank
                val dbFile = context.getDatabasePath(DB_NAME)
                require(dbFile.exists()) { "Datenbank nicht gefunden" }
                schreibe("db/$DB_NAME", dbFile)

                // Einstellungen
                val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
                val prefsJson = JSONObject().apply {
                    prefs.all.forEach { (k, v) -> put(k, v?.toString() ?: "") }
                }
                zip.putNextEntry(ZipEntry("prefs/einstellungen.json"))
                zip.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Unterschriften
                signaturenDir().walkTopDown().filter { it.isFile }.forEach { f ->
                    schreibe("signaturen/${f.name}", f)
                }

                // Fotos (inkl. abgelegter Prüfbericht-PDFs)
                val fotosRoot = fotosDir()
                fotosRoot.walkTopDown().filter { it.isFile && it.length() > 0 }.forEach { f ->
                    val rel = f.relativeTo(fotosRoot).path.replace(File.separatorChar, '/')
                    schreibe("fotos/$rel", f)
                }
            }
        }
        return dateien
    }

    /**
     * Backup einspielen: ersetzt Datenbank, Fotos, Unterschriften und
     * Einstellungen auf diesem Gerät vollständig.
     * @throws IllegalArgumentException bei falschem Passwort oder ungültiger Datei.
     */
    fun einspielen(input: InputStream, passwort: CharArray): Int {
        val stream = input.buffered()
        val magic = ByteArray(MAGIC.size)
        require(stream.read(magic) == MAGIC.size && magic.contentEquals(MAGIC)) {
            "Keine gültige Backup-Datei"
        }
        val salt = ByteArray(16).also { require(stream.read(it) == 16) { "Datei beschädigt" } }
        val iv = ByteArray(12).also { require(stream.read(it) == 12) { "Datei beschädigt" } }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, ableiten(passwort, salt), GCMParameterSpec(128, iv))
        }

        // 1) In Staging-Verzeichnis entschlüsseln und entpacken
        val staging = File(context.cacheDir, "restore_tmp").apply {
            deleteRecursively()
            mkdirs()
        }
        var dateien = 0
        try {
            CipherInputStream(stream, cipher).use { dec ->
                val check = ByteArray(CHECK.size)
                var gelesen = 0
                while (gelesen < CHECK.size) {
                    val n = dec.read(check, gelesen, CHECK.size - gelesen)
                    if (n < 0) break
                    gelesen += n
                }
                require(gelesen == CHECK.size && check.contentEquals(CHECK)) { "Falsches Passwort" }

                ZipInputStream(dec).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val ziel = File(staging, entry.name)
                            // Zip-Slip verhindern
                            require(ziel.canonicalPath.startsWith(staging.canonicalPath)) {
                                "Ungültiger Pfad im Backup"
                            }
                            ziel.parentFile?.mkdirs()
                            ziel.outputStream().use { zip.copyTo(it) }
                            dateien++
                        }
                        entry = zip.nextEntry
                    }
                }
            }

            val dbStaged = File(staging, "db/$DB_NAME")
            require(dbStaged.exists() && dbStaged.length() > 0) { "Backup enthält keine Datenbank" }

            // 2) Datenbank ersetzen (Instanz vorher schließen)
            AppDatabase.reset()
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbStaged.copyTo(dbFile, overwrite = true)

            // 3) Unterschriften ersetzen
            signaturenDir().deleteRecursively()
            File(staging, "signaturen").takeIf { it.isDirectory }?.let { quelle ->
                signaturenDir().mkdirs()
                quelle.listFiles()?.forEach { it.copyTo(File(signaturenDir(), it.name), overwrite = true) }
            }

            // 4) Fotos ersetzen
            fotosDir().deleteRecursively()
            File(staging, "fotos").takeIf { it.isDirectory }?.let { quelle ->
                quelle.walkTopDown().filter { it.isFile }.forEach { f ->
                    val ziel = File(fotosDir(), f.relativeTo(quelle).path)
                    ziel.parentFile?.mkdirs()
                    f.copyTo(ziel, overwrite = true)
                }
            }

            // 5) Einstellungen übernehmen
            File(staging, "prefs/einstellungen.json").takeIf { it.exists() }?.let { f ->
                val json = JSONObject(f.readText())
                val editor = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE).edit()
                json.keys().forEach { k -> editor.putString(k, json.optString(k)) }
                editor.apply()
            }

            return dateien
        } catch (e: javax.crypto.AEADBadTagException) {
            throw IllegalArgumentException("Falsches Passwort oder Datei beschädigt")
        } catch (e: java.io.IOException) {
            if (e.cause is javax.crypto.AEADBadTagException) {
                throw IllegalArgumentException("Falsches Passwort oder Datei beschädigt")
            }
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }
}
