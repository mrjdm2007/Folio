package com.folio.app

import android.media.RingtoneManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun dayOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
fun todayMin(d: Data) = d.sessions.filter { dayOf(it.time) == LocalDate.now() }.sumOf { it.minutes }
fun weekMin(d: Data) = d.sessions.filter { dayOf(it.time) > LocalDate.now().minusDays(7) }.sumOf { it.minutes }
fun streak(d: Data): Int {
    val days = d.sessions.map { dayOf(it.time) }.toSet()
    var day = LocalDate.now(); if (day !in days) day = day.minusDays(1)
    var c = 0; while (day in days) { c++; day = day.minusDays(1) }
    return c
}

@Composable fun StudyScreen(s: Store, open: (String) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Overview", "Cards", "Practice", "Exams", "Focus", "Plan").forEachIndexed { i, t -> Chip(t, sub == i) { sub = i } }
        }
        when (sub) { 0 -> Overview(s, open); 1 -> Cards(s); 2 -> Practice(s); 3 -> Exams(s); 4 -> Focus(s); else -> Planner(s) }
    }
}

@Composable fun Overview(s: Store, open: (String) -> Unit) {
    val d = s.data
    var goal by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Today", "${todayMin(d)} / ${d.dailyGoal} min", Modifier.weight(1f))
            StatTile("This week", "${weekMin(d)} min", Modifier.weight(1f))
            StatTile("Streak", "${streak(d)} days", Modifier.weight(1f))
        }
        GoldProgress({ (todayMin(d) / d.dailyGoal.toFloat()).coerceIn(0f, 1f) })
        WeekChart(d)
        StudyHeatmap(d)
        SubjectBreakdown(d)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Cards due", "${d.cards.count { it.due <= now() }}", Modifier.weight(1f))
            StatTile("Sessions", "${d.sessions.size}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Reviewed today", "${d.reviewLog[LocalDate.now().toString()] ?: 0}", Modifier.weight(1f))
            StatTile("Mastered cards", "${d.cards.count { it.interval >= 21 }}", Modifier.weight(1f))
            StatTile("This visit", "${d.sessions.filter { it.time >= s.appStart }.sumOf { it.minutes }} min", Modifier.weight(1f))
        }
        Chip("Change daily goal") { goal = true }
        val todayN = LocalDate.now().toEpochDay()
        val dueTasks = d.todos.filter { !it.done && it.due in 1..todayN }
        if (dueTasks.isNotEmpty()) {
            SectionTitle("Tasks due")
            dueTasks.take(6).forEach { Text("• ${it.text}" + (if (it.due < todayN) "  (overdue)" else ""), fontSize = 14.sp) }
        }
        val dueRev = s.dueRevisions()
        if (dueRev.isNotEmpty()) {
            SectionTitle("Revise today")
            dueRev.take(8).forEach { n -> Text(s.path(n.id), Modifier.fillMaxWidth().clickable { open(n.id) }.padding(vertical = 4.dp), color = Gold) }
        }
        SectionTitle("Upcoming exams")
        val today = LocalDate.now().toEpochDay()
        d.exams.filter { it.date >= today }.sortedBy { it.date }.take(3).forEach { Text("${it.title} — ${it.date - today} days" + (if (it.tasks.isNotEmpty()) " · syllabus ${it.tasks.count { t -> t.done } * 100 / it.tasks.size}%" else "")) }
        SectionTitle("Recent focus sessions")
        d.sessions.takeLast(5).reversed().forEach { Text("${it.subject.ifBlank { "Study" }} · ${it.minutes} min · ${fmt(it.time)}") }
    }
    if (goal) FormDialog("Daily goal (minutes)", listOf("Minutes"), listOf(d.dailyGoal.toString()),
        { v -> v[0].toIntOrNull()?.takeIf { it > 0 }?.let { g -> s.update { x -> x.copy(dailyGoal = g) } } }) { goal = false }
}

