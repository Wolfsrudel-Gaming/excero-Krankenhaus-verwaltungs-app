package de.excero.tvwartung.excel

import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.util.Dates
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Schreibt die Datenbank als .xlsx-Datei mit zwei Blättern:
 *  - "Übersicht": gleiche Spaltenstruktur wie die bisherige KKH-Übersicht (Tabelle1)
 *  - "Prüfprotokolle": alle in der App ausgefüllten Prüfbögen
 * Implementiert ohne externe Bibliotheken (Minimal-OOXML mit Inline-Strings).
 */
object XlsxWriter {

    fun write(rooms: List<TvRoom>, inspections: List<Inspection>, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("[Content_Types].xml", contentTypes())
            entry("_rels/.rels", rootRels())
            entry("xl/workbook.xml", workbook())
            entry("xl/_rels/workbook.xml.rels", workbookRels())
            entry("xl/styles.xml", styles())
            entry("xl/worksheets/sheet1.xml", overviewSheet(rooms))
            entry("xl/worksheets/sheet2.xml", inspectionSheet(inspections))
        }
    }

    private fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            '\r' -> {}
            '\n' -> append("&#10;")
            else -> if (ch.code >= 0x20 || ch == '\t') append(ch)
        }
    }

    private fun colName(index: Int): String {
        var i = index + 1
        val sb = StringBuilder()
        while (i > 0) {
            val rem = (i - 1) % 26
            sb.insert(0, ('A' + rem))
            i = (i - 1) / 26
        }
        return sb.toString()
    }

    /** Text-Zelle mit Inline-String; Style 3 = Zeilenumbruch (Lebenslauf). */
    private fun textCell(col: Int, row: Int, value: String, style: Int = 0): String {
        if (value.isEmpty()) return ""
        val s = if (style != 0) " s=\"$style\"" else ""
        return "<c r=\"${colName(col)}$row\" t=\"inlineStr\"$s><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"
    }

    /** ISO-Datum als echte Excel-Datumszelle (Style 1 = dd.mm.yyyy); sonst Text. */
    private fun dateCell(col: Int, row: Int, isoValue: String): String {
        if (isoValue.isEmpty()) return ""
        val date = Dates.parseIso(isoValue) ?: return textCell(col, row, isoValue)
        return "<c r=\"${colName(col)}$row\" s=\"1\"><v>${Dates.toExcelSerial(date)}</v></c>"
    }

    private fun boolCell(col: Int, row: Int, value: Boolean?): String =
        when (value) {
            null -> ""
            true -> textCell(col, row, "i.O.")
            false -> textCell(col, row, "n.i.O.")
        }

    private fun headerRow(row: Int, titles: List<String>): String = buildString {
        append("<row r=\"$row\">")
        titles.forEachIndexed { i, t ->
            append("<c r=\"${colName(i)}$row\" t=\"inlineStr\" s=\"2\"><is><t>${esc(t)}</t></is></c>")
        }
        append("</row>")
    }

    private fun overviewSheet(rooms: List<TvRoom>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<cols>")
        append("<col min=\"1\" max=\"1\" width=\"10\" customWidth=\"1\"/>")
        append("<col min=\"2\" max=\"3\" width=\"8\" customWidth=\"1\"/>")
        append("<col min=\"4\" max=\"4\" width=\"70\" customWidth=\"1\"/>")
        append("<col min=\"5\" max=\"9\" width=\"16\" customWidth=\"1\"/>")
        append("</cols>")
        append("<sheetData>")
        append(
            headerRow(
                1,
                listOf(
                    "ID", "Station", "Zimmer", "Lebenslauf", "letzte Prüfung",
                    "TV-Typ", "TV Seriennummer", "Freenet-ID", "Gültig bis"
                )
            )
        )
        rooms.forEachIndexed { i, room ->
            val r = i + 2
            append("<row r=\"$r\">")
            append(textCell(0, r, room.id))
            append(textCell(1, r, room.station))
            append(textCell(2, r, room.zimmer))
            append(textCell(3, r, room.lebenslauf, style = 3))
            append(dateCell(4, r, room.letztePruefung))
            append(textCell(5, r, room.tvTyp))
            append(textCell(6, r, room.seriennummer))
            append(textCell(7, r, room.freenetId))
            append(dateCell(8, r, room.gueltigBis))
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun inspectionSheet(inspections: List<Inspection>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<cols><col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>")
        append("<col min=\"2\" max=\"2\" width=\"10\" customWidth=\"1\"/>")
        append("<col min=\"3\" max=\"10\" width=\"14\" customWidth=\"1\"/>")
        append("<col min=\"11\" max=\"11\" width=\"50\" customWidth=\"1\"/></cols>")
        append("<sheetData>")
        append(
            headerRow(
                1,
                listOf(
                    "Datum", "Zimmer", "Empfang vorhanden?", "Seriennummer TV stimmt?",
                    "Freenet TV-ID stimmt?", "DVD-Test", "Fernbedienung", "Halterung (fest?)",
                    "Gültigkeit Freenet > 3 Monate?", "Freenet verlängert", "Bemerkungen"
                )
            )
        )
        inspections.forEachIndexed { i, insp ->
            val r = i + 2
            val remarks = listOf(
                insp.bemerkungEmpfang, insp.bemerkungSeriennummer, insp.bemerkungFreenetId,
                insp.bemerkungDvd, insp.bemerkungFernbedienung, insp.bemerkungHalterung,
                insp.bemerkungen
            ).filter { it.isNotBlank() }.joinToString("; ")
            append("<row r=\"$r\">")
            append(dateCell(0, r, insp.datum))
            append(textCell(1, r, insp.roomId))
            append(boolCell(2, r, insp.empfangVorhanden))
            append(boolCell(3, r, insp.seriennummerStimmt))
            append(boolCell(4, r, insp.freenetIdStimmt))
            append(boolCell(5, r, insp.dvdTest))
            append(boolCell(6, r, insp.fernbedienung))
            append(boolCell(7, r, insp.halterungFest))
            append(boolCell(8, r, insp.gueltigkeitAusreichend))
            append(boolCell(9, r, insp.freenetVerlaengert))
            append(textCell(10, r, remarks))
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Übersicht" sheetId="1" r:id="rId1"/>
<sheet name="Prüfprotokolle" sheetId="2" r:id="rId2"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /**
     * Styles: 0 = Standard, 1 = Datum (dd.mm.yyyy), 2 = fette Kopfzeile,
     * 3 = Text mit Zeilenumbruch (Lebenslauf).
     */
    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="dd\.mm\.yyyy"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>
</cellXfs>
</styleSheet>"""
}
