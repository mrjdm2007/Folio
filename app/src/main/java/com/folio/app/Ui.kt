package com.folio.app

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------------- Theme: graphite + champagne gold ----------------
val Gold = Color(0xFFD4B26A)
val Graphite = Color(0xFF17191C)
val Panel = Color(0xFF24272B)
val Ink = Color(0xFFECE6D8)
val BLOCK_TYPES = listOf("text", "h1", "h2", "bullet", "number", "check", "quote", "code", "latex", "table", "flash")
val TYPE_LABEL = mapOf("text" to "Text", "h1" to "Title", "h2" to "Heading", "bullet" to "Bullets", "number" to "Numbers",
    "check" to "Checklist", "quote" to "Quote", "code" to "Code", "latex" to "Equation", "table" to "Table", "flash" to "Flashcard")

// Claude's own typeface is proprietary, so the app uses the system serif for headings and reading.
// To use another font, add a .ttf to res/font and replace FontFamily.Serif with FontFamily(Font(R.font.yourfont)).
@Composable fun FolioTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Gold, onPrimary = Graphite, primaryContainer = Color(0xFF3A3326), onPrimaryContainer = Gold,
        secondary = Gold, background = Graphite, onBackground = Ink, surface = Panel, onSurface = Ink,
        surfaceVariant = Panel, onSurfaceVariant = Ink.copy(alpha = 0.7f), outline = Color(0xFF4A4F55)),
    typography = Typography(
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp))
) { Surface(Modifier.fillMaxSize(), color = Graphite, contentColor = Ink) {
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(Gold.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.9f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF5B7DB1).copy(alpha = 0.10f), Color.Transparent), center = Offset(0f, size.height), radius = size.width))
    }) { content() }
} }

// ---------------- small building blocks ----------------
@Composable fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(2.dp).clip(RoundedCornerShape(50))
        .background(if (selected) Gold.copy(alpha = 0.18f) else Color.Transparent)
        .then(if (selected) Modifier.border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(50)) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, color = if (selected) Gold else Ink.copy(alpha = 0.75f), fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable fun Ico(icon: ImageVector, desc: String, tint: Color = Ink.copy(alpha = 0.8f), size: Dp = 40.dp, onClick: () -> Unit) =
    IconButton(onClick, Modifier.size(size)) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }

/** Subtle "crystal glass": translucent gradient fill plus a light edge that fades out. */
fun Modifier.glass(shape: Shape): Modifier = this.clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.04f))))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f), Gold.copy(alpha = 0.18f))), shape)

@Composable fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp), content: @Composable BoxScope.() -> Unit) =
    Box(modifier.glass(shape), content = content)

@Composable fun SectionTitle(text: String) =
    Text(text.uppercase(), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))

@Composable fun StatTile(label: String, value: String, modifier: Modifier = Modifier) =
    GlassCard(modifier, RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Gold)
        }
    }

@Composable fun GoldProgress(progress: () -> Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator(progress = progress, color = Gold, trackColor = Panel,
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))

@Composable fun FormDialog(title: String, labels: List<String>, initial: List<String> = labels.map { "" },
                           onOk: (List<String>) -> Unit, onClose: () -> Unit) {
    val vals = remember { mutableStateListOf(*initial.toTypedArray()) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, containerColor = Color(0xFF202327).copy(alpha = 0.95f), shape = RoundedCornerShape(24.dp),
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            labels.forEachIndexed { i, l -> OutlinedTextField(vals[i], { vals[i] = it }, label = { Text(l) }, shape = RoundedCornerShape(12.dp)) } } },
        confirmButton = { TextButton({ onOk(vals.toList()); onClose() }) { Text("OK") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } })
}

