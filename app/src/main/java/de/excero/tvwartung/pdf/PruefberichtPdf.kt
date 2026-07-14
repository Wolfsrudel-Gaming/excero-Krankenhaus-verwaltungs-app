package de.excero.tvwartung.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.excero.tvwartung.data.Inspection
import de.excero.tvwartung.data.TvRoom
import de.excero.tvwartung.util.Dates
import java.io.File
import java.io.OutputStream

/**
 * Erzeugt aus gespeicherten Prüfbögen ein ansprechendes PDF (A4) inklusive der
 * an diesem Tag aufgenommenen Fotos – ohne externe Bibliotheken
 * (android.graphics.pdf.PdfDocument).
 */
object PruefberichtPdf {

    data class Report(
        val room: TvRoom,
        val inspection: Inspection,
        val photos: List<File>
    )

    private const val PAGE_W = 595   // A4 in PostScript-Punkten
    private const val PAGE_H = 842
    private val MARGIN = 40f
    private val CONTENT_W = PAGE_W - 2 * MARGIN

    private val TEAL = Color.rgb(0, 105, 92)
    private val TEAL_LIGHT = Color.rgb(224, 242, 239)
    private val GREEN = Color.rgb(46, 125, 50)
    private val RED = Color.rgb(198, 40, 40)
    private val GRAY = Color.rgb(117, 117, 117)
    private val ROW_ALT = Color.rgb(245, 248, 247)

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        else Typeface.SANS_SERIF
    }

    fun write(reports: List<Report>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val ctx = PageContext(doc)
            reports.forEach { report ->
                ctx.newPage()
                drawReport(ctx, report)
            }
            ctx.finishPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Verwaltet Seiten, Seitenumbrüche und Fußzeilen. */
    private class PageContext(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var pageNo = 0

        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            )
            canvas = page!!.canvas
            y = MARGIN
        }

        fun finishPage() {
            page?.let { p ->
                val footer = paint(8f, GRAY)
                canvas?.drawText(
                    "KKH TV-Wartung · erstellt am ${Dates.todayGerman()} · Seite $pageNo",
                    MARGIN, PAGE_H - 20f, footer
                )
                doc.finishPage(p)
            }
            page = null
            canvas = null
        }

        /** Neue Seite beginnen, wenn nicht mehr genug Platz ist. */
        fun ensure(height: Float) {
            if (y + height > PAGE_H - 40f) newPage()
        }
    }

    private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        text.split("\n").forEach { rawLine ->
            var line = ""
            rawLine.split(" ").forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(candidate) <= maxWidth) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) result.add(line)
                    // Überlange Einzelwörter hart umbrechen
                    var rest = word
                    while (p.measureText(rest) > maxWidth && rest.length > 1) {
                        var cut = rest.length
                        while (cut > 1 && p.measureText(rest.substring(0, cut)) > maxWidth) cut--
                        result.add(rest.substring(0, cut))
                        rest = rest.substring(cut)
                    }
                    line = rest
                }
            }
            if (line.isNotEmpty()) result.add(line)
        }
        return result
    }

    private fun drawChip(canvas: Canvas, x: Float, yTop: Float, ergebnis: Boolean?) {
        val (text, color) = when (ergebnis) {
            true -> "i.O." to GREEN
            false -> "n.i.O." to RED
            null -> "–" to GRAY
        }
        val chip = paint(9f, Color.WHITE, bold = true)
        val bg = Paint().apply { isAntiAlias = true; this.color = color }
        val w = 44f
        val h = 15f
        canvas.drawRoundRect(RectF(x, yTop, x + w, yTop + h), 5f, 5f, bg)
        val tw = chip.measureText(text)
        canvas.drawText(text, x + (w - tw) / 2, yTop + h - 4.5f, chip)
    }

    private fun drawReport(ctx: PageContext, report: Report) {
        val canvas = ctx.canvas!!
        val room = report.room
        val insp = report.inspection

        // Kopfbereich
        val headerBg = Paint().apply { color = TEAL }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 78f, headerBg)
        canvas.drawText("Prüfung TV-Empfangsgeräte", MARGIN, 38f, paint(19f, Color.WHITE, bold = true))
        canvas.drawText(
            "Kinderkrankenhaus Köln · Prüfbericht ${room.id} · ${Dates.isoToGerman(insp.datum)}",
            MARGIN, 58f, paint(10f, Color.WHITE)
        )
        ctx.y = 100f

        // Info-Block (zwei Spalten)
        val infoBg = Paint().apply { color = TEAL_LIGHT }
        val infoH = 74f
        canvas.drawRoundRect(RectF(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y + infoH), 8f, 8f, infoBg)
        val label = paint(8.5f, GRAY)
        val value = paint(10.5f, Color.BLACK, bold = true)
        val col2 = MARGIN + CONTENT_W / 2
        var iy = ctx.y + 18f
        fun info(x: Float, yy: Float, l: String, v: String) {
            canvas.drawText(l, x + 12f, yy, label)
            canvas.drawText(v.ifBlank { "–" }, x + 12f, yy + 13f, value)
        }
        info(MARGIN, iy, "Station / Zimmer", "${room.station} / ${room.zimmer}")
        info(col2, iy, "TV-Typ", room.tvTyp)
        iy += 32f
        info(MARGIN, iy, "TV Seriennummer", room.seriennummer)
        info(col2, iy, "Freenet-ID · gültig bis", buildString {
            append(room.freenetId.ifBlank { "–" })
            if (room.gueltigBis.isNotBlank()) append(" · ${Dates.isoToGerman(room.gueltigBis)}")
        })
        ctx.y += infoH + 18f

        // Prüfpunkte-Tabelle
        canvas.drawText("Prüfpunkte", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
        ctx.y += 14f
        val body = paint(10f, Color.BLACK)
        val remark = paint(9f, GRAY)
        val labelW = 240f
        val chipX = MARGIN + labelW + 10f
        val remarkX = chipX + 58f
        val remarkW = MARGIN + CONTENT_W - remarkX

        insp.punkte().forEachIndexed { index, (titel, ergebnis, bemerkung) ->
            val extra = when {
                titel.startsWith("Gültigkeit") && room.gueltigBis.isNotBlank() ->
                    "gültig bis: ${Dates.isoToGerman(room.gueltigBis)}"
                else -> bemerkung
            }
            val remarkLines = wrap(extra, remark, remarkW)
            val rowH = maxOf(22f, 10f + remarkLines.size * 11f)
            ctx.ensure(rowH)
            val c = ctx.canvas!!
            if (index % 2 == 0) {
                c.drawRect(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y + rowH, Paint().apply { color = ROW_ALT })
            }
            c.drawText(titel, MARGIN + 4f, ctx.y + 15f, body)
            drawChip(c, chipX, ctx.y + 4f, ergebnis)
            remarkLines.forEachIndexed { i, line ->
                c.drawText(line, remarkX, ctx.y + 14f + i * 11f, remark)
            }
            ctx.y += rowH
        }
        ctx.y += 14f

        // Bemerkungen
        if (insp.bemerkungen.isNotBlank()) {
            val lines = wrap(insp.bemerkungen, body, CONTENT_W - 8f)
            ctx.ensure(30f + lines.size * 13f)
            val c = ctx.canvas!!
            c.drawText("Bemerkungen", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
            ctx.y += 16f
            lines.forEach { line ->
                ctx.ensure(13f)
                ctx.canvas!!.drawText(line, MARGIN + 4f, ctx.y + 10f, body)
                ctx.y += 13f
            }
            ctx.y += 10f
        }

        // Fotos (2 Spalten)
        if (report.photos.isNotEmpty()) {
            ctx.ensure(40f)
            ctx.canvas!!.drawText("Fotos", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
            ctx.y += 14f
            val gap = 10f
            val cellW = (CONTENT_W - gap) / 2
            var col = 0
            var rowMaxH = 0f
            report.photos.forEach { file ->
                val bmp = decodeScaled(file, 900) ?: return@forEach
                val scale = minOf(cellW / bmp.width, 240f / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val cellH = h + 16f
                if (col == 0) {
                    ctx.ensure(cellH)
                } else if (ctx.y + cellH > PAGE_H - 40f) {
                    // Zweite Spalte passt nicht mehr: neue Zeile auf neuer Seite
                    ctx.y += rowMaxH
                    ctx.newPage()
                    col = 0
                    rowMaxH = 0f
                }
                val x = MARGIN + col * (cellW + gap)
                val c = ctx.canvas!!
                c.drawBitmap(bmp, null, RectF(x, ctx.y, x + w, ctx.y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                c.drawText(photoLabel(file), x, ctx.y + h + 11f, paint(8f, GRAY))
                bmp.recycle()
                rowMaxH = maxOf(rowMaxH, cellH + 8f)
                col++
                if (col == 2) {
                    ctx.y += rowMaxH
                    col = 0
                    rowMaxH = 0f
                }
            }
            if (col > 0) ctx.y += rowMaxH
        }
    }

    /** "A4_01a_20260714_fern_101530.jpg" → "fern · 10:15:30" */
    private fun photoLabel(file: File): String {
        val parts = file.nameWithoutExtension.split("_")
        // Uhrzeit ist der letzte 6-stellige Ziffernblock, das Label steht davor
        val timeIndex = parts.indexOfLast { it.length == 6 && it.all { c -> c.isDigit() } }
        if (timeIndex >= 1) {
            val time = parts[timeIndex]
            val label = parts[timeIndex - 1]
            return "$label · ${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
        }
        return file.name
    }

    private fun decodeScaled(file: File, reqWidth: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }
}
