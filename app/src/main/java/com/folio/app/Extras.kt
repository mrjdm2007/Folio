package com.folio.app

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val glassDialog = Color(0xFF202327).copy(alpha = 0.95f)

// ---------------- page templates ----------------
private fun tb(type: String, text: String = "", extra: String = "") = Triple(type, text, extra)

val TEMPLATES: Map<String, List<Triple<String, String, String>>> = linkedMapOf(
    "Lecture notes" to listOf(tb("h2", "Objectives"), tb("bullet"), tb("h2", "Key ideas"), tb("bullet"), tb("h2", "Equations"), tb("latex"),
        tb("h2", "Questions to ask"), tb("check"), tb("h2", "Summary"), tb("text")),
    "Problem set" to listOf(tb("h2", "Problem"), tb("text"), tb("h2", "Given"), tb("bullet"), tb("h2", "Find"), tb("bullet"),
        tb("h2", "Solution"), tb("latex"), tb("text"), tb("h2", "Check the answer"), tb("text")),
    "Cornell notes" to listOf(tb("h2", "Cues and questions"), tb("bullet"), tb("h2", "Notes"), tb("text"), tb("h2", "Summary"), tb("text")),
    "Formula sheet" to listOf(tb("h1", "Formula sheet"), tb("table", "Name | Formula | When to use\n | | "), tb("latex")),
    "Exam revision" to listOf(tb("h2", "Topics to revise"), tb("check"), tb("h2", "Weak areas"), tb("bullet"),
        tb("h2", "Practice questions"), tb("quiz"), tb("flash"))
)

fun templateBlocks(name: String): List<Block> = TEMPLATES[name].orEmpty().map { (t, x, e) -> Block(type = t, text = x, extra = e) }

@Composable fun TemplateDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, containerColor = glassDialog, shape = RoundedCornerShape(24.dp), title = { Text("Insert a template") },
        text = {
            Column {
                Text("Adds the structure to the end of this page. Your existing notes stay.", fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
                TEMPLATES.keys.forEach { name -> Chip(name) { onPick(name); onClose() } }
            }
        }, confirmButton = { TextButton(onClose) { Text("Cancel") } })
}

// ---------------- question (quiz) blocks ----------------
/** Options are one per line; the correct one starts with *. Returns (options, index of the correct one). */
fun quizOptions(b: Block): Pair<List<String>, Int> {
    val lines = b.extra.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return lines.map { it.removePrefix("*").trim() } to lines.indexOfFirst { it.startsWith("*") }.coerceAtLeast(0)
}

@Composable fun QuizReader(b: Block, style: TextStyle, fg: Color) {
    var show by remember { mutableStateOf(false) }
    val (opts, ok) = quizOptions(b)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.06f)).clickable { show = !show }.padding(12.dp)) {
        Text(b.text, style = style.copy(fontWeight = FontWeight.SemiBold))
        opts.forEachIndexed { i, o ->
            val right = show && i == ok
            Text(("ABCDEFGH".getOrNull(i) ?: '•').toString() + ".  " + o + (if (right) "   ✓" else ""), style = style.copy(color = if (right) Gold else style.color))
        }
        Text(if (show) "Tap to hide the answer" else "Tap to reveal the answer", style = style.copy(fontSize = (style.fontSize.value * 0.7f).sp, color = fg.copy(alpha = 0.5f)))
    }
}

// ---------------- formula picker ----------------
@Composable fun FormulaPicker(s: Store, onPick: (Formula) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, containerColor = glassDialog, shape = RoundedCornerShape(24.dp), title = { Text("Insert a formula") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                val list = s.data.formulas.filter { q.isBlank() || it.name.contains(q, true) || it.latex.contains(q, true) }
                if (list.isEmpty()) Text("No saved formulas yet. Add some in Tools → Formulas.", color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
                list.forEach { f ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(f); onClose() }.padding(vertical = 8.dp)) {
                        Text(f.name, fontWeight = FontWeight.Medium)
                        Text(f.latex, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Gold, maxLines = 1)
                    }
                }
            }
        }, confirmButton = { TextButton(onClose) { Text("Close") } })
}