// ---------------- App shell: bottom bar on phones, side rail on tablets / landscape ----------------
@Composable fun App(s: Store) {
    var tab by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<ViewTarget?>(null) }
    val p = page?.takeIf { s.node(it) != null }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val ctx = LocalContext.current
    LaunchedEffect(s.incoming) { s.incoming?.let { view = it; s.incoming = null } }
    Box(Modifier.fillMaxSize()) {
    if (view != null) Viewer(s, view!!) { view = null }
    else if (p != null) {
        if (reading) key(p) {
            Reader(s, p, { view = it }, { title ->
                val t = s.findByTitle(title)
                if (t != null) page = t.id else Toast.makeText(ctx, "No topic named \"$title\"", Toast.LENGTH_SHORT).show()
            }) { reading = false }
        } else key(p) { Editor(s, p, onRead = { reading = true }, onFile = { view = it }, onClose = { page = null }) }
    } else {
        val items = listOf("Library" to Icons.Filled.LibraryBooks, "Study" to Icons.Filled.School, "Tools" to Icons.Filled.Build, "More" to Icons.Filled.MoreHoriz)
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                    when (tab) { 0 -> Library(s) { page = it }; 1 -> StudyScreen(s); 2 -> ToolsScreen(s); else -> MoreScreen(s, { page = it }) { view = it } }
                }
            }
        }
        val navColors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Gold.copy(alpha = 0.18f))
        if (wide) Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = Color.Transparent, modifier = Modifier.glass(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))) {
                items.forEachIndexed { i, (t, ic) ->
                    NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Gold.copy(alpha = 0.18f)))
                }
            }
            Box(Modifier.weight(1f)) { body() }
        } else Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Color.Transparent, modifier = Modifier.glass(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))) {
                items.forEachIndexed { i, (t, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) }, colors = navColors)
                }
            }
        }) { pad -> Box(Modifier.padding(pad)) { body() } }
    }
    TimerPill(s)
    }
}

// ---------------- Library ----------------
@Composable fun Library(s: Store, open: (String) -> Unit) {
    var cur by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(s.data.userName.isBlank() && !s.data.nameAsked) }
    val node = cur?.let { s.node(it) }
    LaunchedEffect(node == null && cur != null) { if (node == null && cur != null) cur = null }
    val kind = when (node?.kind) { null -> "course"; "course" -> "chapter"; "chapter" -> "topic"; else -> "page" }
    BackHandler(cur != null) { cur = node?.parentId }
    fun go(n: Node) { if (n.kind == "page" || (n.kind == "topic" && s.children(n.id).isEmpty())) open(n.id) else { cur = n.id; q = "" } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (cur != null) Ico(Icons.Filled.ArrowBack, "Up") { cur = node?.parentId }
                Text(node?.title ?: "Library", style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
                Ico(Icons.Filled.Timer, "Focus timer", if (s.timerRunning) Gold else Ink.copy(alpha = 0.8f)) { s.timerToggle() }
            }
            if (cur != null) Text(s.path(cur!!), color = Gold, fontSize = 12.sp, maxLines = 1)
            if (cur == null) Greeting(s) { askName = true }
            OutlinedTextField(q, { q = it }, placeholder = { Text("Search titles, notes, #tags") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            val tags = s.allTags()
            if (tags.isNotEmpty() && q.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState())) { tags.forEach { t -> Chip("#$t") { q = "#$t" } } }
            val results = if (q.isNotBlank()) s.search(q) else emptyList()
            val kids = s.children(cur)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (q.isNotBlank()) {
                    if (results.isEmpty()) item { Text("No matches.", color = Ink.copy(alpha = 0.6f)) }
                    items(results) { NodeRow(s, it) { go(it) } }
                } else {
                    if (cur == null) {
                        val rec = s.recent(4)
                        if (rec.isNotEmpty()) { item { SectionTitle("Recent") }; items(rec) { NodeRow(s, it) { go(it) } }; item { SectionTitle("Courses") } }
                    }
                    if (kids.isEmpty()) item { Text("Nothing here yet. Tap “New $kind” to add one.", color = Ink.copy(alpha = 0.6f)) }
                    items(kids) { NodeRow(s, it) { go(it) } }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { add = true }, containerColor = Gold, contentColor = Graphite,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New $kind") })
    }
    if (add) FormDialog("New $kind", listOf("Title"), onOk = { v ->
        val id = s.add(cur, kind, v[0])
        if (kind == "topic" || kind == "page") open(id) else cur = id   // a new topic opens straight into writing
    }, onClose = { add = false })
    if (askName) FormDialog("What should I call you?", listOf("Your name"), listOf(s.data.userName),
        onOk = { v -> s.setName(v[0]) }, onClose = { askName = false; s.markNameAsked() })
}

@Composable fun NodeRow(s: Store, n: Node, go: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val icon = when (n.kind) { "course" -> Icons.Filled.School; "chapter" -> Icons.Filled.Book; "topic" -> Icons.Filled.Label; else -> Icons.Filled.Description }
    val preview = n.blocks.firstOrNull { it.type == "text" && it.text.isNotBlank() }?.text?.take(70)
    val sub = when {
        n.kind == "page" -> preview ?: "Empty page"
        n.kind == "topic" && s.children(n.id).isEmpty() -> preview ?: "Empty topic"
        else -> "${s.children(n.id).size} items"
    }
    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Row(Modifier.clickable(onClick = go).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Gold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(n.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(sub, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f), maxLines = 1)
                if (n.tags.isNotEmpty()) Text(n.tags.take(3).joinToString(" ") { "#$it" }, fontSize = 11.sp, color = Gold)
            }
            Ico(if (n.bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, "Bookmark", if (n.bookmarked) Gold else Ink.copy(alpha = 0.6f)) {
                s.editNode(n.id) { it.copy(bookmarked = !it.bookmarked) }
            }
            Box {
                Ico(Icons.Filled.MoreVert, "More") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { s.move(n.id, true); menu = false })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { s.move(n.id, false); menu = false })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { s.duplicate(n.id); menu = false })
                    DropdownMenuItem(text = { Text("Move to trash") }, onClick = { s.trash(n.id); menu = false })
                }
            }
        }
    }
}

