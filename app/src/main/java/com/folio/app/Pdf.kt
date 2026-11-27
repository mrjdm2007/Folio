package com.folio.app

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Exports one notebook page as a simple PDF (text, headings, lists, code, images). Equations are written as LaTeX source. */
fun exportPagePdf(s: Store, n: Node, out: OutputStream) {
    val W = 595; val H = 842; val M = 40; val tw = W - 2 * M
    val doc = PdfDocument()
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
    var y = M.toFloat()
    fun newPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); y = M.toFloat() }
    fun text(str: String, size: Float, style: Int = Typeface.NORMAL, mono: Boolean = false, indent: Int = 0) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; color = Color.BLACK
            typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SERIF, style)
        }
        val lay = StaticLayout.Builder.obtain(str, 0, str.length, tp, tw - indent).build()
        if (y + lay.height > H - M) newPage()
        val c = page.canvas; c.save(); c.translate((M + indent).toFloat(), y); lay.draw(c); c.restore()
        y += lay.height + 8
    }
    text(n.title, 24f, Typeface.BOLD)
    n.blocks.forEachIndexed { bi, b ->
        when (b.type) {
            "h1" -> text(b.text, 20f, Typeface.BOLD)
            "h2" -> text(b.text, 16f, Typeface.BOLD)
            "bullet" -> text("• " + b.text, 12f, indent = 12 + b.indent * 16)
            "number" -> text("${numberOf(n.blocks, bi)}. " + b.text, 12f, indent = 12 + b.indent * 16)
            "check" -> text((if (b.checked) "[x] " else "[ ] ") + b.text, 12f, indent = 12 + b.indent * 16)
            "quote" -> text(b.text, 12f, Typeface.ITALIC, indent = 20)
            "code" -> text(b.text, 10f, mono = true, indent = 8)
            "latex" -> text("$$ " + b.text + " $$", 11f, mono = true, indent = 8)
            "table" -> text(b.text.replace("|", "  |  "), 11f, mono = true, indent = 8)
            "flash" -> text("Q: ${b.text}\nA: ${b.extra}", 12f, Typeface.BOLD)
            "file" -> text("[Attachment] ${b.extra}", 11f, Typeface.ITALIC)
            "image" -> loadBitmap(s.attachmentFile(b.text), 1000)?.let { bmp ->
                var w = tw.toFloat().coerceAtMost(bmp.width.toFloat()); var h = w * bmp.height / bmp.width
                val maxH = (H - 2 * M).toFloat()
                if (h > maxH) { w *= maxH / h; h = maxH }
                if (y + h > H - M) newPage()
                page.canvas.drawBitmap(bmp, null, RectF(M.toFloat(), y, M + w, y + h), null)
                y += h + 8
                if (b.extra.isNotBlank()) text(b.extra, 10f, Typeface.ITALIC)
            }
            else -> text(b.text, 12f)
        }
    }
    doc.finishPage(page); doc.writeTo(out); doc.close()
}
