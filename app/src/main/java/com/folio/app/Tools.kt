package com.folio.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable fun ToolsScreen(s: Store) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row { listOf("Converter", "Graph", "Formulas").forEachIndexed { i, t -> Chip(t, sub == i) { sub = i } } }
        when (sub) { 0 -> Converter(); 1 -> Grapher(s); else -> Formulas(s) }
    }
}

// ---------- unit converter ----------
val UNITS: Map<String, List<Pair<String, Double>>> = mapOf(
    "Temperature" to listOf("°C" to 1.0, "°F" to 1.0, "K" to 1.0),
    "Length" to listOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mile" to 1609.344, "ft" to 0.3048, "in" to 0.0254),
    "Mass" to listOf("kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.0283495),
    "Time" to listOf("s" to 1.0, "min" to 60.0, "h" to 3600.0, "day" to 86400.0),
    "Energy" to listOf("J" to 1.0, "kJ" to 1000.0, "cal" to 4.184, "kWh" to 3.6e6, "eV" to 1.602176634e-19),
    "Frequency" to listOf("Hz" to 1.0, "kHz" to 1e3, "MHz" to 1e6, "GHz" to 1e9),
    "Pressure" to listOf("Pa" to 1.0, "kPa" to 1e3, "bar" to 1e5, "atm" to 101325.0),
    "Speed" to listOf("m/s" to 1.0, "km/h" to 0.277778, "mph" to 0.44704)
)

fun fmtNum(x: Double): String {
    if (x == 0.0 || x.isNaN() || x.isInfinite()) return x.toString()
    val a = abs(x)
    return if (a >= 1e-4 && a < 1e9) java.math.BigDecimal(x).round(java.math.MathContext(8)).stripTrailingZeros().toPlainString()
    else String.format(java.util.Locale.US, "%.5e", x)
}

fun convert(cat: String, from: Int, to: Int, v: Double): Double {
    val us = UNITS[cat]!!
    if (cat == "Temperature") {
        val c = when (from) { 0 -> v; 1 -> (v - 32) * 5 / 9; else -> v - 273.15 }
        return when (to) { 0 -> c; 1 -> c * 9 / 5 + 32; else -> c + 273.15 }
    }
    return v * us[from].second / us[to].second
}

@Composable fun Converter() {
    val cats = UNITS.keys.toList()
    var cat by remember { mutableStateOf(cats[0]) }
    var from by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(1) }
    var inp by remember { mutableStateOf("100") }
    val us = UNITS[cat]!!
    val v = inp.toDoubleOrNull()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) { cats.forEach { c -> Chip(c, cat == c) { cat = c; from = 0; to = 1 } } }
        OutlinedTextField(inp, { inp = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
        SectionTitle("From")
        Row(Modifier.horizontalScroll(rememberScrollState())) { us.forEachIndexed { i, u -> Chip(u.first, from == i) { from = i } } }
        SectionTitle("To")
        Row(Modifier.horizontalScroll(rememberScrollState())) { us.forEachIndexed { i, u -> Chip(u.first, to == i) { to = i } } }
        if (v != null) Text("${fmtNum(convert(cat, from, to, v))} ${us[to].first}", fontSize = 28.sp, color = Gold)
    }
}