// ---------------- Editor ----------------
@Composable fun Editor(s: Store, id: String, onRead: () -> Unit, onFile: (ViewTarget) -> Unit, onClose: () -> Unit) {
    val n = s.node(id)!!
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var hist by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf<String?>(null) }
    var cmd by remember { mutableStateOf<FmtCmd?>(null) }
    var linkPick by remember { mutableStateOf(false) }
    var tagText by remember(id) { mutableStateOf(n.tags.joinToString(", ")) }
    fun addAttachment(uri: Uri, asImage: Boolean) {
        s.attach(uri)?.let { (stored, name) ->
            val img = asImage || ctx.contentResolver.getType(uri)?.startsWith("image/") == true
            val att = Block(type = if (img) "image" else "file", text = stored, extra = name)
            val tail = Block()   // an empty text block after it, so you can keep typing
            s.insertBlocks(id, active, listOf(att, tail)); active = tail.id; fresh = tail.id
        }
    }
    /** Called when an image/PDF is pasted into the middle of a paragraph: text before, the file, then text after. */
    fun pasteFile(blockId: String, before: String, after: String) {
        val uri = clipFileUri(ctx) ?: return
        s.attach(uri)?.let { (stored, name) ->
            val img = ctx.contentResolver.getType(uri)?.startsWith("image/") == true
            val att = Block(type = if (img) "image" else "file", text = stored, extra = name)
            val tail = Block(text = after)
            s.replaceBlock(id, blockId, before, listOf(att, tail)); active = tail.id; fresh = tail.id
        }
    }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { addAttachment(it, true) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { addAttachment(it, false) } }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> exportPagePdf(s, s.node(id)!!, o) } }
    }
    fun paste() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        if (uri != null) addAttachment(uri, false)
        else item?.text?.let { t -> s.insertBlocks(id, active, listOf(Block(text = t.toString()))) }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxSize().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.Filled.ArrowBack, "Back", onClick = onClose)
                Text(s.path(id).substringBeforeLast(" › ", ""), color = Gold, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Ico(Icons.Filled.Save, "Save snapshot") { s.editNode(id) { it.copy(history = it.history + Snap(now(), it.title, it.blocks)) } }
                Ico(Icons.Filled.History, "History") { hist = true }
                Ico(Icons.Filled.Timer, "Focus timer", if (s.timerRunning) Gold else Ink.copy(alpha = 0.8f)) { s.timerToggle() }
                Ico(Icons.Filled.MenuBook, "Reader", Gold, onClick = onRead)
                Box {
                    Ico(Icons.Filled.MoreVert, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Merge with next page") }, onClick = { s.mergeNext(id); menu = false })
                        DropdownMenuItem(text = { Text("Export as PDF") }, onClick = { exportPdf.launch(n.title.ifBlank { "page" } + ".pdf"); menu = false })
                    }
                }
            }
            val titleStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            BasicTextField(n.title, { v -> s.editNode(id) { it.copy(title = v) } }, textStyle = titleStyle, cursorBrush = SolidColor(Gold),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                decorationBox = { inner -> Box { if (n.title.isEmpty()) Text("Untitled", style = titleStyle.copy(color = Ink.copy(alpha = 0.3f))); inner() } })
            val tagStyle = TextStyle(fontSize = 13.sp, color = Gold)
            BasicTextField(tagText, { v ->
                tagText = v
                s.editNode(id) { it.copy(tags = v.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
            }, textStyle = tagStyle, cursorBrush = SolidColor(Gold), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                decorationBox = { inner -> Box { if (tagText.isEmpty()) Text("Add tags, separated by commas", style = tagStyle.copy(color = Gold.copy(alpha = 0.4f))); inner() } })
            LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)) {
                itemsIndexed(n.blocks, key = { _, b -> b.id }) { i, b ->
                    BlockEditor(s, id, n, i, b, numberOf(n.blocks, i), active == b.id, fresh == b.id, { active = b.id; if (fresh == b.id) fresh = null }, onFile, { bid, bf, af -> pasteFile(bid, bf, af) }, cmd, { cmd = null })
                }
            }
            if (active != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Ico(Icons.Filled.FormatBold, "Bold") { cmd = FmtCmd(now(), "**") }
                Ico(Icons.Filled.FormatItalic, "Italic") { cmd = FmtCmd(now(), "*") }
                Ico(Icons.Filled.Code, "Code") { cmd = FmtCmd(now(), "`") }
                Ico(Icons.Filled.Highlight, "Highlight") { cmd = FmtCmd(now(), "==") }
                Ico(Icons.Filled.Link, "Link to a topic") { linkPick = true }
            }
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), RoundedCornerShape(16.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    BLOCK_TYPES.forEach { t ->
                        Chip("+ " + (TYPE_LABEL[t] ?: t)) {
                            val nb = Block(type = t)
                            val at = (n.blocks.indexOfFirst { b -> b.id == active }.takeIf { it >= 0 } ?: (n.blocks.size - 1)) + 1
                            s.insertBlocks(id, active, listOf(nb)); active = nb.id; fresh = nb.id
                            scope.launch { listState.animateScrollToItem(at) }
                        }
                    }
                    Chip("+ Image") { pickImg.launch("image/*") }
                    Chip("+ File / PDF") { pickFile.launch(arrayOf("*/*")) }
                    Chip("Paste") { paste() }
                }
            }
        }
    }
    if (linkPick) TopicPicker(s, "Link to topic", onPick = { cmd = FmtCmd(now(), insert = "[[${it.title}]]") }, onClose = { linkPick = false })
    if (hist) AlertDialog(onDismissRequest = { hist = false }, containerColor = Color(0xFF202327).copy(alpha = 0.95f), shape = RoundedCornerShape(24.dp), title = { Text("Revision history") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (n.history.isEmpty()) Text("No snapshots yet. Tap the save icon to keep a version.")
                n.history.reversed().forEach { sn ->
                    Chip("${fmt(sn.time)} · ${sn.title} — restore") { s.editNode(id) { it.copy(title = sn.title, blocks = sn.blocks) }; hist = false }
                }
            }
        }, confirmButton = { TextButton({ hist = false }) { Text("Close") } })
}

