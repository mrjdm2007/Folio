package com.folio.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableIntStateOf
import android.media.RingtoneManager
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun uid() = UUID.randomUUID().toString()
fun now() = System.currentTimeMillis()
const val DAY = 86_400_000L
fun fmt(t: Long): String = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(t))
fun fmtDay(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
fun <T> swap(l: List<T>, a: Int, b: Int): List<T> {
    if (a < 0 || b < 0 || a >= l.size || b >= l.size) return l
    val m = l.toMutableList(); val t = m[a]; m[a] = m[b]; m[b] = t; return m
}

/** Number shown for a numbered-list block, counting siblings at the same indent level. */
fun numberOf(l: List<Block>, i: Int): Int {
    var k = 1; var j = i - 1
    while (j >= 0 && l[j].type == "number" && l[j].indent >= l[i].indent) { if (l[j].indent == l[i].indent) k++; j-- }
    return k
}

// ---- Shared notebook format (the same JSON will be used by the Mac app and sync) ----
@Serializable data class Block(val id: String = uid(), val type: String = "text", val text: String = "", val extra: String = "", val checked: Boolean = false, val indent: Int = 0)
@Serializable data class Snap(val time: Long, val title: String, val blocks: List<Block>)
@Serializable data class Node(
    val id: String = uid(), val parentId: String? = null, val kind: String, val title: String,
    val tags: List<String> = emptyList(), val blocks: List<Block> = emptyList(),
    val deleted: Boolean = false, val bookmarked: Boolean = false, val folder: String = "",
    val updated: Long = now(), val history: List<Snap> = emptyList(), val readPos: Int = 0,
    val revDue: Long = 0, val revStep: Int = 0   // topic revision schedule
)
@Serializable data class Flash(val id: String = uid(), val deck: String, val front: String, val back: String,
    val due: Long = 0, val interval: Int = 0, val ease: Double = 2.5, val reps: Int = 0, val last: Long = 0, val lapses: Int = 0)
@Serializable data class Question(val id: String = uid(), val subject: String, val text: String,
    val options: List<String>, val correct: Int, val explanation: String = "", val right: Int = 0, val wrong: Int = 0)
@Serializable data class Task(val text: String, val done: Boolean = false)
@Serializable data class Exam(val id: String = uid(), val title: String, val subject: String, val date: Long,
    val notes: String = "", val tasks: List<Task> = emptyList())
@Serializable data class Session(val time: Long, val subject: String, val minutes: Int)
@Serializable data class Formula(val id: String = uid(), val name: String, val latex: String)
@Serializable data class Ann(val id: String = uid(), val file: String, val page: Int, val kind: String,   // "hl" highlight or "note" pin
    val x: Float, val y: Float, val w: Float = 0f, val h: Float = 0f, val text: String = "")   // positions are 0..1 of the page
@Serializable data class Todo(val id: String = uid(), val text: String, val due: Long = 0, val subject: String = "", val done: Boolean = false)
@Serializable data class Data(
    val schema: Int = 1,
    val nodes: List<Node> = emptyList(), val cards: List<Flash> = emptyList(),
    val questions: List<Question> = emptyList(), val exams: List<Exam> = emptyList(),
    val sessions: List<Session> = emptyList(), val formulas: List<Formula> = emptyList(),
    val graphs: List<String> = emptyList(), val dailyGoal: Int = 120,
    val texts: Map<String, String> = emptyMap(),   // extracted PDF text, keyed by stored attachment name
    val userName: String = "", val nameAsked: Boolean = false,
    val annotations: List<Ann> = emptyList(),
    val reviewLog: Map<String, Int> = emptyMap(),   // date -> cards reviewed that day
    val todos: List<Todo> = emptyList()
)
@Serializable data class BackupFile(val data: Data, val files: Map<String, String> = emptyMap())

class Store(app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val file = File(app.filesDir, "folio.json")
    var data by mutableStateOf(Data()); private set

    init {
        if (file.exists()) runCatching { data = json.decodeFromString<Data>(file.readText()) }.onFailure {
            // never overwrite a notebook we could not read: keep a copy first
            runCatching { file.copyTo(File(file.path + ".corrupt-" + now()), overwrite = true) }
        }
    }
    private var saveJob: Job? = null
    fun update(f: (Data) -> Data) { data = f(data); scheduleSave() }
    private fun scheduleSave() { saveJob?.cancel(); saveJob = viewModelScope.launch { delay(400); withContext(Dispatchers.IO) { writeNow() } } }
    @Synchronized private fun writeNow() {   // atomic write: a crash can never leave a half-written notebook
        val tmp = File(file.path + ".tmp"); tmp.writeText(json.encodeToString(data)); tmp.renameTo(file)
    }
    /** Called when the app goes to the background so nothing is left waiting to be saved. */
    fun flush() { saveJob?.cancel(); writeNow() }
    override fun onCleared() { flush() }

    // ---- tree ----
    fun node(id: String) = data.nodes.firstOrNull { it.id == id }
    fun children(parent: String?) = data.nodes.filter { it.parentId == parent && !it.deleted }
    fun path(id: String): String {
        val out = mutableListOf<String>(); var n = node(id)
        while (n != null) { out.add(0, n.title); n = n.parentId?.let { node(it) } }
        return out.joinToString(" › ")
    }
    fun recent(n: Int) = data.nodes.filter { (it.kind == "page" || it.kind == "topic") && !it.deleted && it.blocks.any { b -> b.text.isNotBlank() || b.type == "image" } }.sortedByDescending { it.updated }.take(n)
    fun search(q: String) = data.nodes.filter { n ->
        (n.kind == "page" || n.kind == "topic") && !n.deleted && (
            if (q.startsWith("#")) n.tags.any { it.contains(q.drop(1), true) }
            else n.title.contains(q, true) || n.tags.any { it.contains(q, true) } ||
                n.blocks.any { it.text.contains(q, true) || it.extra.contains(q, true) } ||
                n.blocks.any { it.type == "file" && data.texts[it.text]?.contains(q, true) == true })
    }
    fun editNode(id: String, f: (Node) -> Node) = update { d ->
        d.copy(nodes = d.nodes.map { old ->
            if (old.id != id) old else {
                var nn = f(old).copy(updated = now())
                // automatic version history: keep the previous state if the last saved version is over 10 minutes old
                val stale = (old.history.lastOrNull()?.time ?: 0L) < now() - 600_000L
                if (nn.history == old.history && nn.blocks != old.blocks && stale && old.blocks.any { b -> b.text.isNotBlank() })
                    nn = nn.copy(history = (old.history + Snap(now(), old.title, old.blocks)).takeLast(30))
                nn
            }
        })
    }
    // ---- undo / redo (per page, kept in memory; quick bursts of typing count as one step) ----
    private val undoS = HashMap<String, ArrayList<List<Block>>>()
    private val redoS = HashMap<String, ArrayList<List<Block>>>()
    private val lastPush = HashMap<String, Long>()
    private fun recordUndo(id: String) {
        val cur = node(id)?.blocks ?: return
        val st = undoS.getOrPut(id) { ArrayList() }
        if (st.isEmpty() || now() - (lastPush[id] ?: 0L) > 1200L) { st.add(cur); if (st.size > 60) st.removeAt(0) }
        lastPush[id] = now(); redoS[id]?.clear()
    }
    fun editBlocks(id: String, f: (List<Block>) -> List<Block>) { recordUndo(id); editNode(id) { it.copy(blocks = f(it.blocks)) } }
    fun canUndo(id: String) = undoS[id]?.isNotEmpty() == true
    fun canRedo(id: String) = redoS[id]?.isNotEmpty() == true
    fun undo(id: String) {
        val st = undoS[id] ?: return; if (st.isEmpty()) return
        val cur = node(id)?.blocks ?: return
        redoS.getOrPut(id) { ArrayList() }.add(cur)
        val prev = st.removeAt(st.size - 1); lastPush[id] = 0L
        editNode(id) { it.copy(blocks = prev) }
    }
    fun redo(id: String) {
        val st = redoS[id] ?: return; if (st.isEmpty()) return
        val cur = node(id)?.blocks ?: return
        undoS.getOrPut(id) { ArrayList() }.add(cur)
        val next = st.removeAt(st.size - 1); lastPush[id] = 0L
        editNode(id) { it.copy(blocks = next) }
    }
    fun applyTemplate(id: String, name: String) = editBlocks(id) { l ->
        l.filter { it.text.isNotBlank() || it.type == "image" || it.type == "file" } + templateBlocks(name)
    }
    /** Saves a quiz block into the question bank. False if it is incomplete or already saved. */
    fun addQuizBlock(subject: String, b: Block): Boolean {
        val (opts, ok) = quizOptions(b)
        if (b.text.isBlank() || opts.size < 2) return false
        val q = Question(subject = subject.ifBlank { "General" }, text = b.text.trim(), options = opts, correct = ok)
        if (data.questions.any { it.text == q.text }) return false
        update { d -> d.copy(questions = d.questions + q) }; return true
    }
    fun saveBitmap(bmp: Bitmap): String? = runCatching {
        val stored = uid() + ".png"
        attachmentFile(stored).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        stored
    }.getOrNull()
    /** Creates a node and returns its id. Topics (and legacy pages) start with one empty text block, ready to type in. */
    fun add(parent: String?, kind: String, title: String): String {
        val n = Node(parentId = parent, kind = kind, title = title.ifBlank { "Untitled" },
            blocks = if (kind == "page" || kind == "topic") listOf(Block()) else emptyList())
        update { d -> d.copy(nodes = d.nodes + n) }
        return n.id
    }
    private fun descendants(id: String): Set<String> {
        val out = mutableSetOf(id); var changed = true
        while (changed) { changed = false; data.nodes.forEach { n -> if (n.parentId?.let { it in out } == true && out.add(n.id)) changed = true } }
        return out
    }
    fun trash(id: String) { val ids = descendants(id); update { d -> d.copy(nodes = d.nodes.map { if (it.id in ids) it.copy(deleted = true) else it }) } }
    fun restore(id: String) {
        val ids = descendants(id).toMutableSet(); var p = node(id)?.parentId
        while (p != null) { ids.add(p); p = node(p)?.parentId }
        update { d -> d.copy(nodes = d.nodes.map { if (it.id in ids) it.copy(deleted = false) else it }) }
    }
    fun purge(id: String) { val ids = descendants(id); update { d -> d.copy(nodes = d.nodes.filter { it.id !in ids }) } }
    fun duplicate(id: String) = update { d ->
        val ids = descendants(id); val map = ids.associateWith { uid() }
        val copies = d.nodes.filter { it.id in ids }.map { n ->
            n.copy(id = map[n.id]!!, parentId = if (n.id == id) n.parentId else n.parentId?.let { map[it] },
                title = if (n.id == id) n.title + " (copy)" else n.title, updated = now())
        }
        d.copy(nodes = d.nodes + copies)
    }
    fun move(id: String, up: Boolean) = update { d ->
        val n = d.nodes.first { it.id == id }
        val sib = d.nodes.filter { it.parentId == n.parentId && !it.deleted }
        val i = sib.indexOfFirst { it.id == id }; val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= sib.size) d
        else d.copy(nodes = swap(d.nodes, d.nodes.indexOfFirst { it.id == id }, d.nodes.indexOfFirst { it.id == sib[j].id }))
    }
    fun split(id: String, idx: Int) = update { d ->
        val n = d.nodes.first { it.id == id }; val tail = n.blocks.drop(idx + 1)
        if (tail.isEmpty()) d else d.copy(
            nodes = d.nodes.map { if (it.id == id) it.copy(blocks = n.blocks.take(idx + 1)) else it } +
                n.copy(id = uid(), title = n.title + " (2)", blocks = tail, history = emptyList(), updated = now()))
    }
    fun mergeNext(id: String) = update { d ->
        val n = d.nodes.first { it.id == id }
        val sib = d.nodes.filter { it.parentId == n.parentId && !it.deleted && (it.kind == "page" || it.kind == "topic") }
        val nx = sib.getOrNull(sib.indexOfFirst { it.id == id } + 1)
        if (nx == null) d else d.copy(nodes = d.nodes.map {
            when (it.id) { id -> it.copy(blocks = it.blocks + nx.blocks, updated = now()); nx.id -> it.copy(deleted = true); else -> it }
        })
    }

    fun allTags(): List<String> = data.nodes.filter { !it.deleted }.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    fun setReadPos(id: String, pos: Int) = update { d -> d.copy(nodes = d.nodes.map { if (it.id == id) it.copy(readPos = pos) else it }) }

    fun insertBlocks(pid: String, afterId: String?, new: List<Block>) = editBlocks(pid) { l ->
        val i = l.indexOfFirst { it.id == afterId }
        if (i < 0) l + new else l.take(i + 1) + new + l.drop(i + 1)
    }
    /** Replaces one block by [newText] followed by [extra] blocks (used to drop an image/PDF into the middle of a paragraph). */
    fun replaceBlock(pid: String, blockId: String, newText: String, extra: List<Block>) = editBlocks(pid) { l ->
        l.flatMap { b -> if (b.id != blockId) listOf(b) else (if (newText.isBlank() && b.type == "text") emptyList() else listOf(b.copy(text = newText))) + extra }
    }
    fun setName(n: String) = update { d -> d.copy(userName = n.trim(), nameAsked = true) }
    fun markNameAsked() = update { d -> if (d.nameAsked) d else d.copy(nameAsked = true) }

    fun findByTitle(t: String) = data.nodes.firstOrNull { !it.deleted && (it.kind == "topic" || it.kind == "page") && it.title.trim().equals(t.trim(), true) }
    fun saveFileToTopic(t: ViewTarget, topicId: String) {
        if (t.cache) runCatching { attachmentFile(t.stored, true).copyTo(attachmentFile(t.stored, false), overwrite = true) }
        insertBlocks(topicId, null, listOf(Block(type = "file", text = t.stored, extra = t.name), Block()))
    }
    fun inboxTopic(title: String): String {
        var cid = data.nodes.firstOrNull { it.kind == "course" && it.title == "Inbox" && !it.deleted }?.id
        if (cid == null) cid = add(null, "course", "Inbox")
        return add(cid, "topic", title.ifBlank { "Document" })
    }
    fun addAnn(a: Ann) = update { d -> d.copy(annotations = d.annotations + a) }
    fun removeAnn(id: String) = update { d -> d.copy(annotations = d.annotations.filter { it.id != id }) }
    fun setAnnText(id: String, text: String) = update { d -> d.copy(annotations = d.annotations.map { if (it.id == id) it.copy(text = text) else it }) }

    // ---- files opened from other apps (WhatsApp, Files, Gmail...) ----
    var incoming by mutableStateOf<ViewTarget?>(null)
    fun openIncoming(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { attach(uri, cache = true) }
            incoming = if (r != null) ViewTarget(r.first, r.second, true) else ViewTarget("missing", "Unreadable file", true)
        }
    }

    // ---- focus timer: survives screen changes and app restarts, and rings through a system alarm ----
    private val app get() = getApplication<Application>()
    var timerMinutes by mutableIntStateOf(25); private set
    var timerLeft by mutableIntStateOf(25 * 60); private set
    var timerRunning by mutableStateOf(false); private set
    var timerSubject by mutableStateOf("")
    private var timerEnd = 0L
    private var timerJob: Job? = null
    private fun saveTimer() {
        Prefs.p(app).edit().putInt("t_min", timerMinutes).putInt("t_left", timerLeft).putBoolean("t_run", timerRunning)
            .putLong("t_end", timerEnd).putString("t_subj", timerSubject).apply()
    }
    private fun restoreTimer() {
        val sp = Prefs.p(app)
        timerMinutes = sp.getInt("t_min", 25); timerSubject = sp.getString("t_subj", "") ?: ""
        timerLeft = sp.getInt("t_left", timerMinutes * 60); timerEnd = sp.getLong("t_end", 0L)
        if (sp.getBoolean("t_run", false)) {
            if (timerEnd > now()) startTick()
            else { timerLeft = timerMinutes * 60; timerRunning = false; saveTimer() }   // it finished while the app was closed
        }
    }
    private fun startTick() {
        timerRunning = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timerRunning) {
                timerLeft = maxOf(0, ((timerEnd - now() + 999) / 1000).toInt())
                if (timerLeft <= 0) break
                delay(250)
            }
            if (timerLeft <= 0 && timerRunning) finishTimer()
        }
    }
    fun changeTimerMinutes(m: Int) { if (!timerRunning) { timerMinutes = m; timerLeft = m * 60; saveTimer() } }
    fun timerToggle() {
        if (timerRunning) {
            timerJob?.cancel(); timerRunning = false
            Notifier.cancelEnd(app); Notifier.timerPaused(app, timerLeft, timerSubject)
        } else {
            timerEnd = now() + timerLeft * 1000L
            startTick()
            Notifier.timerRunning(app, timerEnd, timerSubject, timerMinutes); Notifier.scheduleEnd(app, timerEnd, timerSubject, timerMinutes)
        }
        saveTimer()
    }
    fun timerReset() {
        timerJob?.cancel(); timerRunning = false; timerLeft = timerMinutes * 60
        Notifier.cancelEnd(app); Notifier.clearTimer(app); saveTimer()
    }
    private fun finishTimer() {
        timerRunning = false
        if (TimerPrefs.markLogged(app, timerEnd)) update { d -> d.copy(sessions = d.sessions + Session(now(), timerSubject, timerMinutes)) }
        else viewModelScope.launch { delay(700); mergePending() }   // the system alarm logged it first
        if (!Notifier.canPost(app)) runCatching { RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).play() }
        timerLeft = timerMinutes * 60; saveTimer()
    }
    fun mergePending() { val l = TimerPrefs.takePending(app); if (l.isNotEmpty()) update { d -> d.copy(sessions = d.sessions + l) } }
    init { restoreTimer(); mergePending(); autoBackup() }

    // ---- flashcards (simple SM-2 style scheduling) ----
    fun addCard(deck: String, front: String, back: String) = update { d ->
        val dk = deck.ifBlank { "Default" }
        if (front.isBlank() || d.cards.any { it.deck == dk && it.front == front }) d
        else d.copy(cards = d.cards + Flash(deck = dk, front = front, back = back))
    }
    fun renameDeck(old: String, new: String) =
        update { d -> d.copy(cards = d.cards.map { if (it.deck == old) it.copy(deck = new.ifBlank { old }) else it }) }
    val appStart = now()

    /** One card per {{hidden}} part: the sentence with that part blanked out on the front, the part itself on the back. */
    fun addClozeCards(deck: String, text: String): Int {
        val rx = Regex("\\{\\{(.+?)\\}\\}")
        val ms = rx.findAll(text).toList()
        var made = 0
        ms.forEachIndexed { k, m ->
            var idx = -1
            val front = rx.replace(text) { r -> idx++; if (idx == k) "[…]" else r.groupValues[1] }
            val before = data.cards.size
            addCard(deck, front, m.groupValues[1]); if (data.cards.size > before) made++
        }
        return made
    }

    // ---- topic revision schedule: 1, 3, 7, 14, 30, then 60 days ----
    private val revSteps = intArrayOf(1, 3, 7, 14, 30, 60)
    fun nextRevisionDays(n: Node) = revSteps[minOf(n.revStep, revSteps.size - 1)]
    fun markRevised(id: String) = update { d ->
        d.copy(nodes = d.nodes.map { n -> if (n.id != id) n else n.copy(revStep = n.revStep + 1, revDue = now() + nextRevisionDays(n) * DAY) })
    }
    fun stopRevising(id: String) = update { d -> d.copy(nodes = d.nodes.map { n -> if (n.id == id) n.copy(revDue = 0, revStep = 0) else n }) }
    fun dueRevisions(): List<Node> = data.nodes.filter { !it.deleted && it.revDue > 0 && it.revDue <= now() }.sortedBy { it.revDue }

    /** Quick capture: a fresh topic in Quick Notes > Inbox, ready to type in. */
    fun quickNote(): String {
        val course = data.nodes.firstOrNull { it.kind == "course" && it.title == "Quick Notes" && !it.deleted }?.id ?: add(null, "course", "Quick Notes")
        val chapter = data.nodes.firstOrNull { it.kind == "chapter" && it.parentId == course && !it.deleted }?.id ?: add(course, "chapter", "Inbox")
        return add(chapter, "topic", "Quick note " + fmt(now()))
    }

    fun tagCounts(): List<Pair<String, Int>> =
        data.nodes.filter { !it.deleted }.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    fun renameTag(old: String, new: String) {
        val n = new.trim().removePrefix("#"); if (n.isBlank()) return
        update { d -> d.copy(nodes = d.nodes.map { x -> if (old in x.tags) x.copy(tags = x.tags.map { if (it == old) n else it }.distinct()) else x }) }
    }
    fun deleteTag(tag: String) = update { d -> d.copy(nodes = d.nodes.map { x -> if (tag in x.tags) x.copy(tags = x.tags - tag) else x }) }

    fun recordAnswer(qid: String, ok: Boolean) = update { d ->
        d.copy(questions = d.questions.map { if (it.id == qid) it.copy(right = it.right + (if (ok) 1 else 0), wrong = it.wrong + (if (ok) 0 else 1)) else it })
    }

    fun rate(id: String, r: Int) = update { d ->
        val day = java.time.LocalDate.now().toString()
        d.copy(reviewLog = d.reviewLog + (day to ((d.reviewLog[day] ?: 0) + 1)), cards = d.cards.map { c ->
            if (c.id != id) c else (when (r) {
                0 -> c.copy(interval = 0, reps = 0, ease = maxOf(1.3, c.ease - 0.2), due = now() + 10 * 60_000L)
                1 -> { val i = maxOf(1, (maxOf(c.interval, 1) * 1.2).toInt()); c.copy(interval = i, reps = c.reps + 1, ease = maxOf(1.3, c.ease - 0.15), due = now() + i * DAY) }
                2 -> { val i = when (c.reps) { 0 -> 1; 1 -> 3; else -> (c.interval * c.ease).toInt() }; c.copy(interval = i, reps = c.reps + 1, due = now() + i * DAY) }
                else -> { val i = when (c.reps) { 0 -> 4; 1 -> 7; else -> (c.interval * c.ease * 1.3).toInt() }; c.copy(interval = i, reps = c.reps + 1, ease = c.ease + 0.15, due = now() + i * DAY) }
            }).copy(last = now(), lapses = c.lapses + (if (r == 0) 1 else 0))
        })
    }

    // ---- attachments ----
    fun attachmentFile(stored: String, cache: Boolean = false): File {
        val app = getApplication<Application>()
        return File(File(if (cache) app.cacheDir else app.filesDir, "attachments").apply { mkdirs() }, stored)
    }
    /** Copies a picked/pasted file into app storage. Returns (stored name, original name). */
    fun attach(uri: Uri, cache: Boolean = false): Pair<String, String>? = runCatching {
        val cr = getApplication<Application>().contentResolver
        var name = "file"
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) ?: name }
        }
        var ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) {   // some apps (e.g. WhatsApp) give no extension: use the MIME type instead
            ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(cr.getType(uri)) ?: ""
            if (ext.isNotEmpty()) name += ".$ext"
        }
        val stored = uid() + (if (ext.isNotEmpty()) ".$ext" else "")
        cr.openInputStream(uri)!!.use { i -> attachmentFile(stored, cache).outputStream().use { o -> i.copyTo(o) } }
        stored to name
    }.getOrNull()

    // ---- automatic daily backups kept inside the app (last 7) ----
    private fun autoBackup() {
        runCatching {
            val dir = File(getApplication<Application>().filesDir, "backups").apply { mkdirs() }
            val f = File(dir, "folio-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json")
            if (!f.exists() && file.exists()) {
                file.copyTo(f)
                dir.listFiles()?.sortedBy { it.name }?.dropLast(7)?.forEach { it.delete() }
            }
        }
    }
    fun backups(): List<File> = File(getApplication<Application>().filesDir, "backups").listFiles()?.sortedByDescending { it.name }.orEmpty()
    fun restoreBackup(f: File): Boolean = runCatching { importJson(f.readText()) }.getOrDefault(false)

    // ---- backup / checks ----
    fun exportJson(): String {
        val files = mutableMapOf<String, String>()
        data.nodes.flatMap { it.blocks }.filter { it.type == "image" || it.type == "file" }.forEach { b ->
            val f = attachmentFile(b.text)
            if (f.exists()) files[b.text] = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        }
        return json.encodeToString(BackupFile(data, files))
    }
    fun importJson(t: String): Boolean = runCatching {
        require(t.contains("\"schema\""))
        val bf = runCatching { json.decodeFromString<BackupFile>(t) }.getOrNull()
        val (nd, files) = bf?.let { it.data to it.files } ?: (json.decodeFromString<Data>(t) to emptyMap<String, String>())
        files.forEach { (name, b64) -> attachmentFile(name).writeBytes(Base64.decode(b64, Base64.NO_WRAP)) }
        update { _ -> nd }; true
    }.getOrDefault(false)
    fun integrity(): List<String> {
        val r = mutableListOf<String>(); val ids = data.nodes.map { it.id }.toSet()
        data.nodes.filter { n -> n.parentId?.let { it !in ids } == true }.forEach { r += "Orphaned item: ${it.title}" }
        data.nodes.filter { (it.kind == "page" || it.kind == "topic") && !it.deleted }.groupBy { it.blocks.joinToString("|") { b -> b.text } }
            .filter { it.value.size > 1 && it.key.isNotBlank() }.forEach { r += "Duplicate content: " + it.value.joinToString { n -> n.title } }
        data.nodes.filter { !it.deleted }.forEach { n ->
            n.blocks.filter { it.type == "image" || it.type == "file" }.forEach { b ->
                if (!attachmentFile(b.text).exists()) r += "Missing attachment: ${b.extra.ifBlank { b.text }} (in ${n.title})"
            }
        }
        if (r.isEmpty()) r += "No problems found"
        return r
    }
}
