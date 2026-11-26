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
val BLOCK_TYPES = listOf("text", "h1", "h2", "bullet", "number", "check", "quote", "code", "latex", "flash")
val TYPE_LABEL = mapOf("text" to "Text", "h1" to "Title", "h2" to "Heading", "bullet" to "Bullets", "number" to "Numbers",
    "check" to "Checklist", "quote" to "Quote", "code" to "Code", "latex" to "Equation", "flash" to "Flashcard")

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
) { Surface(Modifier.fillMaxSize(), color = Graphite, contentColor = Ink) { content() } }

// ---------------- small building blocks ----------------
@Composable fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(2.dp).clip(RoundedCornerShape(50))
        .background(if (selected) Gold.copy(alpha = 0.18f) else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, color = if (selected) Gold else Ink.copy(alpha = 0.75f), fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable fun Ico(icon: ImageVector, desc: String, tint: Color = Ink.copy(alpha = 0.8f), size: Dp = 40.dp, onClick: () -> Unit) =
    IconButton(onClick, Modifier.size(size)) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }

@Composable fun SectionTitle(text: String) =
    Text(text.uppercase(), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))

@Composable fun StatTile(label: String, value: String, modifier: Modifier = Modifier) =
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = modifier) {
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
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
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
    if (view != null) Viewer(s, view!!) { view = null }
    else if (p != null) {
        if (reading) Reader(s, p, { view = it }) { reading = false }
        else Editor(s, p, onRead = { reading = true }, onFile = { view = it }, onClose = { page = null })
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
            NavigationRail(containerColor = Panel) {
                items.forEachIndexed { i, (t, ic) ->
                    NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Gold.copy(alpha = 0.18f)))
                }
            }
            Box(Modifier.weight(1f)) { body() }
        } else Scaffold(containerColor = Graphite, bottomBar = {
            NavigationBar(containerColor = Panel) {
                items.forEachIndexed { i, (t, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) }, colors = navColors)
                }
            }
        }) { pad -> Box(Modifier.padding(pad)) { body() } }
    }
}