@Composable fun BlockEditor(s: Store, pid: String, n: Node, i: Int, b: Block, num: Int, active: Boolean, fresh: Boolean, onActive: () -> Unit, onFile: (ViewTarget) -> Unit, onPasteFile: (String, String, String) -> Unit, cmd: FmtCmd?, onCmdDone: () -> Unit) {
    fun upd(nb: Block) = s.editBlocks(pid) { l -> l.map { x -> if (x.id == b.id) nb else x } }
    var dy by remember { mutableFloatStateOf(0f) }
    var hgt by remember { mutableIntStateOf(1) }
    val iNow by rememberUpdatedState(i)
    val countNow by rememberUpdatedState(n.blocks.size)
    Column(Modifier.fillMaxWidth().graphicsLayer { translationY = dy }.onSizeChanged { hgt = it.height }.clip(RoundedCornerShape(10.dp))
        .background(if (active) Panel else Color.Transparent)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onActive)
        .padding(start = (b.indent * 22).dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (b.type) {
            "check" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(b.checked, { upd(b.copy(checked = it)) }, colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Graphite))
                BlockField(b.text, "To-do", b.type, onActive, fresh, onPasteFile = { bf, af -> onPasteFile(b.id, bf, af) }, cmd = if (active) cmd else null, onCmdDone = onCmdDone) { upd(b.copy(text = it)) }
            }
            "bullet" -> Row { Text("•", color = Gold, modifier = Modifier.padding(end = 10.dp, top = 1.dp)); BlockField(b.text, "List item", b.type, onActive, fresh, onPasteFile = { bf, af -> onPasteFile(b.id, bf, af) }, cmd = if (active) cmd else null, onCmdDone = onCmdDone) { upd(b.copy(text = it)) } }
            "number" -> Row { Text("$num.", color = Gold, modifier = Modifier.padding(end = 10.dp, top = 1.dp)); BlockField(b.text, "List item", b.type, onActive, fresh, onPasteFile = { bf, af -> onPasteFile(b.id, bf, af) }, cmd = if (active) cmd else null, onCmdDone = onCmdDone) { upd(b.copy(text = it)) } }
            "quote" -> Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Gold)); Spacer(Modifier.width(10.dp))
                BlockField(b.text, "Quote", b.type, onActive, fresh, onPasteFile = { bf, af -> onPasteFile(b.id, bf, af) }, cmd = if (active) cmd else null, onCmdDone = onCmdDone) { upd(b.copy(text = it)) }
            }
            "code" -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF101214)).padding(10.dp)) {
                BlockField(b.text, "Code", b.type, onActive, fresh) { upd(b.copy(text = it)) }
            }
            "latex" -> Column {
                BlockField(b.text, "LaTeX equation, e.g. \\nabla \\times E = -\\partial B/\\partial t", b.type, onActive, fresh) { upd(b.copy(text = it)) }
                if (b.text.isNotBlank()) MathView(b.text, Ink)
            }
            "table" -> BlockField(b.text, "Header 1 | Header 2\nvalue | value", b.type, onActive, fresh) { upd(b.copy(text = it)) }
            "flash" -> Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B2A26)).padding(10.dp)) {
                Text("FLASHCARD", color = Gold, fontSize = 11.sp, letterSpacing = 1.sp)
                BlockField(b.text, "Front / question", "text", onActive, fresh) { upd(b.copy(text = it)) }
                BlockField(b.extra, "Back / answer", "text", onActive) { upd(b.copy(extra = it)) }
                Chip("Save to deck") { s.addCard(n.title, b.text, b.extra) }
            }
            "image" -> Column { AttachmentImage(s, b.text); BlockField(b.extra, "Caption", "text", onActive) { upd(b.copy(extra = it)) } }
            "file" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${b.extra}", Modifier.weight(1f)); Chip("Open") { onFile(ViewTarget(b.text, b.extra)) }
            }
            else -> BlockField(b.text, "Write something…", b.type, onActive, fresh, onPasteFile = { bf, af -> onPasteFile(b.id, bf, af) }, cmd = if (active) cmd else null, onCmdDone = onCmdDone) { upd(b.copy(text = it)) }
        }
        if (active) Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (b.type != "image" && b.type != "file") BLOCK_TYPES.forEach { t -> Chip(TYPE_LABEL[t] ?: t, b.type == t) { upd(b.copy(type = t)) } }
            Icon(Icons.Filled.DragHandle, "Drag to reorder", tint = Gold, modifier = Modifier.size(36.dp).padding(8.dp).pointerInput(Unit) {
                detectDragGestures(onDragEnd = { dy = 0f }, onDragCancel = { dy = 0f }) { ch, drag ->
                    ch.consume(); dy += drag.y
                    val hh = hgt.toFloat().coerceAtLeast(1f)
                    if (dy > hh * 0.6f && iNow < countNow - 1) { s.editBlocks(pid) { l -> swap(l, iNow, iNow + 1) }; dy -= hh }
                    else if (dy < -hh * 0.6f && iNow > 0) { s.editBlocks(pid) { l -> swap(l, iNow, iNow - 1) }; dy += hh }
                }
            })
            Ico(Icons.Filled.FormatIndentDecrease, "Outdent", size = 36.dp) { upd(b.copy(indent = maxOf(0, b.indent - 1))) }
            Ico(Icons.Filled.FormatIndentIncrease, "Indent", size = 36.dp) { upd(b.copy(indent = minOf(4, b.indent + 1))) }
            Ico(Icons.Filled.ArrowUpward, "Move up", size = 36.dp) { s.editBlocks(pid) { l -> swap(l, i, i - 1) } }
            Ico(Icons.Filled.ArrowDownward, "Move down", size = 36.dp) { s.editBlocks(pid) { l -> swap(l, i, i + 1) } }
            Ico(Icons.Filled.ContentCopy, "Duplicate", size = 36.dp) { s.editBlocks(pid) { l -> l.toMutableList().also { it.add(i + 1, b.copy(id = uid())) } } }
            Ico(Icons.Filled.ContentCut, "Split page here", size = 36.dp) { s.split(pid, i) }
            Ico(Icons.Filled.Delete, "Delete block", Color(0xFFE57373), 36.dp) { s.editBlocks(pid) { l -> l.filter { x -> x.id != b.id } } }
        }
    }
}

