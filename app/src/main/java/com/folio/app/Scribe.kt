package com.folio.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Saves an AI answer as a new topic under Folio AI > Saved answers. */
fun saveAnswerAsNote(s: Store, m: AiMsg) {
    val course = s.data.nodes.firstOrNull { it.kind == "course" && it.title == "Folio AI" && !it.deleted }?.id ?: s.add(null, "course", "Folio AI")
    val chapter = s.data.nodes.firstOrNull { it.kind == "chapter" && it.parentId == course && !it.deleted }?.id ?: s.add(course, "chapter", "Saved answers")
    val id = s.add(chapter, "topic", m.question.ifBlank { "AI answer" }.take(60))
    val sources = (m.notes.map { "Note: " + it.path } + m.web.map { "Web: ${it.title} ${it.url}" }).joinToString("\n")
    s.editBlocks(id) { aiToBlocks(m.text) + (if (sources.isNotBlank()) listOf(Block(type = "h2", text = "Sources"), Block(text = sources)) else emptyList()) }
}

@Composable fun ScribeScreen(s: Store, open: (String) -> Unit) {
    val cfg = s.aiCfg
    var showSetup by remember { mutableStateOf(cfg.key.isBlank() || cfg.model.isBlank()) }
    var mode by remember { mutableStateOf("Explain") }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(s.aiMessages.size, s.aiBusy) {
        if (s.aiMessages.isNotEmpty()) listState.animateScrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Folio AI", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Chip("Setup", showSetup) { showSetup = !showSetup }
            Chip("New chat") { s.newChat() }
        }
        s.aiPinned?.let { id -> s.node(id)?.let { n -> Chip("Asking about: ${n.title}  ✕") { s.aiPinned = null } } }
        if (showSetup) AiSetup(s) { showSetup = false }
        Row(Modifier.horizontalScroll(rememberScrollState())) { Scribe.MODES.keys.forEach { m -> Chip(m, mode == m) { mode = m } } }
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (s.aiMessages.isEmpty()) item {
                Text("Ask anything. I look through your notes first, then the internet, and answer here.\n\n" +
                    "Try: “Explain Newton's law of cooling using my notes”, or “Quiz me on skin depth”.\n\n" +
                    "To ask about one page, open it and choose “Ask Folio AI about this page” in its ⋮ menu.", color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            items(s.aiMessages.toList()) { m -> Bubble(s, m, open) }
            if (s.aiBusy) item { Text("Reading your notes and the web…", color = Gold, modifier = Modifier.padding(8.dp)) }
            if (s.aiError.isNotBlank()) item { Text(s.aiError, color = Color(0xFFE57373), modifier = Modifier.padding(8.dp)) }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 6.dp)) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask about your notes or any topic…") }, maxLines = 4,
                shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f))
            Ico(Icons.Filled.Send, "Send", Gold, 48.dp) { if (input.isNotBlank() && !s.aiBusy) { s.askAi(input.trim(), mode); input = "" } }
        }
    }
}

@Composable private fun Bubble(s: Store, m: AiMsg, open: (String) -> Unit) {
    val ctx = LocalContext.current
    if (m.role == "user") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(m.text, Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(Gold.copy(alpha = 0.18f)).padding(12.dp))
        }
    } else GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionContainer { Text(md(m.text), lineHeight = 22.sp) }
            if (m.notes.isNotEmpty()) {
                SectionTitle("From your notes")
                m.notes.forEachIndexed { i, n -> Chip("N${i + 1} · ${n.path}") { open(n.id) } }
            }
            if (m.web.isNotEmpty()) {
                SectionTitle("From the internet")
                m.web.forEachIndexed { i, w -> Text("W${i + 1} · ${w.title}\n${w.url}", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f)) }
            }
            val cards = if (m.mode == "Flashcards") parseCards(m.text) else emptyList()
            val qs = if (m.mode == "Quiz me") parseQuestions(m.text, m.question.take(40)) else emptyList()
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (cards.isNotEmpty()) Chip("Save ${cards.size} flashcards") {
                    cards.forEach { (f, b) -> s.addCard("Folio AI", f, b) }
                    Toast.makeText(ctx, "Saved to the “Folio AI” deck", Toast.LENGTH_SHORT).show()
                }
                if (qs.isNotEmpty()) Chip("Save ${qs.size} questions") {
                    s.update { d -> d.copy(questions = d.questions + qs.filter { q -> d.questions.none { it.text == q.text } }) }
                    Toast.makeText(ctx, "Saved to the question bank", Toast.LENGTH_SHORT).show()
                }
                Chip("Save as note") { saveAnswerAsNote(s, m); Toast.makeText(ctx, "Saved in Folio AI › Saved answers", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable private fun AiSetup(s: Store, onDone: () -> Unit) {
    val c0 = s.aiCfg
    var prov by remember { mutableStateOf(c0.provider) }
    var key by remember { mutableStateOf(c0.key) }
    var model by remember { mutableStateOf(c0.model) }
    var base by remember { mutableStateOf(c0.base) }
    var web by remember { mutableStateOf(c0.web) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun cur() = AiCfg(prov, key.trim(), model.trim(), base.trim(), web)
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Provider", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Gemini", "Groq", "OpenRouter", "Custom").forEach { p -> Chip(p, prov == p) { prov = p; models = emptyList() } }
            }
            if (prov == "Custom") OutlinedTextField(base, { base = it }, label = { Text("Base URL, e.g. https://host/v1") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Chip("Fetch models") {
                    scope.launch {
                        status = "Fetching models…"
                        try {
                            val l = withContext(Dispatchers.IO) { Ai.models(cur()) }
                            models = l
                            if (model.isBlank()) model = l.firstOrNull { "flash" in it && "lite" !in it && "image" !in it && "tts" !in it && "live" !in it } ?: l.firstOrNull() ?: ""
                            status = "${l.size} models found. Tap one to choose it."
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message ?: "Could not fetch models" }
                    }
                }
                Chip("Test") {
                    scope.launch {
                        status = "Testing…"
                        try {
                            val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with the single word OK."))
                            val r = withContext(Dispatchers.IO) { Ai.chat(cur(), msgs) }
                            status = "It works. The model replied: ${r.take(40)}"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message ?: "Test failed" }
                    }
                }
            }
            if (models.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { models.take(40).forEach { m -> Chip(m, model == m) { model = m } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(web, { web = it })
                Text("  Use the internet (Wikipedia, web search, links you paste)", fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = Gold)
            Text("Your key stays on this phone and is not included in backups. Your question and the note excerpts it uses are sent to the provider you choose.",
                fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            Button({ s.saveAiCfg(cur()); onDone() }) { Text("Save") }
        }
    }
}
