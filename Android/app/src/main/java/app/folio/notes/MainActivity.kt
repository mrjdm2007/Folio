package app.folio.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

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
    var editingTitle by remember { mutableStateOf("") }
    var editingBody by remember { mutableStateOf("") }

    fun persist(updated: List<FolioNote>) {
        notes = updated
        prefs.edit().putString("notes", encodeNotes(updated)).apply()
    }
    fun newNote() {
        val note = FolioNote(java.util.UUID.randomUUID().toString(), "Untitled Note", "")
        persist(listOf(note) + notes)
        selectedId = note.id
        editingTitle = note.title
        editingBody = note.body
    }

    val filtered = notes.filter {
        it.title.contains(query, true) || it.body.contains(query, true)
    }.sortedByDescending { it.pinned }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folio", style = MaterialTheme.typography.headlineMedium) },
                actions = { IconButton(onClick = { newNote() }) { Icon(Icons.Default.Add, "New note") } }
            )
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Column(Modifier.weight(0.9f)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    items(filtered, key = { it.id }) { note ->
                        ListItem(
                            headlineContent = { Text(note.title.ifBlank { "Untitled Note" }) },
                            supportingContent = { Text(note.body.ifBlank { "No additional text" }, maxLines = 2) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedId = note.id
                                editingTitle = note.title
                                editingBody = note.body
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (selectedId != null) {
                Column(Modifier.weight(1.1f)) {
                    OutlinedTextField(
                        value = editingTitle,
                        onValueChange = {
                            editingTitle = it
                            persist(notes.map { n -> if (n.id == selectedId) n.copy(title = it) else n })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title") },
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingBody,
                        onValueChange = {
                            editingBody = it
                            persist(notes.map { n -> if (n.id == selectedId) n.copy(body = it) else n })
                        },
                        modifier = Modifier.fillMaxSize(),
                        placeholder = { Text("Start writing…") }
                    )
                }
            } else {
                Box(Modifier.weight(1.1f).fillMaxHeight()) {
                    Text("Select a note or create one.", modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

private data class FolioNote(val id: String, val title: String, val body: String, val pinned: Boolean = false)

private fun loadNotes(raw: String): List<FolioNote> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        FolioNote(item.getString("id"), item.optString("title"), item.optString("body"), item.optBoolean("pinned"))
    }
} catch (_: Exception) { emptyList() }

private fun encodeNotes(notes: List<FolioNote>): String {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body).put("pinned", note.pinned))
    }
    return array.toString()
}