private val mdVt = VisualTransformation { TransformedText(mdLive(it.text), OffsetMapping.Identity) }

@OptIn(ExperimentalFoundationApi::class)
@Composable fun BlockField(v: String, label: String, type: String, onFocus: () -> Unit = {}, autoFocus: Boolean = false,
                           onPasteFile: ((String, String) -> Unit)? = null, cmd: FmtCmd? = null, onCmdDone: () -> Unit = {},
                           onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val style = when (type) {
        "h1" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        "h2" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        "code", "latex", "table" -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Ink)
        "quote" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = Ink)
        else -> TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
    }
    val plain = type == "code" || type == "latex" || type == "table"
    val fr = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(v, TextRange(v.length))) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    if (tfv.text != v) tfv = tfv.copy(text = v)
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    // keep the line you are typing on visible above the keyboard
    LaunchedEffect(layout, tfv.selection) {
        val l = layout
        if (focused && l != null && tfv.selection.end <= l.layoutInput.text.length)
            runCatching { bring.bringIntoView(l.getCursorRect(tfv.selection.end).inflate(36f)) }
    }
    // formatting toolbar: wrap the selection in **, *, `, == or insert a [[link]]
    LaunchedEffect(cmd) {
        val c = cmd ?: return@LaunchedEffect
        val a = minOf(tfv.selection.start, tfv.selection.end); val b = maxOf(tfv.selection.start, tfv.selection.end)
        val t = tfv.text
        val (nt, sel) = if (c.insert.isNotEmpty()) ((t.substring(0, a) + c.insert + t.substring(b)) to TextRange(a + c.insert.length))
                        else ((t.substring(0, a) + c.mark + t.substring(a, b) + c.mark + t.substring(b)) to TextRange(a + c.mark.length, b + c.mark.length))
        tfv = TextFieldValue(nt, sel); onChange(nt); onCmdDone()
    }
    val base = LocalClipboardManager.current
    val cb = remember(base) { FolioClipboard(base, ctx) }
    val field: @Composable () -> Unit = {
        BasicTextField(tfv, { nv ->
            val i = nv.text.indexOf(PASTE_MARK)
            if (i >= 0 && onPasteFile != null) {
                tfv = nv.copy(text = nv.text.replace(PASTE_MARK, ""))
                onPasteFile(nv.text.substring(0, i), nv.text.substring(i + PASTE_MARK.length))
            } else { tfv = nv; if (nv.text != v) onChange(nv.text) }
        }, textStyle = style, cursorBrush = SolidColor(Gold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            visualTransformation = if (plain) VisualTransformation.None else mdVt,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().focusRequester(fr).bringIntoViewRequester(bring)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
            decorationBox = { inner -> Box { if (v.isEmpty()) Text(label, style = style.copy(color = Ink.copy(alpha = 0.3f))); inner() } })
    }
    if (onPasteFile != null) CompositionLocalProvider(LocalClipboardManager provides cb) { field() } else field()
}