@Composable fun Cards(s: Store) {
    val d = s.data
    var deck by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var cramStart by remember { mutableLongStateOf(0L) }
    val decks = d.cards.map { it.deck }.distinct()
    val inDeck = d.cards.filter { deck == null || it.deck == deck }
    val due = if (cramStart > 0) inDeck.filter { it.last < cramStart }.take(20) else inDeck.filter { it.due <= now() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Chip("All", deck == null) { deck = null }
            decks.forEach { Chip(it, deck == it) { deck = it } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Chip("+ Card") { dlg = "add" }
            Chip("Import list") { dlg = "import" }
            if (deck != null) {
                Chip("Rename / merge deck") { dlg = "rename" }
                Chip("Delete deck") { val dk = deck; s.update { x -> x.copy(cards = x.cards.filter { c -> c.deck != dk }) }; deck = null }
            }
        }
        val c = due.firstOrNull()
        if (c == null) Column {
            Text(if (cramStart > 0) "Cram session finished 🎉" else "Nothing due right now 🎉")
            if (cramStart > 0) Chip("Back to normal") { cramStart = 0L }
            else if (inDeck.isNotEmpty()) Chip("Review ahead (cram up to 20 cards)") { cramStart = now() }
        }
        else GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${due.size} due · ${c.deck}", color = Gold)
                Text(c.front, fontSize = 20.sp)
                if (!reveal) Button({ reveal = true }) { Text("Show answer") }
                else {
                    Text(c.back, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Again", "Hard", "Good", "Easy").forEachIndexed { i, t ->
                            Button({ s.rate(c.id, i); reveal = false }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(t, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        SectionTitle("All cards (due date)")
        inDeck.sortedBy { it.due }.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${if (f.lapses >= 4) "⚠ " else ""}${f.front.take(28)} — ${if (f.due <= now()) "due" else fmtDay(f.due)} (${f.interval}d)", Modifier.weight(1f), fontSize = 13.sp)
                Chip("✎") { editId = f.id; dlg = "edit" }
                Chip("✕") { s.update { x -> x.copy(cards = x.cards.filter { c2 -> c2.id != f.id }) } }
            }
        }
    }
    if (dlg == "import") ImportDialog("Import flashcards", "Deck", deck ?: "Default",
        "One card per line: the front, then a Tab, a semicolon or \" | \", then the back.",
        onOk = { dk, txt -> val l = parseCards(txt); l.forEach { (f, b) -> s.addCard(dk, f, b) }; l.size }, onClose = { dlg = "" })
    if (dlg == "add") FormDialog("New card", listOf("Deck", "Front", "Back"), listOf(deck ?: "Default", "", ""),
        { v -> s.addCard(v[0], v[1], v[2]) }) { dlg = "" }
    if (dlg == "rename") FormDialog("Rename / merge deck", listOf("New name (existing name merges)"), listOf(deck ?: ""),
        { v -> s.renameDeck(deck!!, v[0]); deck = v[0].ifBlank { deck } }) { dlg = "" }
    val ec = d.cards.firstOrNull { it.id == editId }
    if (dlg == "edit" && ec != null) FormDialog("Edit card", listOf("Deck", "Front", "Back"), listOf(ec.deck, ec.front, ec.back),
        { v -> s.update { x -> x.copy(cards = x.cards.map { c -> if (c.id == ec.id) c.copy(deck = v[0], front = v[1], back = v[2]) else c }) } }) { dlg = "" }
}

@Composable fun Practice(s: Store) {
    val qs = s.data.questions
    var queue by remember { mutableStateOf<List<Question>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var weak by remember { mutableStateOf(mapOf<String, Int>()) }
    var dlg by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var subject by remember { mutableStateOf<String?>(null) }
    var importQ by remember { mutableStateOf(false) }
    fun start(list: List<Question>) { queue = list.shuffled(); idx = 0; picked = null; score = 0; weak = emptyMap(); running = true }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!running) {
            val subjects = qs.map { it.subject }.distinct()
            val pool = qs.filter { subject == null || it.subject == subject }
            if (subjects.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
                Chip("All subjects", subject == null) { subject = null }
                subjects.forEach { sj -> Chip(sj, subject == sj) { subject = sj } }
            }
            Row {
                Button({ start(pool) }, enabled = pool.isNotEmpty()) { Text(if (subject == null) "Start mixed practice (${pool.size})" else "Practise $subject (${pool.size})") }
                Chip("+ Question") { editId = null; dlg = true }
                Chip("Import") { importQ = true }
            }
            if (importQ) ImportDialog("Import questions", "Subject", subject ?: "",
                "One question per paragraph, with a blank line between them. First line is the question, then one option per line with * before the correct one. Optional last line: Why: explanation.",
                onOk = { sj, txt ->
                    val l = parseQuestions(txt, sj)
                    s.update { d -> d.copy(questions = d.questions + l.filter { q -> d.questions.none { it.text == q.text } }) }
                    l.size
                }, onClose = { importQ = false })
            val stats = qs.groupBy { it.subject }.map { (sj, l) -> Triple(sj, l.sumOf { it.right }, l.sumOf { it.right + it.wrong }) }
                .filter { it.third > 0 }.sortedBy { it.second / it.third.toFloat() }
            if (stats.isNotEmpty()) {
                SectionTitle("Difficult topics")
                stats.forEach { (sj, ok, tot) -> Text("$sj: ${ok * 100 / tot}% correct ($ok of $tot)", fontSize = 13.sp, color = if (ok * 100 / tot < 60) Color(0xFFE57373) else Ink) }
                val weakPool = qs.filter { q -> stats.any { it.first == q.subject && it.second * 100 / it.third < 60 } }
                if (weakPool.isNotEmpty()) OutlinedButton({ start(weakPool) }) { Text("Practise my weak topics (${weakPool.size})") }
            }
            qs.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${q.subject}: ${q.text.take(40)}", Modifier.weight(1f), fontSize = 13.sp)
                    Chip("✎") { editId = q.id; dlg = true }
                    Chip("✕") { s.update { x -> x.copy(questions = x.questions.filter { it.id != q.id }) } }
                }
            }
        } else {
            val q = queue.getOrNull(idx)
            if (q == null) {
                Text("Finished: $score / ${queue.size}", style = MaterialTheme.typography.titleLarge)
                weak.entries.sortedByDescending { it.value }.forEach { Text("Review topic: ${it.key} (${it.value} missed)") }
                Row { Button({ start(queue) }) { Text("Restart") }; Chip("Close") { running = false } }
            } else {
                Text("Question ${idx + 1} / ${queue.size} · ${q.subject}", color = Gold)
                Text(q.text, fontSize = 18.sp)
                q.options.forEachIndexed { i, o ->
                    val col = when {
                        picked == null -> ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Ink)
                        i == q.correct -> ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
                        i == picked -> ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)
                        else -> ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Ink)
                    }
                    Button({
                        if (picked == null) {
                            picked = i
                            s.recordAnswer(q.id, i == q.correct)
                            if (i == q.correct) score++ else { val k = q.subject; weak = weak + (k to ((weak[k] ?: 0) + 1)) }
                        }
                    }, colors = col, modifier = Modifier.fillMaxWidth()) { Text(o) }
                }
                if (picked != null) {
                    if (q.explanation.isNotBlank()) Text("Explanation: ${q.explanation}")
                    Button({ idx++; picked = null }) { Text("Next") }
                }
                Chip("End early") { idx = queue.size }
            }
        }
    }
    if (dlg) {
        val e = qs.firstOrNull { it.id == editId }
        FormDialog(if (e == null) "New question" else "Edit question",
            listOf("Subject / topic", "Question", "Options (separate with ;)", "Correct option number", "Explanation"),
            if (e == null) listOf("", "", "", "1", "") else listOf(e.subject, e.text, e.options.joinToString("; "), (e.correct + 1).toString(), e.explanation),
            { v ->
                val opts = v[2].split(";").map { it.trim() }.filter { it.isNotEmpty() }
                if (opts.size >= 2) {
                    val nq = Question(id = e?.id ?: uid(), subject = v[0].ifBlank { "General" }, text = v[1], options = opts,
                        correct = ((v[3].toIntOrNull() ?: 1) - 1).coerceIn(0, opts.size - 1), explanation = v[4])
                    s.update { x -> x.copy(questions = if (e == null) x.questions + nq else x.questions.map { if (it.id == e.id) nq else it }) }
                }
            }) { dlg = false }
    }
}

