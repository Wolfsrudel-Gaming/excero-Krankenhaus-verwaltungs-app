package de.excero.tvwartung

import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.excel.XlsxReader
import de.excero.tvwartung.excel.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class XlsxRoundTripTest {

    private val rooms = listOf(
        TvRoom(
            id = "A4_01a", station = "A4", zimmer = "01a",
            lebenslauf = "01.10.2020: Prüfung; Sprache neu\n02.02.2026: TV überprüft, Freenet verlängert & Co. <Test>",
            letztePruefung = "2026-02-02", tvTyp = "Lenco",
            seriennummer = "170302795", freenetId = "64473038863", gueltigBis = "2027-03-19"
        ),
        TvRoom(
            id = "B5_SZ", station = "B5", zimmer = "SZ",
            lebenslauf = "", letztePruefung = "", tvTyp = "Telefunken",
            seriennummer = "NCCMBT1012047004894", freenetId = "70516834978", gueltigBis = ""
        )
    )

    private val inspections = listOf(
        Inspection(
            roomId = "A4_01a", datum = "2026-07-14",
            empfangVorhanden = true, seriennummerStimmt = false, freenetIdStimmt = true,
            dvdTest = null, fernbedienung = true, halterungFest = true,
            gueltigkeitAusreichend = true, freenetVerlaengert = false,
            bemerkungSeriennummer = "201403775", bemerkungen = "TV überprüft"
        )
    )

    @Test
    fun `export laesst sich wieder importieren`() {
        val file = File.createTempFile("roundtrip", ".xlsx")
        file.outputStream().use { XlsxWriter.write(rooms, inspections, it) }

        val imported = file.inputStream().use { XlsxReader.readRooms(it) }

        assertEquals(rooms.size, imported.size)
        rooms.zip(imported).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.station, actual.station)
            assertEquals(expected.zimmer, actual.zimmer)
            assertEquals(expected.lebenslauf, actual.lebenslauf)
            assertEquals(expected.letztePruefung, actual.letztePruefung)
            assertEquals(expected.tvTyp, actual.tvTyp)
            assertEquals(expected.seriennummer, actual.seriennummer)
            assertEquals(expected.freenetId, actual.freenetId)
            assertEquals(expected.gueltigBis, actual.gueltigBis)
        }

        // Datei für externe Validierung (z. B. openpyxl) aufheben
        val keep = File("build/test-output/roundtrip.xlsx")
        keep.parentFile.mkdirs()
        file.copyTo(keep, overwrite = true)
    }
}
