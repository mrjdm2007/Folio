package app.folio.notes

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FolioTheme { FolioScreen() } }
    }
}
@Composable
fun FolioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("folio_local", android.content.Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf(loadNotes(prefs.getString("notes", "[]") ?: "[]")) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("All Notes") }
    var editingTitle by remember { mutableStateOf("") }
    var editingBody by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf("Notes") }
    var editingTags by remember { mutableStateOf("") }
    fun persist(updated: List<FolioNote>) {
        notes = updated; prefs.edit().putString("notes", encodeNotes(updated)).apply()
    }
    fun newNote() {
        val note = FolioNote(UUID.randomUUID().toString(), "Untitled Note", "")
        persist(listOf(note) + notes); selectedId = note.id
        editingTitle = note.title; editingBody = ""; editingFolder = "Notes"; editingTags = ""; section = "All Notes"
    }
    fun updateSelected(transform: (FolioNote) -> FolioNote) {
        val id = selectedId ?: return
        persist(notes.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }
    fun duplicate(note: FolioNote) {
        val copy = note.copy(id = UUID.randomUUID().toString(), title = note.title + " (Copy)",
            pinned = false, archived = false, deleted = false, updatedAt = System.currentTimeMillis())
        persist(listOf(copy) + notes); selectedId = copy.id
        editingTitle = copy.title; editingBody = copy.body; editingFolder = copy.folder
        editingTags = copy.tags.joinToString(", "); section = "All Notes"
    }
    fun createTemplate(kind: String) {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val pair = when (kind) {
            "Class Notes" -> "Class Notes" to "# Topic\\n\\n**Date:** $date\\n**Course:** \\n\\n## Key concepts\\n- \\n\\n## Examples\\n\\n## Questions\\n- [ ] "
            "Meeting" -> "Meeting Notes" to "# Meeting\\n\\n**Date:** $date\\n**Attendees:** \\n\\n## Agenda\\n1. \\n\\n## Decisions\\n- \\n\\n## Action items\\n- [ ] Owner — Task — Due date"
            "Daily Journal" -> "Daily Journal" to "# Daily Journal\\n\\n**Date:** $date\\n\\n## Today\\n\\n## Highlights\\n- \\n\\n## To remember\\n- "
            else -> "Project Brief" to "# Project Brief\\n\\n## Objective\\n\\n## Scope\\n\\n## Milestones\\n- [ ] \\n\\n## Risks\\n\\n## Next steps"
        }
        val note = FolioNote(UUID.randomUUID().toString(), pair.first, pair.second)
        persist(listOf(note) + notes); selectedId = note.id
        editingTitle = note.title; editingBody = note.body; editingFolder = "Notes"; editingTags = ""; section = "All Notes"
    }
    val filtered = notes.filter { note ->
        val inSection = when (section) {
            "Archive" -> note.archived && !note.deleted
            "Recently Deleted" -> note.deleted
            "All Notes" -> !note.archived && !note.deleted
            else -> note.folder == section && !note.archived && !note.deleted
        }
        inSection && (query.isBlank() || note.title.contains(query, true) ||
            note.body.contains(query, true) || note.tags.any { it.contains(query, true) })
    }.sortedWith(compareByDescending<FolioNote> { it.pinned }.thenByDescending { it.updatedAt })

    Scaffold(topBar = {
        TopAppBar(title = { Text("Folio", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                var showTemplates by remember { mutableStateOf(false) }
                IconButton(onClick = { showTemplates = true }) { Icon(Icons.Default.Add, "New note or template") }
                DropdownMenu(expanded = showTemplates, onDismissRequest = { showTemplates = false }) {
                    DropdownMenuItem(text = { Text("Blank note") }, onClick = { showTemplates = false; newNote() })
                    listOf("Class Notes", "Meeting", "Daily Journal", "Project Brief").forEach { kind ->
                        DropdownMenuItem(text = { Text(kind) }, onClick = { showTemplates = false; createTemplate(kind) })
                    }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search titles, text and tags") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All Notes", "Archive", "Recently Deleted").forEach { name ->
                    FilterChip(selected = section == name, onClick = { section = name }, label = { Text(name) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(0.9f)) {
                    LazyColumn {
                        items(filtered, key = { it.id }) { note ->
                            ListItem(
                                headlineContent = { Text(note.title.ifBlank { "Untitled Note" }) },
                                supportingContent = {
                                    Column {
                                        Text(note.body.ifBlank { "No additional text" }, maxLines = 2)
                                        Text(note.folder + if (note.tags.isEmpty()) "" else " · " + note.tags.joinToString(", "),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { persist(notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }) }) {
                                            Icon(Icons.Default.PushPin, if (note.pinned) "Unpin" else "Pin")
                                        }
                                        if (!note.deleted && !note.archived) {
                                            TextButton(onClick = { duplicate(note) }) { Text("Copy") }
                                            IconButton(onClick = {
                                                persist(notes.map { if (it.id == note.id) it.copy(archived = true) else it })
                                                if (selectedId == note.id) selectedId = null
                                            }) { Icon(Icons.Default.Archive, "Archive") }
                                            IconButton(onClick = {
                                                persist(notes.map { if (it.id == note.id) it.copy(deleted = true) else it })
                                                if (selectedId == note.id) selectedId = null
                                            }) { Icon(Icons.Default.Delete, "Move to trash") }
                                        } else if (note.deleted) {
                                            TextButton(onClick = { persist(notes.map { if (it.id == note.id) it.copy(deleted = false, archived = false) else it }) }) { Text("Restore") }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedId = note.id; editingTitle = note.title; editingBody = note.body
                                    editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (selectedId != null) {
                    Column(Modifier.weight(1.1f)) {
                        OutlinedTextField(value = editingTitle, onValueChange = { value ->
                            editingTitle = value; updateSelected { it.copy(title = value) }
                        }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Title") },
                            textStyle = MaterialTheme.typography.titleLarge, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = editingFolder, onValueChange = { value ->
                            editingFolder = value; updateSelected { it.copy(folder = value.ifBlank { "Notes" }) }
                        }, modifier = Modifier.fillMaxWidth(), label = { Text("Folder") }, singleLine = true)
                        OutlinedTextField(value = editingTags, onValueChange = { value ->
                            editingTags = value
                            val tags = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            updateSelected { it.copy(tags = tags) }
                        }, modifier = Modifier.fillMaxWidth(), label = { Text("Tags (comma separated)") }, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("# " to "H1", "## " to "H2", "- " to "Bullet", "- [ ] " to "Task", "> " to "Quote").forEach { (prefix, label) ->
                                TextButton(onClick = {
                                    editingBody += (if (editingBody.isBlank() || editingBody.endsWith("\\n")) "" else "\\n") + prefix
                                    updateSelected { it.copy(body = editingBody) }
                                }) { Text(label, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        OutlinedTextField(value = editingBody, onValueChange = { value ->
                            editingBody = value; updateSelected { it.copy(body = value) }
                        }, modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Write in Markdown…") })
                        TextButton(onClick = {
                            val note = notes.firstOrNull { it.id == selectedId } ?: return@TextButton
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_SUBJECT, note.title)
                                putExtra(Intent.EXTRA_TEXT, "# " + note.title + "\\n\\n" + note.body)
                            }
                            context.startActivity(Intent.createChooser(send, "Share note"))
                        }) { Text("Share / Export Markdown") }
                    }
                } else {
                    Box(Modifier.weight(1.1f).fillMaxHeight()) {
                        Text("Select a note or create one.", modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}
private data class FolioNote(
    val id: String, val title: String, val body: String, val pinned: Boolean = false,
    val folder: String = "Notes", val tags: List<String> = emptyList(),
    val archived: Boolean = false, val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
private fun loadNotes(raw: String): List<FolioNote> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        val tagsJson = item.optJSONArray("tags") ?: JSONArray()
        FolioNote(item.getString("id"), item.optString("title"), item.optString("body"),
            item.optBoolean("pinned"), item.optString("folder", "Notes"),
            (0 until tagsJson.length()).map { tagsJson.optString(it) },
            item.optBoolean("archived"), item.optBoolean("deleted"),
            item.optLong("updatedAt", System.currentTimeMillis()))
    }
} catch (_: Exception) { emptyList() }
private fun encodeNotes(notes: List<FolioNote>): String {
    val array = JSONArray()
    notes.forEach { note ->
        val tags = JSONArray().apply { note.tags.forEach { put(it) } }
        array.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body)
            .put("pinned", note.pinned).put("folder", note.folder).put("tags", tags)
            .put("archived", note.archived).put("deleted", note.deleted).put("updatedAt", note.updatedAt))
    }
    return array.toString()
}