@Composable fun Exams(s: Store) {
    var dlg by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var taskDlg by remember { mutableStateOf<String?>(null) }
    var editExam by remember { mutableStateOf<String?>(null) }
    fun edit(id: String, f: (Exam) -> Exam) = s.update { d -> d.copy(exams = d.exams.map { if (it.id == id) f(it) else it }) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("+ Exam") { dlg = true }
        s.data.exams.sortedBy { it.date }.forEach { e ->
            val left = e.date - LocalDate.now().toEpochDay()
            val pct = if (e.tasks.isEmpty()) 0f else e.tasks.count { it.done } / e.tasks.size.toFloat()
            GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text("${e.title} · ${e.subject}", Modifier.weight(1f).clickable { open = if (open == e.id) null else e.id })
                        Text(if (left >= 0) "$left days" else "past", color = Gold)
                    }
                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    val tasksLeft = e.tasks.count { !it.done }
                    if (tasksLeft > 0 && left > 0) Text("$tasksLeft syllabus task(s) left · about ${(tasksLeft + left.toInt() - 1) / left.toInt()} per day", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                    if (open == e.id) {
                        if (e.notes.isNotBlank()) Text(e.notes, fontSize = 13.sp)
                        e.tasks.forEachIndexed { i, t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(t.done, { v -> edit(e.id) { x -> x.copy(tasks = x.tasks.mapIndexed { j, y -> if (j == i) y.copy(done = v) else y }) } })
                                Text(t.text)
                            }
                        }
                        Row { Chip("Edit exam") { editExam = e.id }; Chip("+ Syllabus task") { taskDlg = e.id }; Chip("Delete exam") { s.update { d -> d.copy(exams = d.exams.filter { it.id != e.id }) } } }
                    }
                }
            }
        }
    }
    if (dlg) FormDialog("New exam", listOf("Title", "Subject", "Date (YYYY-MM-DD)", "Notes"), onOk = { v ->
        val day = runCatching { LocalDate.parse(v[2].trim()) }.getOrDefault(LocalDate.now().plusDays(30))
        s.update { d -> d.copy(exams = d.exams + Exam(title = v[0].ifBlank { "Exam" }, subject = v[1], date = day.toEpochDay(), notes = v[3])) }
    }, onClose = { dlg = false })
    s.data.exams.firstOrNull { it.id == editExam }?.let { e ->
        FormDialog("Edit exam", listOf("Title", "Subject", "Date (YYYY-MM-DD)", "Notes"), listOf(e.title, e.subject, LocalDate.ofEpochDay(e.date).toString(), e.notes), onOk = { v ->
            val day = runCatching { LocalDate.parse(v[2].trim()) }.getOrDefault(LocalDate.ofEpochDay(e.date))
            edit(e.id) { x -> x.copy(title = v[0].ifBlank { x.title }, subject = v[1], date = day.toEpochDay(), notes = v[3]) }
        }, onClose = { editExam = null })
    }
    taskDlg?.let { id -> FormDialog("Syllabus task", listOf("Task"), onOk = { v -> if (v[0].isNotBlank()) edit(id) { x -> x.copy(tasks = x.tasks + Task(v[0])) } }, onClose = { taskDlg = null }) }
}