// ---------- graph plotter ----------
class Parser(private val s: String, private val x: Double) {
    private var i = 0
    private fun peek() = if (i < s.length) s[i] else '\u0000'
    private fun expect(c: Char) { if (peek() != c) throw IllegalArgumentException("expected $c"); i++ }
    fun parse(): Double { val v = expr(); if (i < s.length) throw IllegalArgumentException("unexpected ${peek()}"); return v }
    private fun expr(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') { val c = s[i++]; val t = term(); v = if (c == '+') v + t else v - t }
        return v
    }
    private fun term(): Double {
        var v = unary()
        while (true) {
            val c = peek()
            if (c == '*' || c == '/') { i++; val t = unary(); v = if (c == '*') v * t else v / t }
            else if (c.isLetter() || c == '(') v *= unary()   // implicit multiplication: 2x, 3sin(x)
            else break
        }
        return v
    }
    private fun unary(): Double { if (peek() == '-') { i++; return -unary() }; if (peek() == '+') { i++; return unary() }; return pow() }
    private fun pow(): Double { val b = atom(); if (peek() == '^') { i++; return b.pow(unary()) }; return b }
    private fun atom(): Double {
        val c = peek()
        if (c == '(') { i++; val v = expr(); expect(')'); return v }
        if (c.isDigit() || c == '.') { val st = i; while (peek().isDigit() || peek() == '.') i++; return s.substring(st, i).toDouble() }
        if (c.isLetter()) {
            val st = i; while (peek().isLetter()) i++
            val name = s.substring(st, i)
            if (peek() == '(') {
                i++; val a = expr(); expect(')')
                return when (name) {
                    "sin" -> sin(a); "cos" -> cos(a); "tan" -> tan(a); "sqrt" -> sqrt(a); "abs" -> abs(a)
                    "ln" -> ln(a); "log" -> log10(a); "exp" -> exp(a); else -> throw IllegalArgumentException("unknown function $name")
                }
            }
            return when (name) { "x" -> x; "pi" -> PI; "e" -> E; else -> throw IllegalArgumentException("unknown name $name") }
        }
        throw IllegalArgumentException("unexpected input")
    }
}
fun evalExpr(src: String, x: Double) = Parser(src.lowercase().replace(" ", ""), x).parse()

@Composable fun Grapher(s: Store) {
    var expr by remember { mutableStateOf("sin(x)") }
    var range by remember { mutableFloatStateOf(10f) }
    val err = remember(expr) { runCatching { evalExpr(expr, 1.0) }.exceptionOrNull()?.message }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(expr, { expr = it }, label = { Text("y = f(x)   e.g. 2x^2 - sqrt(abs(x)) + ln(x)") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("sin(", "cos(", "tan(", "sqrt(", "abs(", "ln(", "log(", "exp(", "^", "pi", "e", "(", ")").forEach { t -> Chip(t) { expr += t } }
        }
        Row { Chip("Zoom in") { range = max(1f, range / 2) }; Chip("Zoom out") { range = min(1000f, range * 2) }; Chip("Save graph") { if (err == null) s.update { d -> d.copy(graphs = (d.graphs + expr).distinct()) } } }
        if (err != null) Text("⚠ $err", color = Gold)
        val gold = Gold; val grid = Ink.copy(alpha = 0.3f)
        Canvas(Modifier.fillMaxWidth().height(320.dp)) {
            val w = size.width; val h = size.height
            drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2)); drawLine(grid, Offset(w / 2, 0f), Offset(w / 2, h))
            if (err == null) {
                val path = Path(); var pen = false
                for (px in 0..w.toInt()) {
                    val x = ((px / w) * 2 - 1) * range
                    val y = runCatching { evalExpr(expr, x.toDouble()) }.getOrDefault(Double.NaN)
                    val py = h / 2 - (y / range).toFloat() * (w / 2)
                    if (y.isNaN() || y.isInfinite() || py < -h || py > 2 * h) pen = false
                    else if (!pen) { path.moveTo(px.toFloat(), py); pen = true } else path.lineTo(px.toFloat(), py)
                }
                drawPath(path, gold, style = Stroke(width = 3f))
            }
        }
        Text("x from -${range.toInt()} to ${range.toInt()}", fontSize = 12.sp)
        if (s.data.graphs.isNotEmpty()) SectionTitle("Saved graphs")
        s.data.graphs.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g, Modifier.weight(1f).clickable { expr = g }, fontFamily = FontFamily.Monospace)
                Chip("✕") { s.update { d -> d.copy(graphs = d.graphs - g) } }
            }
        }
    }
}