// ---------------- Reader ----------------
@Composable fun Reader(s: Store, id: String, onFile: (ViewTarget) -> Unit, onLink: (String) -> Unit, onClose: () -> Unit) {
    val n = s.node(id)!!
    var size by remember { mutableFloatStateOf(18f) }
    var width by remember { mutableFloatStateOf(640f) }
    var theme by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(false) }
    val themes = listOf(Color(0xFFF6F1E7) to Color(0xFF2B2A28), Color(0xFFE9DDC3) to Color(0xFF3A2E1C), Color(0xFF16181B) to Color(0xFFE6E1D6))
    val (bg, fg) = themes[theme]
    val st = rememberLazyListState(initialFirstVisibleItemIndex = n.readPos.coerceIn(0, n.blocks.size))
    val prog = if (n.blocks.isEmpty()) 0f else (st.firstVisibleItemIndex + 1f) / (n.blocks.size + 1)
    val words = n.blocks.sumOf { b -> b.text.split(" ").count { w -> w.isNotBlank() } }
    val mins = maxOf(1, words / 200)
    LaunchedEffect(st) { snapshotFlow { st.firstVisibleItemIndex }.collect { i -> if (i != s.node(id)?.readPos) s.setReadPos(id, i) } }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(bg)) {
        GoldProgress({ prog })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.Filled.ArrowBack, "Back", fg, onClick = onClose)
            Spacer(Modifier.weight(1f))
            Text("${(prog * 100).toInt()}%", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
            Ico(if (n.bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, "Bookmark", if (n.bookmarked) Gold else fg) {
                s.editNode(id) { it.copy(bookmarked = !it.bookmarked) }
            }
            Ico(Icons.Filled.TextFields, "Appearance", fg) { panel = !panel }
        }
        if (panel) Surface(color = fg.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                val sc = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                Text("Font size", color = fg, fontSize = 13.sp); Slider(size, { size = it }, valueRange = 14f..30f, colors = sc)
                Text("Reading width", color = fg, fontSize = 13.sp); Slider(width, { width = it }, valueRange = 360f..900f, colors = sc)
                Row { listOf("Paper", "Sepia", "Night").forEachIndexed { i, t -> TextButton({ theme = i }) { Text(t, color = if (theme == i) Gold else fg) } } }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = st, horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.widthIn(max = width.dp).fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(s.path(id).substringBeforeLast(" › ", ""), color = Gold, fontSize = 12.sp)
                    Text(n.title, fontSize = (size * 1.9f).sp, lineHeight = (size * 2.3f).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = fg)
                    Text("$mins min read", color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            itemsIndexed(n.blocks) { i, b ->
                Box(Modifier.widthIn(max = width.dp).fillMaxWidth().padding(start = (b.indent * 22).dp)) {
                    val serif = TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size * 1.6f).sp, color = fg)
                    when (b.type) {
                        "h1" -> Text(b.text, style = serif.copy(fontSize = (size * 1.6f).sp, fontWeight = FontWeight.Bold))
                        "h2" -> Text(b.text, style = serif.copy(fontSize = (size * 1.3f).sp, fontWeight = FontWeight.SemiBold))
                        "bullet" -> MdText(md("•  " + b.text), serif, onLink = onLink)
                        "number" -> MdText(md("${numberOf(n.blocks, i)}.  " + b.text), serif, onLink = onLink)
                        "check" -> MdText(md((if (b.checked) "☑  " else "☐  ") + b.text), serif, onLink = onLink)
                        "quote" -> Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(Gold)); MdText(md("  " + b.text), serif.copy(fontStyle = FontStyle.Italic), onLink = onLink)
                        }
                        "code" -> Surface(color = fg.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
                            Text(b.text, style = serif.copy(fontFamily = FontFamily.Monospace, fontSize = (size * 0.85f).sp), modifier = Modifier.padding(10.dp).fillMaxWidth())
                        }
                        "latex" -> MathView(b.text, fg)
                        "image" -> Column {
                            AttachmentImage(s, b.text)
                            if (b.extra.isNotBlank()) Text(b.extra, style = serif.copy(fontSize = (size * 0.8f).sp, fontStyle = FontStyle.Italic))
                        }
                        "file" -> TextButton({ onFile(ViewTarget(b.text, b.extra)) }) { Text("📎 ${b.extra} — open", color = Gold) }
                        "table" -> Column {
                            b.text.lines().filter { l -> l.isNotBlank() && !l.matches(Regex("[\\s|:\\-]+")) }.forEachIndexed { r, line ->
                                Row(Modifier.fillMaxWidth().border(0.5.dp, fg.copy(alpha = 0.25f))) {
                                    line.split("|").forEach { cell ->
                                        Text(md(cell.trim()), style = serif.copy(fontSize = (size * 0.9f).sp, fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal),
                                            modifier = Modifier.weight(1f).padding(6.dp))
                                    }
                                }
                            }
                        }
                        "flash" -> Text("Q: ${b.text}\nA: ${b.extra}", style = serif.copy(fontWeight = FontWeight.Medium))
                        else -> MdText(md(b.text), serif, onLink = onLink)
                    }
                }
            }
        }
    }
}

