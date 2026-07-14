package de.excero.tvwartung.excel

import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.util.Dates
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Liest die KKH-Übersicht (Tabelle1) direkt aus einer .xlsx-Datei, ohne externe
 * Bibliotheken. Erwartete Spalten: A=ID, B=Station, C=Zimmer, D=Lebenslauf,
 * E=letzte Prüfung, F=TV-Typ, G=TV Seriennummer, H=Freenet-ID, I=Gültig bis.
 */
object XlsxReader {

    fun readRooms(input: InputStream): List<TvRoom> {
        val entries = readZipEntries(input)
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetBytes = findFirstSheet(entries)
            ?: throw IllegalArgumentException("Keine Tabelle in der Datei gefunden")
        return parseSheet(sheetBytes, shared)
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.startsWith("xl/") || entry.name.startsWith("[Content_Types]"))) {
                    map[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return map
    }

    /** Erstes Arbeitsblatt gemäß workbook.xml + Beziehungen; Fallback: sheet1.xml. */
    private fun findFirstSheet(entries: Map<String, ByteArray>): ByteArray? {
        val workbook = entries["xl/workbook.xml"]
        val rels = entries["xl/_rels/workbook.xml.rels"]
        if (workbook != null && rels != null) {
            val firstRelId = firstSheetRelId(workbook)
            if (firstRelId != null) {
                val target = relTarget(rels, firstRelId)
                if (target != null) {
                    val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                    entries[path]?.let { return it }
                }
            }
        }
        return entries["xl/worksheets/sheet1.xml"]
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }

    private fun firstSheetRelId(workbookXml: ByteArray): String? {
        val parser = newParser(workbookXml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "id") return parser.getAttributeValue(i)
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun relTarget(relsXml: ByteArray, relId: String): String? {
        val parser = newParser(relsXml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                var id: String? = null
                var target: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "Id" -> id = parser.getAttributeValue(i)
                        "Target" -> target = parser.getAttributeValue(i)
                    }
                }
                if (id == relId) return target
            }
            event = parser.next()
        }
        return null
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = newParser(bytes)
        var event = parser.eventType
        var current = StringBuilder()
        var inSi = false
        var inText = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; current = StringBuilder() }
                    "t" -> if (inSi) inText = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> { inSi = false; strings.add(current.toString()) }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<TvRoom> {
        val rooms = mutableListOf<TvRoom>()
        val parser = newParser(bytes)
        var event = parser.eventType

        var rowIndex = 0
        var cells = arrayOfNulls<String>(16)   // Rohtexte der Spalten A..P
        var numeric = BooleanArray(16)          // true = Zelle war numerisch (Datumskandidat)
        var cellColumn = -1
        var cellType = ""
        var cellValue = StringBuilder()
        var inValue = false
        var inInlineText = false

        fun flushRow() {
            if (rowIndex < 2) return // Kopfzeile überspringen
            val id = cells[0]?.trim().orEmpty()
            if (id.isEmpty()) return
            fun text(i: Int) = cells[i]?.trim().orEmpty()
            fun date(i: Int): String {
                val raw = text(i)
                if (raw.isEmpty()) return ""
                return if (numeric[i]) {
                    raw.toDoubleOrNull()?.let { Dates.fromExcelSerial(it).toString() } ?: raw
                } else {
                    Dates.normalizeIso(raw)
                }
            }
            fun number(i: Int): String {
                val raw = text(i)
                // Ganze Zahlen ohne ".0"-Anhang übernehmen (Seriennummern, IDs)
                val d = raw.toDoubleOrNull() ?: return raw
                return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else raw
            }
            rooms.add(
                TvRoom(
                    id = id,
                    station = text(1),
                    zimmer = number(2),
                    lebenslauf = text(3),
                    letztePruefung = date(4),
                    tvTyp = text(5),
                    seriennummer = number(6),
                    freenetId = number(7),
                    gueltigBis = date(8)
                )
            )
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowIndex = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                        cells = arrayOfNulls(16)
                        numeric = BooleanArray(16)
                    }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        cellColumn = columnIndex(ref)
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellValue = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        if (cellColumn in 0..15) {
                            val raw = cellValue.toString()
                            val resolved = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                else -> raw
                            }
                            cells[cellColumn] = resolved
                            numeric[cellColumn] = cellType.isEmpty() || cellType == "n"
                        }
                        cellColumn = -1
                        cellType = ""
                    }
                    "row" -> flushRow()
                }
            }
            event = parser.next()
        }
        return rooms
    }

    /** "AB12" → Spaltenindex (0-basiert). */
    private fun columnIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch.isLetter()) col = col * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return col - 1
    }
}