// ---------------- Library ----------------
@Composable fun Library(s: Store, open: (String) -> Unit) {
    var cur by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val node = cur?.let { s.node(it) }
    val kind = when (node?.kind) { null -> "course"; "course" -> "chapter"; "chapter" -> "topic"; else -> "page" }
    BackHandler(cur != null) { cur = node?.parentId }
    fun go(n: Node) { if (n.kind == "page") open(n.id) else { cur = n.id; q = "" } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (cur != null) Ico(Icons.Filled.ArrowBack, "Up") { cur = node?.parentId }
                Text(node?.title ?: "Library", style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
                Ico(Icons.Filled.FlashOn, "Quick note", Gold) { open(s.quickNote()) }
            }
            if (cur != null) Text(s.path(cur!!), color = Gold, fontSize = 12.sp, maxLines = 1)
            OutlinedTextField(q, { q = it }, placeholder = { Text("Search titles, notes, #tags") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
    if (add) FormDialog("New $kind", listOf("Title"), onOk = { s.add(cur, kind, it[0]) }, onClose = { add = false })
}

@Composable fun NodeRow(s: Store, n: Node, go: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val icon = when (n.kind) { "course" -> Icons.Filled.School; "chapter" -> Icons.Filled.Book; "topic" -> Icons.Filled.Label; else -> Icons.Filled.Description }
    val sub = if (n.kind == "page") (n.blocks.firstOrNull { it.type == "text" && it.text.isNotBlank() }?.text?.take(70) ?: "Empty page")
              else "${s.children(n.id).size} items"
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
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
    var tagText by remember(id) { mutableStateOf(n.tags.joinToString(", ")) }
    fun addAttachment(uri: Uri, asImage: Boolean) {
        s.attach(uri)?.let { (stored, name) ->
            val img = asImage || ctx.contentResolver.getType(uri)?.startsWith("image/") == true
            val nb = Block(type = if (img) "image" else "file", text = stored, extra = name)
            s.editBlocks(id) { it + nb }; active = nb.id
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
        else item?.text?.let { t -> s.editBlocks(id) { it + Block(text = t.toString()) } }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxSize().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.Filled.ArrowBack, "Back", onClick = onClose)
                Text(s.path(id).substringBeforeLast(" › ", ""), color = Gold, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Ico(Icons.Filled.Save, "Save snapshot") { s.editNode(id) { it.copy(history = it.history + Snap(now(), it.title, it.blocks)) } }
                Ico(Icons.Filled.History, "History") { hist = true }
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
            LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(n.blocks, key = { _, b -> b.id }) { i, b ->
                    var num = 0
                    if (b.type == "number") { var j = i; while (j >= 0 && n.blocks[j].type == "number") { num++; j-- } }
                    BlockEditor(s, id, n, i, b, num, active == b.id, { active = b.id }, onFile)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    BLOCK_TYPES.forEach { t ->
                        Chip("+ " + (TYPE_LABEL[t] ?: t)) {
                            val nb = Block(type = t); val at = n.blocks.size
                            s.editBlocks(id) { it + nb }; active = nb.id
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
    if (hist) AlertDialog(onDismissRequest = { hist = false }, title = { Text("Revision history") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (n.history.isEmpty()) Text("No snapshots yet. Tap the save icon to keep a version.")
                n.history.reversed().forEach { sn ->
                    Chip("${fmt(sn.time)} · ${sn.title} — restore") { s.editNode(id) { it.copy(title = sn.title, blocks = sn.blocks) }; hist = false }
                }
            }
        }, confirmButton = { TextButton({ hist = false }) { Text("Close") } })
}

@Composable fun BlockEditor(s: Store, pid: String, n: Node, i: Int, b: Block, num: Int, active: Boolean, onActive: () -> Unit, onFile: (ViewTarget) -> Unit) {
    fun upd(nb: Block) = s.editBlocks(pid) { l -> l.map { x -> if (x.id == b.id) nb else x } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(if (active) Panel else Color.Transparent)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onActive)
        .padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (b.type) {
            "check" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(b.checked, { upd(b.copy(checked = it)) }, colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Graphite))
                BlockField(b.text, "To-do", b.type, onActive) { upd(b.copy(text = it)) }
            }
            "bullet" -> Row { Text("•", color = Gold, modifier = Modifier.padding(end = 10.dp, top = 1.dp)); BlockField(b.text, "List item", b.type, onActive) { upd(b.copy(text = it)) } }
            "number" -> Row { Text("$num.", color = Gold, modifier = Modifier.padding(end = 10.dp, top = 1.dp)); BlockField(b.text, "List item", b.type, onActive) { upd(b.copy(text = it)) } }
            "quote" -> Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Gold)); Spacer(Modifier.width(10.dp))
                BlockField(b.text, "Quote", b.type, onActive) { upd(b.copy(text = it)) }
            }
            "code" -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF101214)).padding(10.dp)) {
                BlockField(b.text, "Code", b.type, onActive) { upd(b.copy(text = it)) }
            }
            "latex" -> Column {
                BlockField(b.text, "LaTeX equation, e.g. \\nabla \\times E = -\\partial B/\\partial t", b.type, onActive) { upd(b.copy(text = it)) }
                if (b.text.isNotBlank()) MathView(b.text, Ink)
            }
            "flash" -> Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B2A26)).padding(10.dp)) {
                Text("FLASHCARD", color = Gold, fontSize = 11.sp, letterSpacing = 1.sp)
                BlockField(b.text, "Front / question", "text", onActive) { upd(b.copy(text = it)) }
                BlockField(b.extra, "Back / answer", "text", onActive) { upd(b.copy(extra = it)) }
                Chip("Save to deck") { s.addCard(n.title, b.text, b.extra) }
            }
            "image" -> Column { AttachmentImage(s, b.text); BlockField(b.extra, "Caption", "text", onActive) { upd(b.copy(extra = it)) } }
            "file" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${b.extra}", Modifier.weight(1f)); Chip("Open") { onFile(ViewTarget(b.text, b.extra)) }
            }
            else -> BlockField(b.text, "Write something…", b.type, onActive) { upd(b.copy(text = it)) }
        }
        if (active) Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (b.type != "image" && b.type != "file") BLOCK_TYPES.forEach { t -> Chip(TYPE_LABEL[t] ?: t, b.type == t) { upd(b.copy(type = t)) } }
            Ico(Icons.Filled.ArrowUpward, "Move up", size = 36.dp) { s.editBlocks(pid) { l -> swap(l, i, i - 1) } }
            Ico(Icons.Filled.ArrowDownward, "Move down", size = 36.dp) { s.editBlocks(pid) { l -> swap(l, i, i + 1) } }
            Ico(Icons.Filled.ContentCopy, "Duplicate", size = 36.dp) { s.editBlocks(pid) { l -> l.toMutableList().also { it.add(i + 1, b.copy(id = uid())) } } }
            Ico(Icons.Filled.ContentCut, "Split page here", size = 36.dp) { s.split(pid, i) }
            Ico(Icons.Filled.Delete, "Delete block", Color(0xFFE57373), 36.dp) { s.editBlocks(pid) { l -> l.filter { x -> x.id != b.id } } }
        }
    }
}