/** A formatting command sent from the editor toolbar to the active text field. */
data class FmtCmd(val id: Long, val mark: String = "", val insert: String = "")

/** Live styling while typing: same text length, markers dimmed, so the cursor never jumps. */
fun mdLive(src: String): AnnotatedString = buildAnnotatedString {
    append(src)
    val dim = SpanStyle(color = Ink.copy(alpha = 0.28f))
    fun pair(open: String, close: String, style: SpanStyle) {
        var i = 0
        while (i < src.length) {
            val a = src.indexOf(open, i); if (a < 0) break
            val b = src.indexOf(close, a + open.length)
            if (b < 0 || b == a + open.length) { i = a + open.length; continue }
            addStyle(dim, a, a + open.length); addStyle(style, a + open.length, b); addStyle(dim, b, b + close.length)
            i = b + close.length
        }
    }
    pair("**", "**", SpanStyle(fontWeight = FontWeight.Bold))
    pair("==", "==", SpanStyle(background = Gold.copy(alpha = 0.3f)))
    pair("`", "`", SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080)))
    pair("[[", "]]", SpanStyle(color = Gold, textDecoration = TextDecoration.Underline))
    var i = 0
    while (i < src.length) {
        if (src[i] == '*' && (i == 0 || src[i - 1] != '*') && i + 1 < src.length && src[i + 1] != '*') {
            val e = src.indexOf('*', i + 1)
            if (e > i + 1 && (e + 1 >= src.length || src[e + 1] != '*')) {
                addStyle(dim, i, i + 1); addStyle(SpanStyle(fontStyle = FontStyle.Italic), i + 1, e); addStyle(dim, e, e + 1); i = e + 1; continue
            }
        }
        i++
    }
}

