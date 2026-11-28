package com.folio.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val dialogBg = Color(0xFF202327).copy(alpha = 0.95f)

// ---------------- task planner ----------------
@Composable private fun TodoRow(t: Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(t.done, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Graphite))
        Column(Modifier.weight(1f)) {
            Text(t.text, textDecoration = if (t.done) TextDecoration.LineThrough else null, color = if (t.done) Ink.copy(alpha = 0.5f) else Ink)
            val meta = listOfNotNull(t.subject.ifBlank { null }, if (t.due > 0) LocalDate.ofEpochDay(t.due).toString() else null).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = Gold.copy(alpha = 0.8f))
        }
        Chip("✕", onClick = onDelete)
    }
}

@Composable fun Planner(s: Store) {
    var dlg by remember { mutableStateOf(false) }
    val todos = s.data.todos
    val today = LocalDate.now().toEpochDay()
    fun toggle(id: String) = s.update { d -> d.copy(todos = d.todos.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun remove(id: String) = s.update { d -> d.copy(todos = d.todos.filter { it.id != id }) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Chip("+ Task") { dlg = true }
        val open = todos.filter { !it.done }
        val groups = listOf(
            "Overdue" to open.filter { it.due in 1 until today },
            "Today" to open.filter { it.due == today },
            "Upcoming" to open.filter { it.due > today }.sortedBy { it.due },
            "Anytime" to open.filter { it.due == 0L })
        groups.forEach { (title, list) ->
            if (list.isNotEmpty()) { SectionTitle(title); list.forEach { t -> TodoRow(t, { toggle(t.id) }, { remove(t.id) }) } }
        }
        if (open.isEmpty()) Text("Nothing planned. Tap “+ Task” to add something.", color = Ink.copy(alpha = 0.6f))
        val done = todos.filter { it.done }
        if (done.isNotEmpty()) {
            SectionTitle("Done (${done.size})")
            done.takeLast(8).reversed().forEach { t -> TodoRow(t, { toggle(t.id) }, { remove(t.id) }) }
            Chip("Clear completed") { s.update { d -> d.copy(todos = d.todos.filter { !it.done }) } }
        }
    }
    if (dlg) FormDialog("New task", listOf("Task", "Due date (YYYY-MM-DD, blank = anytime)", "Subject"), onOk = { v ->
        if (v[0].isNotBlank()) {
            val due = runCatching { LocalDate.parse(v[1].trim()).toEpochDay() }.getOrDefault(0L)
            s.update { d -> d.copy(todos = d.todos + Todo(text = v[0].trim(), due = due, subject = v[2].trim())) }
        }
    }, onClose = { dlg = false })
}

// ---------------- cloze (fill in the blank) ----------------
/** Shows a note with {{hidden}} parts; tap it to reveal or hide the answers. */
@Composable fun ClozeReader(b: Block, style: TextStyle, fg: Color) {
    var show by remember { mutableStateOf(false) }
    val rx = Regex("\\{\\{(.+?)\\}\\}")
    val text = buildAnnotatedString {
        var last = 0
        rx.findAll(b.text).forEach { m ->
            append(b.text.substring(last, m.range.first))
            if (show) withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            else withStyle(SpanStyle(color = Gold.copy(alpha = 0.6f), background = Gold.copy(alpha = 0.15f))) { append("  […]  ") }
            last = m.range.last + 1
        }
        append(b.text.substring(last))
    }
    Text(text, style = style, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.04f)).clickable { show = !show }.padding(8.dp))
}

// ---------------- outline ----------------
@Composable fun OutlineDialog(blocks: List<Block>, onGo: (Int) -> Unit, onClose: () -> Unit) {
    val heads = blocks.withIndex().filter { (_, b) -> (b.type == "h1" || b.type == "h2") && b.text.isNotBlank() }
    AlertDialog(onDismissRequest = onClose, containerColor = dialogBg, shape = RoundedCornerShape(24.dp), title = { Text("Outline") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (heads.isEmpty()) Text("No headings yet. Add Title or Heading blocks to build an outline.", color = Ink.copy(alpha = 0.6f))
                heads.forEach { (i, b) ->
                    Text(b.text, Modifier.fillMaxWidth().clickable { onGo(i); onClose() }.padding(start = if (b.type == "h2") 16.dp else 0.dp, top = 8.dp, bottom = 8.dp),
                        fontWeight = if (b.type == "h1") FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                }
            }
        }, confirmButton = { TextButton(onClose) { Text("Close") } })
}

// ---------------- bulk import ----------------
/** Paste-in dialog. onOk returns how many items were added so a confirmation can be shown. */
@Composable fun ImportDialog(title: String, firstLabel: String, firstInit: String, hint: String, onOk: (String, String) -> Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var first by remember { mutableStateOf(firstInit) }
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, containerColor = dialogBg, shape = RoundedCornerShape(24.dp), title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Text(hint, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                OutlinedTextField(text, { text = it }, minLines = 6, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ val n = onOk(first, text); Toast.makeText(ctx, "$n imported", Toast.LENGTH_SHORT).show(); onClose() }) { Text("Import") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } })
}

/** One question per paragraph: question line, option lines (* marks the correct one), optional "Why: ..." line. */
fun parseQuestions(text: String, subject: String): List<Question> =
    text.split(Regex("\\n\\s*\\n")).mapNotNull { blk ->
        val lines = blk.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3) return@mapNotNull null
        var expl = ""
        val q = lines[0].removePrefix("Q:").removePrefix("q:").trim()
        val opts = mutableListOf<String>(); var correct = 0
        lines.drop(1).forEach { l ->
            if (l.startsWith("Why:", true) || l.startsWith("Explanation:", true)) expl = l.substringAfter(":").trim()
            else {
                var o = l; val star = o.startsWith("*")
                if (star) o = o.removePrefix("*").trim()
                o = o.replace(Regex("^[A-Ha-h][).:]\\s+"), "").replace(Regex("^[-•]\\s+"), "")
                if (star) correct = opts.size
                opts.add(o)
            }
        }
        if (opts.size < 2) null else Question(subject = subject.ifBlank { "General" }, text = q, options = opts, correct = correct, explanation = expl)
    }

/** One card per line: front, then a Tab, semicolon or " | ", then back. */
fun parseCards(text: String): List<Pair<String, String>> = text.lines().mapNotNull { l ->
    val parts = when {
        l.contains('\t') -> l.split('\t', limit = 2)
        l.contains(" | ") -> l.split(" | ", limit = 2)
        l.contains(';') -> l.split(';', limit = 2)
        else -> return@mapNotNull null
    }
    val f = parts[0].trim(); val b = parts[1].trim()
    if (f.isEmpty() || b.isEmpty()) null else f to b
}

// ---------------- where the study time goes ----------------
@Composable fun SubjectBreakdown(d: Data) {
    val since = LocalDate.now().minusDays(29)
    val by = d.sessions.filter { dayOf(it.time) >= since }.groupBy { it.subject.ifBlank { "Unlabelled" } }
        .mapValues { e -> e.value.sumOf { it.minutes } }.entries.sortedByDescending { it.value }.take(6)
    if (by.isNotEmpty()) GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TIME BY SUBJECT · 30 DAYS", color = Gold, fontSize = 11.sp, letterSpacing = 1.sp)
            val top = by.first().value.coerceAtLeast(1)
            by.forEach { (name, m) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.width(110.dp), fontSize = 13.sp, maxLines = 1)
                    Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(m / top.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gold)) }
                    Text("  $m min", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
                }
            }
        }
    }
}
