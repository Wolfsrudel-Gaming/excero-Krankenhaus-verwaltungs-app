package de.excero.tvwartung.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.excero.tvwartung.util.Dates
import java.io.OutputStream

/**
 * Stundenzettel / Leistungsnachweis für eine Station (A4).
 * Auftraggeber ist die Kinderklinik Köln (Amsterdamer Straße); der Zettel listet
 * je Zimmer die durchgeführten Arbeiten, fasst das verbaute Material zusammen
 * und bietet zwei Unterschriftfelder (Station und Dienstleister).
 */
object StundenzettelPdf {

    /** Eine Zeile je geprüftem Zimmer der Station. */
    data class Leistung(
        val zimmer: String,
        val datum: String,          // ISO
        val arbeiten: List<String>
    )

    data class Stundenzettel(
        val station: String,
        val zeitraum: String,       // z. B. "diese Woche (ab 13.07.2026)"
        val leistungen: List<Leistung>,
        val material: List<Pair<String, Int>>,   // (Bezeichnung, Anzahl)
        val auftragsnummer: String = "",          // z. B. "A-2026-0007"
        val logo: Bitmap? = null,                 // Firmenlogo für den Kopfbereich
        val datum: String = "",                   // Tag der Leistung (deutsch)
        val arbeitsstunden: String = "",          // z. B. "3,5 Std."
        val anfahrt: String = "",                 // z. B. "0,5 Std."
        val techniker: String = "",               // Name Dienstleister
        val signaturStation: Bitmap? = null,
        val signaturTechniker: Bitmap? = null
    )

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private val TEAL = Color.rgb(0, 105, 92)
    private val TEAL_LIGHT = Color.rgb(224, 242, 239)
    private val GRAY = Color.rgb(117, 117, 117)
    private val ROW_ALT = Color.rgb(245, 248, 247)
    private val LINE = Color.rgb(180, 180, 180)