@Composable fun Focus(s: Store) {
    var pick by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(s.timerSubject, { s.timerSubject = it }, label = { Text("Subject / topic") },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Chip("Pick a topic from my notes") { pick = true }
        if (pick) TopicPicker(s, "Study which topic?", onPick = { s.timerSubject = it.title }, onClose = { pick = false })
        Row { listOf(15, 25, 45, 60, 90).forEach { m -> Chip("$m", s.timerMinutes == m) { s.changeTimerMinutes(m) } } }
        Text(java.lang.String.format(java.util.Locale.US, "%02d:%02d", s.timerLeft / 60, s.timerLeft % 60), fontSize = 64.sp, color = Gold)
        Row {
            Button({ s.timerToggle() }) { Text(if (s.timerRunning) "Pause" else if (s.timerLeft < s.timerMinutes * 60) "Resume" else "Start") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ s.timerReset() }) { Text("Reset") }
        }
        Text("A small timer floats on every screen while a session is running. Drag it anywhere. It stops if the app is closed.", fontSize = 12.sp)
        SectionTitle("Session history")
        s.data.sessions.takeLast(10).reversed().forEach { Text("${it.subject.ifBlank { "Study" }} · ${it.minutes} min · ${fmt(it.time)}", fontSize = 13.sp) }
    }
}