// ---------------- sketch pad (draw a diagram with your finger, saved as an image) ----------------
private data class SketchStroke(val pts: List<Offset>, val color: Color, val width: Float)

private fun pathOf(p: List<Offset>) = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1 until p.size) lineTo(p[i].x, p[i].y) }

private fun renderSketch(strokes: List<SketchStroke>, size: IntSize): Bitmap {
    val bmp = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp); c.drawColor(0xFFF6F1E7.toInt())
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { st ->
        paint.color = st.color.toArgb(); paint.strokeWidth = st.width
        val path = android.graphics.Path(); path.moveTo(st.pts[0].x, st.pts[0].y)
        for (i in 1 until st.pts.size) path.lineTo(st.pts[i].x, st.pts[i].y)
        c.drawPath(path, paint)
    }
    return bmp
}

@Composable fun SketchDialog(onSave: (Bitmap) -> Unit, onClose: () -> Unit) {
    val colors = listOf(Color(0xFF222222), Color(0xFFC62828), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFB8860B))
    var color by remember { mutableStateOf(colors[0]) }
    var width by remember { mutableFloatStateOf(5f) }
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Graphite) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(Icons.Filled.Close, "Cancel", onClick = onClose)
                    Text("Sketch", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Ico(Icons.Filled.Undo, "Undo last stroke") { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
                    Ico(Icons.Filled.Delete, "Clear all") { strokes.clear() }
                    TextButton({ onSave(renderSketch(strokes.toList(), size)); onClose() }, enabled = strokes.isNotEmpty()) { Text("Add to page", color = Gold) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    colors.forEach { c ->
                        Box(Modifier.padding(6.dp).size(28.dp).clip(CircleShape).background(c)
                            .border(if (c == color) 2.dp else 0.dp, Gold, CircleShape).clickable { color = c })
                    }
                    Slider(width, { width = it }, valueRange = 2f..16f, modifier = Modifier.width(150.dp))
                }
                Canvas(Modifier.weight(1f).fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF6F1E7))
                    .onSizeChanged { size = it }
                    .pointerInput(color, width) {
                        detectDragGestures(
                            onDragStart = { live = listOf(it) },
                            onDragEnd = { if (live.size > 1) strokes.add(SketchStroke(live, color, width)); live = emptyList() },
                            onDragCancel = { live = emptyList() }
                        ) { ch, _ -> ch.consume(); live = live + ch.position }
                    }) {
                    val all = strokes.toList() + (if (live.size > 1) listOf(SketchStroke(live, color, width)) else emptyList())
                    all.forEach { st -> drawPath(pathOf(st.pts), st.color, style = Stroke(st.width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
        }
    }
}

// ---------------- read aloud ----------------
class Tts(ctx: Context) {
    var speaking by mutableStateOf(false); private set
    private val engine: TextToSpeech = TextToSpeech(ctx.applicationContext) { }
    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == "last") speaking = false }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = false }
        })
    }
    fun speak(chunks: List<String>) {
        if (chunks.isEmpty()) return
        speaking = true
        chunks.forEachIndexed { i, c ->
            engine.speak(c.take(3500), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) "last" else "c$i")
        }
    }
    fun stop() { engine.stop(); speaking = false }
    fun shutdown() { engine.stop(); engine.shutdown() }
}

/** The page as a list of paragraphs to speak: no equations, code or files. */
fun readableText(n: Node): List<String> {
    fun clean(t: String) = t.replace("**", "").replace("==", "").replace("`", "").replace("[[", "").replace("]]", "").replace("{{", "").replace("}}", "").replace("*", "").trim()
    return listOf(n.title) + n.blocks.mapNotNull { b ->
        val r = when (b.type) {
            "latex", "code", "image", "file", "table" -> null
            "flash" -> "Question. ${clean(b.text)}. Answer. ${clean(b.extra)}"
            else -> clean(b.text)
        }
        if (r.isNullOrBlank()) null else r
    }
}