    private const val AUFTRAGGEBER_1 = "Kinderklinik Köln"
    private const val AUFTRAGGEBER_2 = "Amsterdamer Straße 59, 50735 Köln"

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        else Typeface.SANS_SERIF
    }

    fun write(zettel: Stundenzettel, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val ctx = Ctx(doc)
            ctx.newPage()
            drawHeader(ctx, zettel)
            drawZeiten(ctx, zettel)
            drawLeistungen(ctx, zettel)
            drawMaterial(ctx, zettel)
            drawUnterschriften(ctx, zettel)
            ctx.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Ctx(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var pageNo = 0
        val station get() = _station
        var _station = ""

        fun newPage() {
            finish()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            y = MARGIN
        }

        fun finish() {
            page?.let { p ->
                canvas?.drawText(
                    "KKH TV-Wartung · Stundenzettel · erstellt am ${Dates.todayGerman()} · Seite $pageNo",
                    MARGIN, PAGE_H - 20f, paint(8f, GRAY)
                )
                doc.finishPage(p)
            }
            page = null
            canvas = null
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - 40f) newPage()
        }
    }

    private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        var line = ""
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= maxWidth) line = candidate
            else {
                if (line.isNotEmpty()) result.add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) result.add(line)
        return result
    }

    private fun drawHeader(ctx: Ctx, zettel: Stundenzettel) {
        val canvas = ctx.canvas!!
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 70f, Paint().apply { color = TEAL })
        canvas.drawText("Stundenzettel / Leistungsnachweis", MARGIN, 34f, paint(19f, Color.WHITE, bold = true))
        canvas.drawText("TV-Wartung Freenet-Empfangsgeräte", MARGIN, 54f, paint(10f, Color.WHITE))
        drawLogo(canvas, zettel.logo, 70f)
        ctx.y = 90f

        // Auftraggeber-/Station-Block
        val boxH = 70f
        canvas.drawRoundRect(RectF(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y + boxH), 8f, 8f,
            Paint().apply { color = TEAL_LIGHT })
        val label = paint(8.5f, GRAY)
        val value = paint(11f, Color.BLACK, bold = true)
        val col2 = MARGIN + CONTENT_W / 2
        canvas.drawText("Auftraggeber", MARGIN + 12f, ctx.y + 18f, label)
        canvas.drawText(AUFTRAGGEBER_1, MARGIN + 12f, ctx.y + 33f, value)
        canvas.drawText(AUFTRAGGEBER_2, MARGIN + 12f, ctx.y + 48f, paint(9.5f, Color.BLACK))
        canvas.drawText(
            "Station" + if (zettel.auftragsnummer.isNotBlank()) " · Auftrag ${zettel.auftragsnummer}" else "",
            col2 + 12f, ctx.y + 18f, label
        )
        canvas.drawText(zettel.station, col2 + 12f, ctx.y + 33f, value)
        canvas.drawText("Zeitraum: ${zettel.zeitraum}", col2 + 12f, ctx.y + 48f, paint(9.5f, Color.BLACK))
        ctx.y += boxH + 20f
    }

    /** Logo rechts im Kopfbalken auf weißer Fläche. */
    private fun drawLogo(canvas: Canvas, logo: Bitmap?, headerH: Float) {
        if (logo == null || logo.width <= 0 || logo.height <= 0) return
        val boxH = headerH - 20f
        val maxW = 130f
        val scale = minOf(maxW / logo.width, (boxH - 12f) / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        val boxW = w + 16f
        val right = PAGE_W - 14f
        val top = (headerH - boxH) / 2
        canvas.drawRoundRect(
            RectF(right - boxW, top, right, top + boxH), 6f, 6f,
            Paint().apply { isAntiAlias = true; color = Color.WHITE }
        )
        canvas.drawBitmap(
            logo, null,
            RectF(right - boxW + 8f, top + (boxH - h) / 2, right - boxW + 8f + w, top + (boxH + h) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawZeiten(ctx: Ctx, zettel: Stundenzettel) {
        val canvas = ctx.canvas!!
        val boxH = 44f
        canvas.drawRoundRect(RectF(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y + boxH), 8f, 8f,
            Paint().apply { color = ROW_ALT })
        val label = paint(8.5f, GRAY)
        val value = paint(10.5f, Color.BLACK, bold = true)
        val cellW = CONTENT_W / 4
        val felder = listOf(
            "Datum" to zettel.datum,
            "Arbeitsstunden" to zettel.arbeitsstunden,
            "Anfahrt" to zettel.anfahrt,
            "Techniker" to zettel.techniker
        )
        felder.forEachIndexed { i, (l, v) ->
            val x = MARGIN + i * cellW + 12f
            canvas.drawText(l, x, ctx.y + 17f, label)
            canvas.drawText(v.ifBlank { "–" }, x, ctx.y + 32f, value)
        }
        ctx.y += boxH + 18f
    }

    private fun drawLeistungen(ctx: Ctx, zettel: Stundenzettel) {
        val canvas = ctx.canvas!!
        canvas.drawText("Durchgeführte Leistungen", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
        ctx.y += 16f

        val zimmerX = MARGIN + 4f
        val datumX = MARGIN + 70f
        val arbeitX = MARGIN + 150f
        val arbeitW = MARGIN + CONTENT_W - arbeitX - 4f
        val header = paint(9f, GRAY, bold = true)

        fun kopf() {
            val c = ctx.canvas!!
            c.drawText("Zimmer", zimmerX, ctx.y + 11f, header)
            c.drawText("Datum", datumX, ctx.y + 11f, header)
            c.drawText("Arbeiten / verbautes Material", arbeitX, ctx.y + 11f, header)
            ctx.y += 16f
            c.drawLine(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y, Paint().apply { color = LINE })
        }
        kopf()

        val body = paint(9.5f, Color.BLACK)
        val bodyBold = paint(9.5f, Color.BLACK, bold = true)
        if (zettel.leistungen.isEmpty()) {
            ctx.y += 6f
            ctx.canvas!!.drawText(
                "Keine Prüfungen im gewählten Zeitraum erfasst.", zimmerX, ctx.y + 10f,
                paint(9.5f, GRAY)
            )
            ctx.y += 20f
            return
        }

        zettel.leistungen.forEachIndexed { index, l ->
            val text = if (l.arbeiten.isEmpty()) "TV überprüft"
            else "TV überprüft; " + l.arbeiten.joinToString(", ")
            val lines = wrap(text, body, arbeitW)
            val rowH = maxOf(18f, 6f + lines.size * 12f)
            if (ctx.y + rowH > PAGE_H - 40f) {
                ctx.newPage()
                kopf()
            }
            val c = ctx.canvas!!
            if (index % 2 == 0) {
                c.drawRect(MARGIN, ctx.y, MARGIN + CONTENT_W, ctx.y + rowH, Paint().apply { color = ROW_ALT })
            }
            c.drawText(l.zimmer, zimmerX, ctx.y + 13f, bodyBold)
            c.drawText(Dates.isoToGerman(l.datum), datumX, ctx.y + 13f, body)
            lines.forEachIndexed { i, line ->
                c.drawText(line, arbeitX, ctx.y + 13f + i * 12f, body)
            }
            ctx.y += rowH
        }
        ctx.y += 16f
    }

    private fun drawMaterial(ctx: Ctx, zettel: Stundenzettel) {
        if (zettel.material.isEmpty()) return
        ctx.ensure(30f + zettel.material.size * 14f)
        val c = ctx.canvas!!
        c.drawText("Materialnachweis / Zusammenfassung", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
        ctx.y += 18f
        val body = paint(10f, Color.BLACK)
        zettel.material.forEach { (bez, anzahl) ->
            ctx.ensure(14f)
            val cc = ctx.canvas!!
            cc.drawText("•  $bez", MARGIN + 6f, ctx.y + 10f, body)
            cc.drawText("${anzahl}×", MARGIN + CONTENT_W - 40f, ctx.y + 10f, paint(10f, Color.BLACK, bold = true))
            ctx.y += 14f
        }
        ctx.y += 16f
    }

    private fun drawUnterschriften(ctx: Ctx, zettel: Stundenzettel) {
        ctx.ensure(130f)
        val c = ctx.canvas!!
        c.drawText("Bestätigung", MARGIN, ctx.y + 4f, paint(13f, TEAL, bold = true))
        ctx.y += 20f

        val gap = 30f
        val colW = (CONTENT_W - gap) / 2
        val sigTop = ctx.y
        val sigH = 50f
        val lineY = sigTop + sigH
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        val label = paint(9f, GRAY)
        val sub = paint(8f, GRAY)
        val x2 = MARGIN + colW + gap

        fun sig(bmp: Bitmap?, x: Float) {
            if (bmp == null || bmp.width <= 0 || bmp.height <= 0) return
            val scale = minOf(colW / bmp.width, sigH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            // linksbündig, unten auf der Linie aufsitzend
            c.drawBitmap(bmp, null, RectF(x, lineY - h, x + w, lineY), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        sig(zettel.signaturStation, MARGIN)
        sig(zettel.signaturTechniker, x2)

        // Station
        c.drawLine(MARGIN, lineY, MARGIN + colW, lineY, linePaint)
        c.drawText("Unterschrift Station", MARGIN, lineY + 14f, label)
        c.drawText("Datum, Name, Stempel", MARGIN, lineY + 26f, sub)

        // Dienstleister / Techniker
        c.drawLine(x2, lineY, x2 + colW, lineY, linePaint)
        c.drawText("Unterschrift Dienstleister", x2, lineY + 14f, label)
        c.drawText(zettel.techniker.ifBlank { "Datum, Name" }, x2, lineY + 26f, sub)

        ctx.y = lineY + 40f
    }
}
