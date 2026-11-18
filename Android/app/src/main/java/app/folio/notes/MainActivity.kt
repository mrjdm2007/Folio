package app.folio.notes

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
    val compactLayout = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700
    val prefs = remember { context.getSharedPreferences("folio_local", android.content.Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf(loadNotes(prefs.getString("notes", "[]") ?: "[]") { corrupt ->
        // Preserve the original bytes before a later edit can overwrite malformed storage.
        prefs.edit().putString("notes_recovery_backup", corrupt).apply()
    }) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var permanentDeleteId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("All Notes") }
    var editingTitle by remember { mutableStateOf("") }
    var editingBody by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf("Notes") }
    var editingTags by remember { mutableStateOf("") }
    var showRenderedPreview by remember { mutableStateOf(false) }
    var attachedDocs by remember { mutableStateOf<List<FolioDocument>>(emptyList()) }
    var attachmentTargetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val first = notes.firstOrNull { !it.deleted && !it.archived }
        if (first != null && selectedId == null) {
            selectedId = first.id
            editingTitle = first.title
            editingBody = first.body
            editingFolder = first.folder
            editingTags = first.tags.joinToString(", ")
            attachedDocs = decodeDocuments(prefs.getString("documents_${first.id}", "[]") ?: "[]")
        }
    }
    fun persist(updated: List<FolioNote>) {
        notes = updated; prefs.edit().putString("notes", encodeNotes(updated)).apply()
        FolioWidgetProvider.refresh(context)
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
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(encodeBackup(notes).toByteArray(Charsets.UTF_8)) }
                ?: error("Could not open the selected destination.")
        }.onSuccess { backupStatus = "Backup exported." }
            .onFailure { backupStatus = it.message ?: "Backup export failed." }
    }
    val importAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = attachmentTargetId
        if (uri != null && target != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Attached document"
            val current = decodeDocuments(prefs.getString("documents_$target", "[]") ?: "[]")
            val next = current + FolioDocument(uri.toString(), name)
            prefs.edit().putString("documents_$target", encodeDocuments(next)).apply()
            if (selectedId == target) attachedDocs = next
        }.onFailure { backupStatus = it.message ?: "Could not attach document." }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read the selected file.")
            val imported = decodeBackup(raw)
            val existingById = notes.associateBy { it.id }
            val importedById = imported.groupBy { it.id }.mapValues { (_, copies) -> copies.maxBy { it.updatedAt } }
            val merged = importedById.map { (id, candidate) ->
                val existing = existingById[id]
                if (existing != null && existing.updatedAt > candidate.updatedAt) existing else candidate
            } + notes.filter { local -> imported.none { it.id == local.id } }
            persist(merged.sortedByDescending { it.updatedAt })
        }.onSuccess { backupStatus = "Backup restored and merged." }
            .onFailure { backupStatus = it.message ?: "Invalid backup file." }
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
            "Class Notes" -> "Class Notes" to "# Topic\n\n**Date:** $date\n**Course:** \n\n## Key concepts\n- \n\n## Examples\n\n## Questions\n- [ ] "
            "Meeting" -> "Meeting Notes" to "# Meeting\n\n**Date:** $date\n**Attendees:** \n\n## Agenda\n1. \n\n## Decisions\n- \n\n## Action items\n- [ ] Owner — Task — Due date"
            "Daily Journal" -> "Daily Journal" to "# Daily Journal\n\n**Date:** $date\n\n## Today\n\n## Highlights\n- \n\n## To remember\n- "
            else -> "Project Brief" to "# Project Brief\n\n## Objective\n\n## Scope\n\n## Milestones\n- [ ] \n\n## Risks\n\n## Next steps"
        }
        val note = FolioNote(UUID.randomUUID().toString(), pair.first, pair.second)
        persist(listOf(note) + notes); selectedId = note.id
        editingTitle = note.title; editingBody = note.body; editingFolder = "Notes"; editingTags = ""; section = "All Notes"
    }
    val folders = notes.asSequence().filter { !it.deleted && !it.archived }
        .map { it.folder.trim().ifBlank { "Notes" } }.filterNot { it in setOf("All Notes", "Archive", "Recently Deleted") }.distinct().sorted().toList()
    val filtered = notes.filter { note ->
        val inSection = when (section) {
            "Archive" -> note.archived && !note.deleted
            "Recently Deleted" -> note.deleted
            "Pinned" -> note.pinned && !note.archived && !note.deleted
            "All Notes" -> !note.archived && !note.deleted
            else -> note.folder.trim().ifBlank { "Notes" } == section && !note.archived && !note.deleted
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportBackup.launch("Folio-Backup.json") }) { Text("Export backup") }
                OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/json")) }) { Text("Restore backup") }
            }
            backupStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Text(filtered.size.toString() + " notes shown · " + notes.count { it.pinned && !it.archived && !it.deleted }.toString() + " pinned",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All Notes", "Pinned", "Archive", "Recently Deleted").forEach { name ->
                    FilterChip(selected = section == name, onClick = { section = name }, label = { Text(name) })
                }
            }
            Spacer(Modifier.height(6.dp))
            if (folders.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { folder ->
                        FilterChip(selected = section == folder, onClick = { section = folder },
                            label = { Text(folder) })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.weight(1f)) {
                if (!compactLayout || selectedId == null) Column(Modifier.weight(if (compactLayout) 1f else 0.9f)) {
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
                                        IconButton(onClick = { persist(notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned, updatedAt = System.currentTimeMillis()) else it }) }) {
                                            Icon(Icons.Default.PushPin, if (note.pinned) "Unpin" else "Pin")
                                        }
                                        if (!note.deleted && !note.archived) {
                                            TextButton(onClick = { duplicate(note) }) { Text("Copy") }
                                            IconButton(onClick = {
                                                persist(notes.map { if (it.id == note.id) it.copy(archived = true, updatedAt = System.currentTimeMillis()) else it })
                                                if (selectedId == note.id) selectedId = null
                                            }) { Icon(Icons.Default.Archive, "Archive") }
                                            IconButton(onClick = {
                                                persist(notes.map { if (it.id == note.id) it.copy(deleted = true, archived = false, updatedAt = System.currentTimeMillis()) else it })
                                                if (selectedId == note.id) selectedId = null
                                            }) { Icon(Icons.Default.Delete, "Move to trash") }
                                        } else if (note.deleted) {
                                            TextButton(onClick = { persist(notes.map { if (it.id == note.id) it.copy(deleted = false, archived = false, updatedAt = System.currentTimeMillis()) else it }) }) { Text("Restore") }
                                            TextButton(onClick = { permanentDeleteId = note.id }) { Text("Delete permanently") }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedId = note.id; editingTitle = note.title; editingBody = note.body
                                    editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                                    attachedDocs = decodeDocuments(prefs.getString("documents_${note.id}", "[]") ?: "[]")
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                if (!compactLayout) Spacer(Modifier.width(10.dp))
                if (selectedId != null) {
                    Column(Modifier.weight(if (compactLayout) 1f else 1.1f)) {
                        if (compactLayout) TextButton(onClick = { selectedId = null }) { Text("← All notes") }
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
                                    editingBody += (if (editingBody.isBlank() || editingBody.endsWith("\n")) "" else "\n") + prefix
                                    updateSelected { it.copy(body = editingBody) }
                                }) { Text(label, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Markdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showRenderedPreview = !showRenderedPreview }) { Text(if (showRenderedPreview) "Edit source" else "Preview") }
                        }
                        if (showRenderedPreview) {
                            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                                items(editingBody.lines()) { line ->
                                    val clean = line.trimStart()
                                    val heading = clean.startsWith("#")
                                    val content = clean.trimStart('#', ' ').let { if (clean.startsWith("- ")) "• " + clean.removePrefix("- ") else it }
                                    Text(content, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        style = when { clean.startsWith("# ") -> MaterialTheme.typography.headlineSmall; clean.startsWith("## ") -> MaterialTheme.typography.titleLarge; clean.startsWith("### ") -> MaterialTheme.typography.titleMedium; clean.startsWith("> ") -> MaterialTheme.typography.bodyLarge; else -> MaterialTheme.typography.bodyMedium },
                                        color = if (clean.startsWith("> ")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        } else {
                            OutlinedTextField(value = editingBody, onValueChange = { value ->
                                editingBody = value; updateSelected { it.copy(body = value) }
                            }, modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Write in Markdown…") })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Documents · ${attachedDocs.size}", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { attachmentTargetId = selectedId; importAttachment.launch(arrayOf("application/pdf", "image/*", "text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }) { Text("Attach file") }
                        }
                        attachedDocs.forEach { doc ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = {
                                    runCatching {
                                        val uri = Uri.parse(doc.uri)
                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                    }.onFailure { backupStatus = "No app can open this document." }
                                }) { Text(doc.name, maxLines = 1) }
                                TextButton(onClick = {
                                    val next = attachedDocs.filterNot { it.uri == doc.uri }; attachedDocs = next
                                    selectedId?.let { prefs.edit().putString("documents_${it}", encodeDocuments(next)).apply() }
                                }) { Text("Remove") }
                            }
                        }
                        Text(editingBody.split(Regex("\\s+")).count { it.isNotBlank() }.toString() + " words · " + editingBody.length + " characters",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            val note = notes.firstOrNull { it.id == selectedId } ?: return@TextButton
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_SUBJECT, note.title)
                                putExtra(Intent.EXTRA_TEXT, "# " + note.title + "\n\n" + note.body)
                            }
                            context.startActivity(Intent.createChooser(send, "Share note"))
                        }) { Text("Share / Export Markdown") }
                    }
                } else if (!compactLayout) {
                    Box(Modifier.weight(1.1f).fillMaxHeight()) {
                        Text("Select a note or create one.", modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
    permanentDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { permanentDeleteId = null },
            title = { Text("Delete note permanently?") },
            text = { Text("This removes the note from this device and cannot be undone. Make sure you have a backup if you need it.") },
            confirmButton = {
                TextButton(onClick = {
                    persist(notes.filterNot { it.id == id })
                    if (selectedId == id) selectedId = null
                    permanentDeleteId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}
private data class FolioNote(
    val id: String, val title: String, val body: String, val pinned: Boolean = false,
    val folder: String = "Notes", val tags: List<String> = emptyList(),
    val archived: Boolean = false, val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = updatedAt
)
private fun encodeBackup(notes: List<FolioNote>): String {
    val portable = JSONArray()
    notes.forEach { note ->
        val tags = JSONArray().apply { note.tags.forEach { put(it) } }
        portable.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body)
            .put("folder", note.folder).put("tags", tags).put("isPinned", note.pinned)
            .put("isArchived", note.archived).put("isDeleted", note.deleted)
            .put("createdAtMs", note.createdAt).put("updatedAtMs", note.updatedAt))
    }
    return JSONObject().put("schemaVersion", 1).put("exportedAtMs", System.currentTimeMillis())
        .put("notes", portable).toString(2)
}

private fun decodeBackup(raw: String): List<FolioNote> {
    val now = System.currentTimeMillis()
    val root = runCatching { JSONObject(raw) }.getOrNull()
    val array = if (root != null) {
        require(root.optInt("schemaVersion", -1) == 1) { "Unsupported Folio backup version." }
        root.optJSONArray("notes") ?: error("Backup has no notes.")
    } else {
        // Backward compatibility with the original macOS JSON-array backup format.
        runCatching { JSONArray(raw) }.getOrElse { error("This is not a valid Folio backup.") }
    }
    return (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val tags = item.optJSONArray("tags") ?: JSONArray()
        val createdAtMs = when {
            item.has("createdAtMs") -> item.optLong("createdAtMs", now)
            item.has("createdAt") -> (item.optDouble("createdAt") * 1000.0 + 978307200000.0).toLong()
            else -> item.optLong("updatedAtMs", now)
        }
        val updatedAtMs = when {
            item.has("updatedAtMs") -> item.optLong("updatedAtMs", now)
            item.has("updatedAt") -> (item.optDouble("updatedAt") * 1000.0 + 978307200000.0).toLong()
            else -> now
        }
        FolioNote(
            id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            title = item.optString("title", "Untitled Note"),
            body = item.optString("body", ""),
            pinned = item.optBoolean("isPinned", item.optBoolean("pinned")),
            folder = item.optString("folder", "Notes"),
            tags = (0 until tags.length()).map { tags.optString(it) },
            archived = item.optBoolean("isArchived", item.optBoolean("archived")),
            deleted = item.optBoolean("isDeleted", item.optBoolean("deleted")),
            updatedAt = updatedAtMs,
            createdAt = createdAtMs
        )
    }
}
private fun loadNotes(raw: String, onCorrupt: (String) -> Unit = {}): List<FolioNote> = try {
    val array = JSONArray(raw)
    val parsed = (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        val tagsJson = item.optJSONArray("tags") ?: JSONArray()
        FolioNote(item.getString("id"), item.optString("title"), item.optString("body"),
            item.optBoolean("pinned"), item.optString("folder", "Notes"),
            (0 until tagsJson.length()).map { tagsJson.optString(it) },
            item.optBoolean("archived"), item.optBoolean("deleted"),
            item.optLong("updatedAt", System.currentTimeMillis()),
            item.optLong("createdAt", item.optLong("updatedAt", System.currentTimeMillis())))
    }
    // Duplicate IDs break LazyColumn stable keys. Keep the newest valid local version.
    parsed.groupBy { it.id }.values.map { copies -> copies.maxBy { it.updatedAt } }
        .sortedByDescending { it.updatedAt }
} catch (_: Exception) {
    onCorrupt(raw)
    emptyList()
}
private fun encodeNotes(notes: List<FolioNote>): String {
    val array = JSONArray()
    notes.forEach { note ->
        val tags = JSONArray().apply { note.tags.forEach { put(it) } }
        array.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body)
            .put("pinned", note.pinned).put("folder", note.folder).put("tags", tags)
            .put("archived", note.archived).put("deleted", note.deleted)
            .put("createdAt", note.createdAt).put("updatedAt", note.updatedAt))
    }
    return array.toString()
}

private data class FolioDocument(val uri: String, val name: String)
private fun encodeDocuments(items: List<FolioDocument>): String = JSONArray().apply {
    items.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) }
}.toString()
private fun decodeDocuments(raw: String): List<FolioDocument> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        FolioDocument(uri, item.optString("name", "Attached document"))
    }
}.getOrDefault(emptyList())