/** Markdown for the reader: **bold**, *italic*, `code`, ==highlight== and [[topic links]]. */
fun md(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("**", i) && src.indexOf("**", i + 2) > i + 2 -> {
                val e = src.indexOf("**", i + 2); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, e)) }; i = e + 2
            }
            src.startsWith("==", i) && src.indexOf("==", i + 2) > i + 2 -> {
                val e = src.indexOf("==", i + 2); withStyle(SpanStyle(background = Gold.copy(alpha = 0.3f))) { append(src.substring(i + 2, e)) }; i = e + 2
            }
            src.startsWith("[[", i) && src.indexOf("]]", i + 2) > i + 2 -> {
                val e = src.indexOf("]]", i + 2); val ttl = src.substring(i + 2, e)
                pushStringAnnotation("link", ttl)
                withStyle(SpanStyle(color = Gold, textDecoration = TextDecoration.Underline)) { append(ttl) }
                pop(); i = e + 2
            }
            src[i] == '`' && src.indexOf('`', i + 1) > i + 1 -> {
                val e = src.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080))) { append(src.substring(i + 1, e)) }; i = e + 1
            }
            src[i] == '*' && src.indexOf('*', i + 1) > i + 1 -> {
                val e = src.indexOf('*', i + 1); withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(i + 1, e)) }; i = e + 1
            }
            else -> { append(src[i]); i++ }
        }
    }
}

@Composable fun MdText(a: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, onLink: (String) -> Unit) {
    if (a.getStringAnnotations("link", 0, a.length).isEmpty()) Text(a, style = style, modifier = modifier)
    else ClickableText(a, style = style, modifier = modifier) { off -> a.getStringAnnotations("link", off, off).firstOrNull()?.let { onLink(it.item) } }
}

/** Search-and-pick dialog for topics (used for links and for "save document to a note"). */
@Composable fun TopicPicker(s: Store, title: String, onPick: (Node) -> Unit, onNew: (() -> Unit)? = null, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = s.data.nodes.filter { !it.deleted && (it.kind == "topic" || it.kind == "page") && (q.isBlank() || it.title.contains(q, true)) }.take(30)
    AlertDialog(onDismissRequest = onClose, containerColor = Color(0xFF202327).copy(alpha = 0.95f), shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Search topics") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (onNew != null) item { Chip("＋ New topic in Inbox") { onNew(); onClose() } }
                    items(list) { n ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(n); onClose() }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            Text(n.title.ifBlank { "Untitled" })
                            Text(s.path(n.id).substringBeforeLast(" › ", ""), fontSize = 11.sp, color = Gold)
                        }
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClose) { Text("Cancel") } })
}

val QUIPS = listOf("Every circuit starts with one node.", "Signal over noise today.", "Small steps, clean waveforms.",
    "Derive it once, remember it forever.", "Curiosity is the best amplifier.")

/** Time-of-day greeting with one line that reflects what is happening in your studies. */
@Composable fun Greeting(s: Store, onName: () -> Unit) {
    val d = s.data
    val h = java.time.LocalTime.now().hour
    val hello = when { h < 5 -> "Still up"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; h < 22 -> "Good evening"; else -> "Good night" }
    val today = java.time.LocalDate.now().toEpochDay()
    val exam = d.exams.filter { it.date >= today }.minByOrNull { it.date }
    val due = d.cards.count { it.due <= now() }
    val line = when {
        exam != null && exam.date - today == 0L -> "${exam.title} is today. You've got this."
        exam != null && exam.date - today <= 14 -> "${exam.title} is in ${exam.date - today} days. Let's make them count."
        due > 0 -> "$due flashcards are waiting for you."
        streak(d) >= 2 -> "${streak(d)}-day study streak. Keep the signal strong."
        todayMin(d) > 0 -> "${todayMin(d)} minutes studied today. Nice."
        else -> QUIPS[(today % QUIPS.size).toInt()]
    }
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onName), RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(if (d.userName.isBlank()) hello else "$hello, ${d.userName}", style = MaterialTheme.typography.titleLarge)
            Text(line, color = Ink.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}