// ---------------- study chart ----------------
@Composable fun WeekChart(d: Data) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val mins = days.map { day -> d.sessions.filter { dayOf(it.time) == day }.sumOf { it.minutes } }
    val top = maxOf(mins.maxOrNull() ?: 0, d.dailyGoal / 2, 1)
    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("LAST 7 DAYS", color = Gold, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((70f * mins[i] / top).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(4.dp))
                            .background(if (mins[i] >= d.dailyGoal) Gold else Gold.copy(alpha = 0.45f)))
                        Text(day.dayOfWeek.name.take(1), fontSize = 11.sp, color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------- Markdown export ----------------
private fun mdTable(t: String): String {
    val rows = t.lines().filter { it.isNotBlank() }.map { l -> l.split("|").map { it.trim() } }
    if (rows.isEmpty()) return ""
    val w = rows.maxOf { it.size }
    fun row(r: List<String>) = "| " + (r + List(w - r.size) { "" }).joinToString(" | ") + " |"
    return (listOf(row(rows[0]), "|" + " --- |".repeat(w)) + rows.drop(1).map { row(it) }).joinToString("\n")
}

fun pageToMarkdown(n: Node, prefix: String = "attachments/"): String {
    val sb = StringBuilder("# ${n.title}\n\n")
    if (n.tags.isNotEmpty()) sb.append(n.tags.joinToString(" ") { "#$it" }).append("\n\n")
    n.blocks.forEachIndexed { i, b ->
        val pad = "  ".repeat(b.indent)
        val line = when (b.type) {
            "h1" -> "## ${b.text}"
            "h2" -> "### ${b.text}"
            "bullet" -> "$pad- ${b.text}"
            "number" -> "$pad${numberOf(n.blocks, i)}. ${b.text}"
            "check" -> "$pad- [${if (b.checked) "x" else " "}] ${b.text}"
            "quote" -> b.text.lines().joinToString("\n") { "> $it" }
            "code" -> "```\n${b.text}\n```"
            "latex" -> "\$\$\n${b.text}\n\$\$"
            "table" -> mdTable(b.text)
            "image" -> "![${b.extra}](${prefix}${b.text})"
            "file" -> "[${b.extra}](${prefix}${b.text})"
            "cloze" -> b.text.replace("{{", "**").replace("}}", "**")
            "flash" -> "**Q:** ${b.text}\n\n**A:** ${b.extra}"
            "quiz" -> { val (o, ok) = quizOptions(b); "**Question:** ${b.text}\n\n" + o.mapIndexed { k, x -> "- $x" + if (k == ok) " (correct)" else "" }.joinToString("\n") }
            else -> b.text
        }
        val tight = b.type in listOf("bullet", "number", "check") && n.blocks.getOrNull(i + 1)?.type == b.type
        sb.append(line).append(if (tight) "\n" else "\n\n")
    }
    return sb.toString()
}

/** Whole notebook as a .zip of Markdown files in course / chapter / topic folders, with attachments alongside. */
fun exportNotebookZip(s: Store, out: OutputStream) {
    ZipOutputStream(out.buffered()).use { z ->
        val used = HashSet<String>(); val files = LinkedHashSet<String>()
        fun safe(x: String) = x.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "untitled" }
        s.data.nodes.filter { n -> !n.deleted && (n.kind == "page" || n.blocks.any { b -> b.text.isNotBlank() || b.type == "image" }) }.forEach { n ->
            val segs = s.path(n.id).split(" › ").map { safe(it) }
            var p = "Folio/" + segs.joinToString("/"); var k = 2
            while (!used.add(p)) { p = "Folio/" + segs.joinToString("/") + " ($k)"; k++ }
            z.putNextEntry(ZipEntry("$p.md"))
            z.write(pageToMarkdown(n, "../".repeat(segs.size - 1) + "attachments/").toByteArray())
            z.closeEntry()
            n.blocks.filter { it.type == "image" || it.type == "file" }.forEach { files.add(it.text) }
        }
        files.forEach { name ->
            val f = s.attachmentFile(name)
            if (f.exists()) { z.putNextEntry(ZipEntry("Folio/attachments/$name")); z.write(f.readBytes()); z.closeEntry() }
        }
    }
}
