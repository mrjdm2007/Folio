package com.folio.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

// ---- Shared notebook format (the same JSON will be used by the Mac app and sync) ----
@Serializable data class Block(val id: String = uid(), val type: String = "text", val text: String = "", val extra: String = "", val checked: Boolean = false)
@Serializable data class Snap(val time: Long, val title: String, val blocks: List<Block>)
@Serializable data class Node(
    val id: String = uid(), val parentId: String? = null, val kind: String, val title: String,
    val tags: List<String> = emptyList(), val blocks: List<Block> = emptyList(),
    val deleted: Boolean = false, val bookmarked: Boolean = false, val folder: String = "",
    val updated: Long = now(), val history: List<Snap> = emptyList()
)
@Serializable data class Flash(val id: String = uid(), val deck: String, val front: String, val back: String,
    val due: Long = 0, val interval: Int = 0, val ease: Double = 2.5, val reps: Int = 0, val last: Long = 0)
@Serializable data class Question(val id: String = uid(), val subject: String, val text: String,
    val options: List<String>, val correct: Int, val explanation: String = "")
@Serializable data class Task(val text: String, val done: Boolean = false)
@Serializable data class Exam(val id: String = uid(), val title: String, val subject: String, val date: Long,
    val notes: String = "", val tasks: List<Task> = emptyList())
@Serializable data class Session(val time: Long, val subject: String, val minutes: Int)
@Serializable data class Formula(val id: String = uid(), val name: String, val latex: String)
@Serializable data class Data(
    val schema: Int = 1,
    val nodes: List<Node> = emptyList(), val cards: List<Flash> = emptyList(),
    val questions: List<Question> = emptyList(), val exams: List<Exam> = emptyList(),
    val sessions: List<Session> = emptyList(), val formulas: List<Formula> = emptyList(),
    val graphs: List<String> = emptyList(), val dailyGoal: Int = 120,
    val texts: Map<String, String> = emptyMap()   // extracted PDF text, keyed by stored attachment name
)
@Serializable data class BackupFile(val data: Data, val files: Map<String, String> = emptyMap())

class Store(app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val file = File(app.filesDir, "folio.json")
    var data by mutableStateOf(Data()); private set

    init { runCatching { if (file.exists()) data = json.decodeFromString<Data>(file.readText()) } }

    fun update(f: (Data) -> Data) { data = f(data); save() }
    private fun save() {   // atomic write: a crash can never leave a half-written notebook
        val tmp = File(file.path + ".tmp"); tmp.writeText(json.encodeToString(data)); tmp.renameTo(file)
    }

    // ---- tree ----
    fun node(id: String) = data.nodes.firstOrNull { it.id == id }
    fun children(parent: String?) = data.nodes.filter { it.parentId == parent && !it.deleted }
    fun path(id: String): String {
        val out = mutableListOf<String>(); var n = node(id)
        while (n != null) { out.add(0, n.title); n = n.parentId?.let { node(it) } }
        return out.joinToString(" › ")
    }
    fun recent(n: Int) = data.nodes.filter { it.kind == "page" && !it.deleted }.sortedByDescending { it.updated }.take(n)
    fun search(q: String) = data.nodes.filter { n ->
        n.kind == "page" && !n.deleted && (
            if (q.startsWith("#")) n.tags.any { it.contains(q.drop(1), true) }
            else n.title.contains(q, true) || n.tags.any { it.contains(q, true) } ||
                n.blocks.any { it.text.contains(q, true) || it.extra.contains(q, true) } ||
                n.blocks.any { it.type == "file" && data.texts[it.text]?.contains(q, true) == true })
    }
    fun editNode(id: String, f: (Node) -> Node) = update { d -> d.copy(nodes = d.nodes.map { if (it.id == id) f(it).copy(updated = now()) else it }) }
    fun editBlocks(id: String, f: (List<Block>) -> List<Block>) = editNode(id) { it.copy(blocks = f(it.blocks)) }
    fun add(parent: String?, kind: String, title: String) = update { d ->
        d.copy(nodes = d.nodes + Node(parentId = parent, kind = kind, title = title.ifBlank { "Untitled" },
            blocks = if (kind == "page") listOf(Block()) else emptyList()))
    }
    fun quickNote(): String {
        var cid = data.nodes.firstOrNull { it.kind == "course" && it.title == "Quick Notes" && !it.deleted }?.id
        val pid = uid()
        update { d ->
            var nodes = d.nodes
            if (cid == null) { cid = uid(); nodes = nodes + Node(id = cid!!, kind = "course", title = "Quick Notes") }
            d.copy(nodes = nodes + Node(id = pid, parentId = cid, kind = "page", title = "Quick note " + fmt(now()), blocks = listOf(Block())))
        }
        return pid
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
        val sib = d.nodes.filter { it.parentId == n.parentId && !it.deleted && it.kind == "page" }
        val nx = sib.getOrNull(sib.indexOfFirst { it.id == id } + 1)
        if (nx == null) d else d.copy(nodes = d.nodes.map {
            when (it.id) { id -> it.copy(blocks = it.blocks + nx.blocks, updated = now()); nx.id -> it.copy(deleted = true); else -> it }
        })
    }

    // ---- flashcards (simple SM-2 style scheduling) ----
    fun addCard(deck: String, front: String, back: String) =
        update { d -> d.copy(cards = d.cards + Flash(deck = deck.ifBlank { "Default" }, front = front, back = back)) }
    fun renameDeck(old: String, new: String) =
        update { d -> d.copy(cards = d.cards.map { if (it.deck == old) it.copy(deck = new.ifBlank { old }) else it }) }
    fun rate(id: String, r: Int) = update { d ->
        d.copy(cards = d.cards.map { c ->
            if (c.id != id) c else (when (r) {
                0 -> c.copy(interval = 0, reps = 0, ease = maxOf(1.3, c.ease - 0.2), due = now() + 10 * 60_000L)
                1 -> { val i = maxOf(1, (maxOf(c.interval, 1) * 1.2).toInt()); c.copy(interval = i, reps = c.reps + 1, ease = maxOf(1.3, c.ease - 0.15), due = now() + i * DAY) }
                2 -> { val i = when (c.reps) { 0 -> 1; 1 -> 3; else -> (c.interval * c.ease).toInt() }; c.copy(interval = i, reps = c.reps + 1, due = now() + i * DAY) }
                else -> { val i = when (c.reps) { 0 -> 4; 1 -> 7; else -> (c.interval * c.ease * 1.3).toInt() }; c.copy(interval = i, reps = c.reps + 1, ease = c.ease + 0.15, due = now() + i * DAY) }
            }).copy(last = now())
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
        val ext = name.substringAfterLast('.', "").lowercase()
        val stored = uid() + (if (ext.isNotEmpty()) ".$ext" else "")
        cr.openInputStream(uri)!!.use { i -> attachmentFile(stored, cache).outputStream().use { o -> i.copyTo(o) } }
        stored to name
    }.getOrNull()

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
        data.nodes.filter { it.kind == "page" && !it.deleted }.groupBy { it.blocks.joinToString("|") { b -> b.text } }
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
