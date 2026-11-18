package app.folio.notes

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val colors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFB9F36A),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF17200C),
        secondary = androidx.compose.ui.graphics.Color(0xFFFFB85C),
        onSecondary = androidx.compose.ui.graphics.Color(0xFF261706),
        tertiary = androidx.compose.ui.graphics.Color(0xFF8DD8A2),
        background = androidx.compose.ui.graphics.Color(0xFF0A0D0B),
        onBackground = androidx.compose.ui.graphics.Color(0xFFE7E9E2),
        surface = androidx.compose.ui.graphics.Color(0xFF131815),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE7E9E2),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF202820),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFADB7A8),
        outline = androidx.compose.ui.graphics.Color(0xFF465344)
    )
    MaterialTheme(colorScheme = colors, content = content)
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
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf("Recently updated") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSyncPanel by remember { mutableStateOf(false) }
    var historyNoteId by remember { mutableStateOf<String?>(null) }
    var historyItems by remember { mutableStateOf<List<FolioNote>>(emptyList()) }
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
        val current = notes.firstOrNull { it.id == id } ?: return
        val changed = transform(current)
        if (current.title != changed.title || current.body != changed.body ||
            current.folder != changed.folder || current.tags != changed.tags) appendRevision(prefs, current)
        val updated = changed.copy(updatedAt = System.currentTimeMillis())
        persist(notes.map { if (it.id == id) updated else it })
    }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var peerAddress by remember { mutableStateOf(prefs.getString("p2p_peer_address", "") ?: "") }
    var pairingKey by remember { mutableStateOf(FolioPeerSync.pairingSecret(context)) }
    var syncMessage by remember { mutableStateOf("Peer sync is not paired") }
    DisposableEffect(Unit) {
        FolioPeerSync.onRemoteNotes = { raw ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                notes = loadNotes(raw)
                selectedId?.let { id ->
                    notes.firstOrNull { it.id == id }?.let { note ->
                        editingTitle = note.title; editingBody = note.body
                        editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                    }
                }
            }
        }
        onDispose { FolioPeerSync.onRemoteNotes = null }
    }
    LaunchedEffect(Unit) {
        FolioPeerSync.startListener(context)
        val savedPeer = prefs.getString("p2p_peer_address", "") ?: ""
        if (savedPeer.isNotBlank() && pairingKey.length >= 32) {
            syncMessage = "Checking paired device…"
            FolioPeerSync.sync(context, savedPeer) { result ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    result.onSuccess { count ->
                        notes = loadNotes(prefs.getString("notes", "[]") ?: "[]")
                        selectedId?.let { id -> notes.firstOrNull { it.id == id }?.let { note ->
                            editingTitle = note.title; editingBody = note.body
                            editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                        } }
                        syncMessage = "Synced · $count notes"
                    }.onFailure { error -> syncMessage = "Peer unavailable · ${error.message ?: "retry later"}" }
                }
            }
        }
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(encodeBackup(notes).toByteArray(Charsets.UTF_8)) }
                ?: error("Could not open the selected destination.")
        }.onSuccess { backupStatus = "Backup exported." }
            .onFailure { backupStatus = it.message ?: "Backup export failed." }
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        val note = notes.firstOrNull { it.id == selectedId }
        if (uri != null && note != null) runCatching {
            val markdown = buildString {
                append("# ").append(note.title).append("\n\n")
                if (note.tags.isNotEmpty()) append(note.tags.joinToString(" ") { "#$it" }).append("\n\n")
                append(note.body)
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
                ?: error("Could not write the Markdown file.")
        }.onSuccess { backupStatus = "Markdown exported." }
            .onFailure { backupStatus = it.message ?: "Markdown export failed." }
    }
    val importAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = attachmentTargetId
        if (uri != null && target != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Attached document"
            val current = decodeDocuments(prefs.getString("documents_$target", "[]") ?: "[]")
            require(current.none { it.uri == uri.toString() }) { "This document is already attached." }
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
            "Class Notes" -> "Class Notes" to """# Topic

**Date:** $date
**Course:**

## Key concepts
-

## Examples

## Questions
- [ ]"""
            "Meeting" -> "Meeting Notes" to """# Meeting

**Date:** $date
**Attendees:**

## Agenda
1.

## Decisions
-

## Action items
- [ ] Owner — Task — Due date"""
            "Daily Journal" -> "Daily Journal" to """# Daily Journal

**Date:** $date

## Today

## Highlights
-

## To remember
-"""
            "Research Notes" -> "Research Notes" to """# Research question

**Source / URL:**
**Date accessed:** $date

## Summary

## Evidence
-

## Open questions
- [ ]

## References
- """
            "Reading List" -> "Reading List" to """# Reading List

## To read
- [ ] Title — Author — Link

## Reading notes

## Finished
- [x] """
            "Weekly Planner" -> "Weekly Planner" to """# Weekly Plan · $date

## Priorities
1.
2.
3.

## Monday
- [ ]

## Tuesday
- [ ]

## Wednesday
- [ ]

## Thursday
- [ ]

## Friday
- [ ]

## Weekend
- [ ]

## Notes / review""" 
            "Lecture Revision" -> "Lecture Revision" to """# Lecture Revision

**Subject:**
**Topic:**
**Exam date:**

## Core concepts
-

## Formulas / definitions
-

## Worked examples

## Questions to practise
- [ ]

## Confidence check
- [ ] Revisit weak areas""" 
            else -> "Project Brief" to """# Project Brief

## Objective

## Scope

## Milestones
- [ ]

## Risks

## Next steps"""
        }
        val note = FolioNote(UUID.randomUUID().toString(), pair.first, pair.second)
        persist(listOf(note) + notes); selectedId = note.id
        editingTitle = note.title; editingBody = note.body; editingFolder = "Notes"; editingTags = ""; section = "All Notes"
    }
    fun createDailyNote() {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val title = "Daily · $date"
        val existing = notes.firstOrNull { it.title == title && it.folder == "Daily Notes" && !it.deleted && !it.archived }
        if (existing != null) {
            selectedId = existing.id; editingTitle = existing.title; editingBody = existing.body
            editingFolder = existing.folder; editingTags = existing.tags.joinToString(", ")
            attachedDocs = decodeDocuments(prefs.getString("documents_${existing.id}", "[]") ?: "[]")
            section = "Daily Notes"
            return
        }
        val note = FolioNote(UUID.randomUUID().toString(), title,
            "# $title\n\n## Priorities\n- [ ] \n\n## Notes\n\n## Tasks\n- [ ] ",
            folder = "Daily Notes")
        persist(listOf(note) + notes); selectedId = note.id
        editingTitle = note.title; editingBody = note.body; editingFolder = note.folder
        editingTags = ""; section = "Daily Notes"
    }
    val folders = notes.asSequence().filter { !it.deleted && !it.archived }
        .map { it.folder.trim().ifBlank { "Notes" } }.filterNot { it in setOf("All Notes", "Archive", "Recently Deleted") }.distinct().sorted().toList()
    val filtered = notes.filter { note ->
        val inSection = when (section) {
            "Archive" -> note.archived && !note.deleted
            "Recently Deleted" -> note.deleted
            "Pinned" -> note.pinned && !note.archived && !note.deleted
            "Tasks" -> !note.archived && !note.deleted && Regex("(?m)^\\s*- \\[ \\]").containsMatchIn(note.body)
            "All Notes" -> !note.archived && !note.deleted
            else -> note.folder.trim().ifBlank { "Notes" } == section && !note.archived && !note.deleted
        }
        inSection && (selectedTag == null || note.tags.any { it.equals(selectedTag, true) }) && (query.isBlank() || note.title.contains(query, true) ||
            note.body.contains(query, true) || note.tags.any { it.contains(query, true) })
    }.let { matches ->
        val pinnedFirst = matches.sortedByDescending { it.pinned }
        when (sortMode) {
            "Recently created" -> pinnedFirst.sortedByDescending { it.createdAt }
            "Oldest first" -> pinnedFirst.sortedBy { it.updatedAt }
            "Title A–Z" -> pinnedFirst.sortedBy { it.title.lowercase() }
            "Title Z–A" -> pinnedFirst.sortedByDescending { it.title.lowercase() }
            else -> pinnedFirst.sortedByDescending { it.updatedAt }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("F", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("FOLIO", style = MaterialTheme.typography.titleLarge, letterSpacing = 2.sp)
                    Text("PERSONAL LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.2.sp)
                }
            } },
            actions = {
                IconButton(onClick = { showSyncPanel = !showSyncPanel }) {
                    Icon(Icons.Default.Settings, if (showSyncPanel) "Hide sync settings" else "Sync settings")
                }
                var showTemplates by remember { mutableStateOf(false) }
                IconButton(onClick = { showTemplates = true }) { Icon(Icons.Default.Add, "New note or template") }
                DropdownMenu(expanded = showTemplates, onDismissRequest = { showTemplates = false }) {
                    DropdownMenuItem(text = { Text("Daily note · today") }, onClick = { showTemplates = false; createDailyNote() })
                    DropdownMenuItem(text = { Text("Blank note") }, onClick = { showTemplates = false; newNote() })
                    listOf("Class Notes", "Meeting", "Daily Journal", "Project Brief", "Research Notes", "Reading List", "Weekly Planner", "Lecture Revision").forEach { kind ->
                        DropdownMenuItem(text = { Text(kind) }, onClick = { showTemplates = false; createTemplate(kind) })
                    }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search titles, text and tags") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            if (showSyncPanel) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Peer-to-peer sync", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = pairingKey, onValueChange = { pairingKey = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Shared pairing key (32+ characters)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pairingKey = FolioPeerSync.createPairingSecret(context)
                            syncMessage = "Pairing key created. Enter the same key on your other device."
                        }) { Text("Create key") }
                        OutlinedButton(onClick = {
                            runCatching { FolioPeerSync.setPairingSecret(context, pairingKey) }
                                .onSuccess { syncMessage = "This device is paired and listening on port 47831." }
                                .onFailure { syncMessage = it.message ?: "Invalid pairing key." }
                        }) { Text("Save key") }
                    }
                    OutlinedTextField(value = peerAddress, onValueChange = { peerAddress = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Other device IP address") }, singleLine = true)
                    Button(onClick = {
                        prefs.edit().putString("p2p_peer_address", peerAddress.trim()).apply()
                        syncMessage = "Connecting…"
                        FolioPeerSync.sync(context, peerAddress.trim()) { result ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.onSuccess { count ->
                                    notes = loadNotes(prefs.getString("notes", "[]") ?: "[]")
                                    selectedId?.let { id -> notes.firstOrNull { it.id == id }?.let { note ->
                                        editingTitle = note.title; editingBody = note.body
                                        editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                                    } }
                                    syncMessage = "Sync complete · $count notes on this device"
                                }.onFailure { error -> syncMessage = "Sync failed · ${error.message ?: "connection error"}" }
                            }
                        }
                    }, enabled = peerAddress.isNotBlank() && pairingKey.length >= 32) { Text("Sync now") }
                    Text(syncMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Both devices must use the same key and be reachable on the same Wi-Fi or private VPN. Keep this key private.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportBackup.launch("Folio-Backup.json") }) { Text("Export library") }
                        OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/json")) }) { Text("Restore backup") }
                    }
                    backupStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(filtered.size.toString() + " notes shown · " + notes.count { it.pinned && !it.archived && !it.deleted }.toString() + " pinned",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    TextButton(onClick = { showSortMenu = true }) { Text("Sort: $sortMode") }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf("Recently updated", "Recently created", "Oldest first", "Title A–Z", "Title Z–A").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { sortMode = option; showSortMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All Notes", "Pinned", "Tasks", "Archive", "Recently Deleted").forEach { name ->
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
            val availableTags = notes.filter { !it.archived && !it.deleted }.flatMap { it.tags }
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
            if (availableTags.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedTag == null, onClick = { selectedTag = null },
                        label = { Text("All tags") })
                    availableTags.forEach { tag ->
                        FilterChip(selected = selectedTag == tag, onClick = {
                            selectedTag = if (selectedTag == tag) null else tag
                        }, label = { Text("#$tag") })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.weight(1f)) {
                if (!compactLayout || selectedId == null) Column(Modifier.weight(if (compactLayout) 1f else 0.9f)) {
                    LazyColumn {
                        items(filtered, key = { it.id }) { note ->
                            FolioNoteRow(
                                note = note, selected = selectedId == note.id,
                                onSelect = {
                                    selectedId = note.id; editingTitle = note.title; editingBody = note.body
                                    editingFolder = note.folder; editingTags = note.tags.joinToString(", ")
                                    attachedDocs = decodeDocuments(prefs.getString("documents_${note.id}", "[]") ?: "[]")
                                },
                                onPin = { persist(notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned, updatedAt = System.currentTimeMillis()) else it }) },
                                onDuplicate = { duplicate(note) },
                                onArchive = {
                                    persist(notes.map { if (it.id == note.id) it.copy(archived = true, updatedAt = System.currentTimeMillis()) else it })
                                    if (selectedId == note.id) selectedId = null
                                },
                                onTrash = {
                                    persist(notes.map { if (it.id == note.id) it.copy(deleted = true, archived = false, updatedAt = System.currentTimeMillis()) else it })
                                    if (selectedId == note.id) selectedId = null
                                },
                                onRestore = { persist(notes.map { if (it.id == note.id) it.copy(deleted = false, archived = false, updatedAt = System.currentTimeMillis()) else it }) },
                                onDelete = { permanentDeleteId = note.id }
                            )
                            Spacer(Modifier.height(5.dp))                        }
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
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("# " to "H1", "## " to "H2", "- " to "Bullet", "- [ ] " to "Task", "> " to "Quote",
                                "**bold**" to "Bold", "_italic_" to "Italic", "`code`" to "Code", "[text](url)" to "Link",
                                "| Column | Column |\\n| --- | --- |\\n| Value | Value |" to "Table").forEach { (snippet, label) ->
                                TextButton(onClick = {
                                    val addition = if (snippet in listOf("**bold**", "_italic_", "`code`", "[text](url)")) snippet
                                    else (if (editingBody.isBlank() || editingBody.endsWith("\n")) "" else "\n") + snippet
                                    editingBody += addition
                                    updateSelected { it.copy(body = editingBody) }
                                }) { Text(label, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Markdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = {
                                    val id = selectedId ?: return@TextButton
                                    historyItems = runCatching {
                                        val array = JSONArray(prefs.getString("history_$id", "[]") ?: "[]")
                                        (0 until array.length()).map { index ->
                                            val item = array.getJSONObject(index)
                                            val tagArray = item.optJSONArray("tags") ?: JSONArray()
                                            val rawId = item.getString("id")
        val canonicalId = runCatching { UUID.fromString(rawId).toString() }.getOrDefault(rawId)
        FolioNote(canonicalId, item.optString("title"), item.optString("body"),
                                                item.optBoolean("isPinned"), item.optString("folder", "Notes"),
                                                (0 until tagArray.length()).map { tagArray.optString(it) },
                                                item.optBoolean("isArchived"), item.optBoolean("isDeleted"),
                                                item.optLong("updatedAtMs"), item.optLong("createdAtMs"))
                                        }
                                    }.getOrDefault(emptyList())
                                    historyNoteId = id
                                }) { Text("History") }
                                TextButton(onClick = { showRenderedPreview = !showRenderedPreview }) { Text(if (showRenderedPreview) "Edit source" else "Preview") }
                            }
                        }
                        if (showRenderedPreview) {
                            MarkdownPreview(editingBody, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
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
                        val wordCount = editingBody.split(Regex("\\s+")).count { it.isNotBlank() }
                        val checklist = Regex("(?m)^\\s*- \\[([ xX])\\]").findAll(editingBody).toList()
                        val completedTasks = checklist.count { it.groupValues[1].equals("x", true) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val readingMinutes = if (wordCount == 0) 0 else (wordCount + 199) / 200
                            Text("$wordCount words · ${editingBody.length} characters · ${readingMinutes} min read",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (checklist.isNotEmpty()) Text("$completedTasks / ${checklist.size} tasks",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (checklist.isNotEmpty()) LinearProgressIndicator(
                            progress = { completedTasks.toFloat() / checklist.size.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val note = notes.firstOrNull { it.id == selectedId } ?: return@TextButton
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(Intent.EXTRA_SUBJECT, note.title)
                                    putExtra(Intent.EXTRA_TEXT, "# " + note.title + "\n\n" + note.body)
                                }
                                context.startActivity(Intent.createChooser(send, "Share note"))
                            }) { Text("Share") }
                            TextButton(onClick = {
                                val name = (notes.firstOrNull { it.id == selectedId }?.title ?: "Folio-Note")
                                    .replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "Folio-Note" } + ".md"
                                exportMarkdown.launch(name)
                            }) { Text("Export .md") }
                        }
                    }
                } else if (!compactLayout) {
                    Box(Modifier.weight(1.1f).fillMaxHeight()) {
                        Text("Select a note or create one.", modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
    historyNoteId?.let { id ->
        AlertDialog(
            onDismissRequest = { historyNoteId = null },
            title = { Text("Version history") },
            text = {
                if (historyItems.isEmpty()) Text("No previous versions have been saved yet.")
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    historyItems.forEach { version ->
                        TextButton(onClick = {
                            notes.firstOrNull { it.id == id }?.let { appendRevision(prefs, it) }
                            persist(notes.map { if (it.id == id) version.copy(updatedAt = System.currentTimeMillis()) else it })
                            if (selectedId == id) {
                                editingTitle = version.title; editingBody = version.body
                                editingFolder = version.folder; editingTags = version.tags.joinToString(", ")
                            }
                            historyNoteId = null
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(version.title.ifBlank { "Untitled Note" })
                                Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(version.updatedAt)) + " · " + version.body.take(90),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyNoteId = null }) { Text("Close") } }
        )
    }
    permanentDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { permanentDeleteId = null },
            title = { Text("Delete note permanently?") },
            text = { Text("This removes the note from this device and cannot be undone. Make sure you have a backup if you need it.") },
            confirmButton = {
                TextButton(onClick = {
                    persist(notes.filterNot { it.id == id })
                    prefs.edit().remove("documents_$id").remove("revisions_$id").apply()
                    if (selectedId == id) { selectedId = null; attachedDocs = emptyList() }
                    permanentDeleteId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}
@Composable
private fun FolioNoteRow(
    note: FolioNote, selected: Boolean, onSelect: () -> Unit, onPin: () -> Unit,
    onDuplicate: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit,
    onRestore: () -> Unit, onDelete: () -> Unit
) {
    var expanded by remember(note.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(
                if (note.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(note.title.ifBlank { "Untitled Note" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    if (note.pinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                }
                Text(note.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "No additional text",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text(note.folder + if (note.tags.isEmpty()) "" else "  ·  " + note.tags.take(3).joinToString("  "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Note actions") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(if (note.pinned) "Unpin note" else "Pin note") }, onClick = { expanded = false; onPin() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { expanded = false; onDuplicate() })
                    if (note.deleted) {
                        DropdownMenuItem(text = { Text("Restore") }, onClick = { expanded = false; onRestore() })
                        DropdownMenuItem(text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onDelete() })
                    } else if (!note.archived) {
                        DropdownMenuItem(text = { Text("Archive") }, onClick = { expanded = false; onArchive() })
                        DropdownMenuItem(text = { Text("Move to trash") }, onClick = { expanded = false; onTrash() })
                    } else {
                        DropdownMenuItem(text = { Text("Move to notes") }, onClick = { expanded = false; onRestore() })
                    }
                }
            }
        }
    }
}
private fun appendRevision(prefs: android.content.SharedPreferences, note: FolioNote) {
    val key = "history_${note.id}"
    val history = runCatching { org.json.JSONArray(prefs.getString(key, "[]") ?: "[]") }.getOrDefault(org.json.JSONArray())
    val now = System.currentTimeMillis()
    val lastSaved = if (history.length() == 0) 0L else history.optJSONObject(history.length() - 1)?.optLong("savedAtMs", 0L) ?: 0L
    if (lastSaved > 0L && now - lastSaved < 30_000L) return
    val tags = org.json.JSONArray().apply { note.tags.forEach { put(it) } }
    history.put(org.json.JSONObject().put("id", note.id).put("title", note.title).put("body", note.body)
        .put("folder", note.folder).put("tags", tags).put("isPinned", note.pinned)
        .put("isArchived", note.archived).put("isDeleted", note.deleted)
        .put("updatedAtMs", note.updatedAt).put("createdAtMs", note.createdAt).put("savedAtMs", now))
    while (history.length() > 50) history.remove(0)
    prefs.edit().putString(key, history.toString()).apply()
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
            id = item.optString("id").takeIf { it.isNotBlank() }?.let { rawId -> runCatching { UUID.fromString(rawId).toString() }.getOrDefault(rawId) } ?: UUID.randomUUID().toString(),
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

@Composable
private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    var inCodeBlock = false
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val clean = line.trimStart()
            if (clean.startsWith("```")) {
                inCodeBlock = !inCodeBlock
            } else if (inCodeBlock) {
                Text(line, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            } else if (clean.isBlank()) {
                Spacer(Modifier.height(2.dp))
            } else if (clean.matches(Regex("^(---|___|\\*\\*\\*)$"))) {
                HorizontalDivider()
            } else {
                val headingLevel = clean.takeWhile { it == '#' }.length
                val isQuote = clean.startsWith("> ")
                val task = Regex("^[-*+] \\[([ xX])\\] (.*)$").matchEntire(clean)
                val bullet = Regex("^[-*+] (.*)$").matchEntire(clean)
                val numbered = Regex("^\\d+[.)] (.*)$").matchEntire(clean)
                val content = when {
                    headingLevel in 1..6 -> clean.drop(headingLevel).trimStart()
                    isQuote -> clean.removePrefix("> ")
                    task != null -> (if (task.groupValues[1].equals("x", true)) "☑ " else "☐ ") + task.groupValues[2]
                    bullet != null -> "• " + bullet.groupValues[1]
                    numbered != null -> clean
                    else -> clean
                }
                Text(markdownInline(content),
                    modifier = Modifier.fillMaxWidth().then(if (isQuote) Modifier.padding(start = 10.dp) else Modifier),
                    style = when {
                        headingLevel == 1 -> MaterialTheme.typography.headlineMedium
                        headingLevel == 2 -> MaterialTheme.typography.headlineSmall
                        headingLevel == 3 -> MaterialTheme.typography.titleLarge
                        headingLevel in 4..6 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    color = if (isQuote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun markdownInline(value: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val pattern = Regex("\\*\\*(.+?)\\*\\*|(?<!\\*)\\*([^*]+)\\*|(?<!_)_([^_]+)_|`([^`]+)`")
        var cursor = 0
        pattern.findAll(value).forEach { match ->
            append(value.substring(cursor, match.range.first))
            val group = (1..4).firstOrNull { match.groups[it] != null }
            if (group != null) {
                val span = match.groups[group]!!.value
                val style = when (group) {
                    1 -> androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    2, 3 -> androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    else -> androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = androidx.compose.ui.graphics.Color(0x223F8F5F))
                }
                pushStyle(style); append(span); pop()
            }
            cursor = match.range.last + 1
        }
        append(value.substring(cursor))
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
