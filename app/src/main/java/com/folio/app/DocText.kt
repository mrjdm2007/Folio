package com.folio.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class DocPart(val title: String, val lines: List<String>)

private fun entries(f: File, want: (String) -> Boolean): Map<String, ByteArray> {
    val m = LinkedHashMap<String, ByteArray>()
    ZipInputStream(f.inputStream().buffered()).use { z ->
        var e = z.nextEntry
        while (e != null) { if (want(e.name)) m[e.name] = z.readBytes(); e = z.nextEntry }
    }
    return m
}

private fun xml(b: ByteArray): XmlPullParser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), null) }

fun readDoc(f: File, ext: String): List<DocPart> = when (ext) {
    "docx" -> docx(entries(f) { it == "word/document.xml" }["word/document.xml"] ?: error("Not a valid .docx file"))
    "pptx" -> pptx(f)
    "xlsx" -> xlsx(f)
    else -> listOf(DocPart("", f.readText().lines()))
}

private fun docx(b: ByteArray): List<DocPart> {
    val p = xml(b); val lines = mutableListOf<String>()
    val para = StringBuilder(); val cell = StringBuilder(); val row = mutableListOf<String>()
    var style = ""; var inT = false; var depth = 0
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> when (p.name) {
                "w:p" -> { para.setLength(0); style = "" }
                "w:pStyle" -> style = p.getAttributeValue(null, "w:val") ?: ""
                "w:t" -> inT = true
                "w:tab" -> para.append("  ")
                "w:tbl" -> depth++
                "w:tr" -> row.clear()
            }
            XmlPullParser.TEXT -> if (inT) para.append(p.text)
            XmlPullParser.END_TAG -> when (p.name) {
                "w:t" -> inT = false
                "w:p" -> {
                    val t = para.toString()
                    if (depth > 0) { if (cell.isNotEmpty() && t.isNotEmpty()) cell.append(" "); cell.append(t) }
                    else if (t.isNotBlank()) lines.add(if (style.startsWith("Heading") || style == "Title") "#$t" else t)
                }
                "w:tc" -> { row.add(cell.toString()); cell.setLength(0) }
                "w:tr" -> lines.add(row.joinToString(" | "))
                "w:tbl" -> depth--
            }
        }
        ev = p.next()
    }
    return listOf(DocPart("", lines))
}

private fun pptx(f: File): List<DocPart> {
    val slideRx = Regex("ppt/slides/slide\\d+\\.xml")
    val m = entries(f) { slideRx.matches(it) }
    if (m.isEmpty()) error("Not a valid .pptx file")
    return m.entries.sortedBy { e -> e.key.filter { c -> c.isDigit() }.toInt() }.mapIndexed { i, e ->
        val p = xml(e.value); val lines = mutableListOf<String>(); val para = StringBuilder(); var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) { "a:p" -> para.setLength(0); "a:t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) para.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "a:t" -> inT = false
                    "a:p" -> if (para.isNotBlank()) lines.add(para.toString())
                }
            }
            ev = p.next()
        }
        DocPart("Slide ${i + 1}", lines)
    }
}

private fun xlsx(f: File): List<DocPart> {
    val sheetRx = Regex("xl/worksheets/sheet\\d+\\.xml")
    val m = entries(f) { it == "xl/sharedStrings.xml" || sheetRx.matches(it) }
    val shared = mutableListOf<String>()
    m["xl/sharedStrings.xml"]?.let { b ->
        val p = xml(b); var sb = StringBuilder(); var inT = false; var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> sb = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> shared.add(sb.toString()) }
            }
            ev = p.next()
        }
    }
    val sheets = m.entries.filter { sheetRx.matches(it.key) }.sortedBy { e -> e.key.filter { c -> c.isDigit() }.toInt() }
    if (sheets.isEmpty()) error("Not a valid .xlsx file")
    return sheets.mapIndexed { i, e ->
        val p = xml(e.value); val lines = mutableListOf<String>(); val row = mutableListOf<String>()
        var type = ""; var inV = false; var inT = false; var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && lines.size < 500) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row.clear()
                    "c" -> type = p.getAttributeValue(null, "t") ?: ""
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> {
                    if (inV) row.add(if (type == "s") shared.getOrElse(p.text.trim().toIntOrNull() ?: -1) { "" } else p.text)
                    else if (inT) row.add(p.text)
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "row" -> if (row.any { it.isNotBlank() }) lines.add(row.joinToString(" | "))
                }
            }
            ev = p.next()
        }
        DocPart("Sheet ${i + 1}", lines)
    }
}