// ---------- formula pad / library ----------
@Composable fun Formulas(s: Store) {
    var name by remember { mutableStateOf("") }
    var src by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("Formula composer")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(src, { src = it }, label = { Text("LaTeX source") }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("\\frac{}{}", "\\sqrt{}", "^{}", "_{}", "\\int_{}^{}", "\\sum_{}^{}", "\\partial", "\\nabla", "\\vec{}", "\\omega", "\\epsilon_0", "\\mu_0", "\\pi", "\\theta").forEach { t -> Chip(t) { src += t } }
        }
        Button({ if (src.isNotBlank()) { s.update { d -> d.copy(formulas = d.formulas + Formula(name = name.ifBlank { "Formula" }, latex = src)) }; name = ""; src = "" } }) { Text("Save to library") }
        OutlinedTextField(q, { q = it }, label = { Text("Search formulas") }, modifier = Modifier.fillMaxWidth())
        s.data.formulas.filter { q.isBlank() || it.name.contains(q, true) || it.latex.contains(q, true) }.forEach { f ->
            GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) { Text(f.name); Text(f.latex, fontFamily = FontFamily.Monospace, color = Gold, fontSize = 13.sp) }
                    Chip("✕") { s.update { d -> d.copy(formulas = d.formulas.filter { it.id != f.id }) } }
                }
            }
        }
    }
}

// ---------- More: bookmarks, trash, backup, checks ----------
@Composable fun MoreScreen(s: Store, open: (String) -> Unit, view: (ViewTarget) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Bookmarks", "Trash", "Backup", "Checks", "Documents").forEachIndexed { i, t -> Chip(t, sub == i) { sub = i } } }
        when (sub) { 0 -> Bookmarks(s, open); 1 -> TrashView(s); 2 -> Backup(s); 3 -> Checks(s); else -> OpenDoc(s, view) }
    }
}

@Composable fun Bookmarks(s: Store, open: (String) -> Unit) {
    val marks = s.data.nodes.filter { it.bookmarked && !it.deleted }
    var q by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf<Node?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Search saved notes") }, modifier = Modifier.fillMaxWidth())
        val shown = marks.filter { q.isBlank() || it.title.contains(q, true) }
        shown.groupBy { it.folder.ifBlank { "Unfiled" } }.forEach { (folder, list) ->
            Text(folder, color = Gold)
            list.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.title, Modifier.weight(1f).clickable { if (n.kind == "page") open(n.id) })
                    Chip("Folder") { moving = n }
                    Chip("Remove") { s.editNode(n.id) { it.copy(bookmarked = false, folder = "") } }
                }
            }
        }
        if (shown.isEmpty()) Text("No bookmarks yet. Tap ☆ on a note.")
    }
    moving?.let { n -> FormDialog("Bookmark folder", listOf("Folder name (blank = unfiled)"), listOf(n.folder), { v -> s.editNode(n.id) { it.copy(folder = v[0].trim()) } }) { moving = null } }
}

@Composable fun TrashView(s: Store) {
    val del = s.data.nodes.filter { n -> n.deleted && n.parentId?.let { p -> s.node(p)?.deleted == true } != true }   // only the top of each deleted branch
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (del.isEmpty()) Text("Trash is empty.")
        del.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${n.kind}: ${n.title}", Modifier.weight(1f))
                Chip("Restore") { s.restore(n.id) }
                Chip("Delete forever") { s.purge(n.id) }
            }
        }
    }
}

@Composable fun Backup(s: Store) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(s.exportJson().toByteArray()) }; msg = "Backup saved" }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val t = ctx.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            msg = if (t != null && s.importJson(t)) "Restored from backup" else "That file is not a valid Folio backup"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Everything is saved on this device automatically. The backup is one JSON file that includes your attachments, and the Mac app will read it too.")
        Button({ exp.launch("folio-backup.json") }) { Text("Export backup (JSON)") }
        OutlinedButton({ imp.launch(arrayOf("application/json", "*/*")) }) { Text("Import / restore backup") }
        if (msg.isNotBlank()) Text(msg, color = Gold)
    }
}

@Composable fun Checks(s: Store) {
    var res by remember { mutableStateOf<List<String>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button({ res = s.integrity() }) { Text("Run library check") }
        res?.forEach { Text("• $it") }
    }
}