@Composable fun BlockField(v: String, label: String, type: String, onFocus: () -> Unit = {}, onChange: (String) -> Unit) {
    val style = when (type) {
        "h1" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        "h2" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        "code", "latex" -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Ink)
        "quote" -> TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = Ink)
        else -> TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
    }
    BasicTextField(v, onChange, textStyle = style, cursorBrush = SolidColor(Gold),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(label, style = style.copy(color = Ink.copy(alpha = 0.3f))); inner() } })
}

// ---------------- Reader ----------------
@Composable fun Reader(s: Store, id: String, onFile: (ViewTarget) -> Unit, onClose: () -> Unit) {
    val n = s.node(id)!!
    var size by remember { mutableFloatStateOf(18f) }
    var width by remember { mutableFloatStateOf(640f) }
    var theme by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(false) }
    val themes = listOf(Color(0xFFF6F1E7) to Color(0xFF2B2A28), Color(0xFFE9DDC3) to Color(0xFF3A2E1C), Color(0xFF16181B) to Color(0xFFE6E1D6))
    val (bg, fg) = themes[theme]
    val st = rememberLazyListState()
    val prog = if (n.blocks.isEmpty()) 0f else (st.firstVisibleItemIndex + 1f) / n.blocks.size
    val words = n.blocks.sumOf { b -> b.text.split(" ").count { w -> w.isNotBlank() } }
    val mins = maxOf(1, words / 200)
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
                Box(Modifier.widthIn(max = width.dp).fillMaxWidth()) {
                    val serif = TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size * 1.6f).sp, color = fg)
                    when (b.type) {
                        "h1" -> Text(b.text, style = serif.copy(fontSize = (size * 1.6f).sp, fontWeight = FontWeight.Bold))
                        "h2" -> Text(b.text, style = serif.copy(fontSize = (size * 1.3f).sp, fontWeight = FontWeight.SemiBold))
                        "bullet" -> Text("•  " + b.text, style = serif)
                        "number" -> {
                            var k = 1; var j = i - 1
                            while (j >= 0 && n.blocks[j].type == "number") { k++; j-- }
                            Text("$k.  " + b.text, style = serif)
                        }
                        "check" -> Text((if (b.checked) "☑  " else "☐  ") + b.text, style = serif)
                        "quote" -> Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(Gold)); Text("  " + b.text, style = serif.copy(fontStyle = FontStyle.Italic))
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
                        "flash" -> Text("Q: ${b.text}\nA: ${b.extra}", style = serif.copy(fontWeight = FontWeight.Medium))
                        else -> Text(b.text, style = serif)
                    }
                }
            }
        }
    }
}
